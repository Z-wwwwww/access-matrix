/**
 * Auth store — handles both legacy password JWT and OIDC (Keycloak) tokens.
 *
 * Internal model:
 *   - accessToken : short-lived JWT in localStorage, attached as Bearer.
 *                   Format identical in both modes (Keycloak signs RS256, our
 *                   own AdminAuthController signs HS256; backend Resource
 *                   Server validates against the right JwtDecoder).
 *   - idToken     : OIDC id_token from the /token exchange. ONLY needed
 *                   for the RP-Initiated Logout call so Keycloak can wipe
 *                   its session cookie. Not used elsewhere.
 *   - refresh     : opaque token in HttpOnly cookie; never visible to JS
 *                   (password mode only — OIDC refresh lives in Keycloak's
 *                   session cookie, not in our app).
 *   - userInfo    : profile + roles + authorities from GET /user/me.
 */
import { defineStore } from 'pinia'
import { ref, computed } from 'vue'
import { loginApi, refreshApi, logoutApi, getMeApi } from '../../services/auth'
import { decodeJwt } from '@/utils/jwt-decode'
import { keycloakLogoutUrl, oidcConfig, isSsoReachable } from '@/utils/oidc'
import { clearTenantCache } from '@/utils/tenant'

const ACCESS_KEY = 'access_token'
const ID_TOKEN_KEY = 'id_token'

// Platform-ops "support session" — these slots hold the ORIGINAL ops
// token + tenant_id while a support session is active. On terminate we
// swap back. Kept here (not in a separate store) so the existing axios
// Bearer plumbing keeps working unchanged — it just reads access_token.
const SUPPORT_ORIG_ACCESS_KEY = 'support_orig_access_token'
const SUPPORT_ORIG_ID_KEY = 'support_orig_id_token'
const SUPPORT_ORIG_TENANT_KEY = 'support_orig_tenant_id'
const SUPPORT_SESSION_KEY = 'support_session_info'   // { sessionId, tenantCode, displayName, expiresAt }
const TENANT_LS_KEY = 'tenant_id'

export const useAuthStore = defineStore('auth', () => {
  const accessToken = ref(localStorage.getItem(ACCESS_KEY) || '')
  const idToken     = ref(localStorage.getItem(ID_TOKEN_KEY) || '')
  const userInfo    = ref(null)

  // --- JWT-derived ---
  const isAuthenticated = computed(() => !!accessToken.value)
  const claims   = computed(() => (accessToken.value ? decodeJwt(accessToken.value) : {}))
  const userId   = computed(() => claims.value.sub || '')
  const tenantId = computed(() => claims.value.tid || '')
  const username = computed(() => claims.value.preferred_username || '')

  // --- /me-derived ---
  const roles       = computed(() => {
    const r = userInfo.value?.roles || []
    return r.map((x) => (typeof x === 'string' ? x : x.roleCode))
  })
  const authorities = computed(() => userInfo.value?.authorities || [])

  function setAccessToken(t) {
    accessToken.value = t || ''
    if (accessToken.value) localStorage.setItem(ACCESS_KEY, accessToken.value)
    else                   localStorage.removeItem(ACCESS_KEY)
  }

  function setIdToken(t) {
    idToken.value = t || ''
    if (idToken.value) localStorage.setItem(ID_TOKEN_KEY, idToken.value)
    else               localStorage.removeItem(ID_TOKEN_KEY)
  }

  function clearAuth() {
    accessToken.value = ''
    idToken.value     = ''
    userInfo.value    = null
    localStorage.removeItem(ACCESS_KEY)
    localStorage.removeItem(ID_TOKEN_KEY)
    sessionStorage.removeItem('access_matrix_tabs')
    // login_password_unlocked is the per-tab "user is in break-glass
    // mode" flag (set by the hot-zone or the unreachable-banner CTA on
    // /login). Once we log out, the tab's relationship with the SPA
    // resets — the next visit to /login should re-evaluate whether
    // SSO is reachable rather than silently dropping the user into
    // the password form because they once broke glass an hour ago.
    sessionStorage.removeItem('login_password_unlocked')
  }

  async function login(payload) {
    const res = await loginApi(payload)
    if (res.data.code === 0) setAccessToken(res.data.data.access_token)
    return res
  }

  async function refresh() {
    const res = await refreshApi()
    if (res.data.code === 0) setAccessToken(res.data.data.access_token)
    else throw new Error(res.data.msg || 'refresh failed')
  }

  /**
   * Sign the user out. Three-stage in OIDC mode:
   *
   *   1. Best-effort backend call to revoke the refresh token and
   *      trigger ForceLogoutService (kills lingering access tokens
   *      across pods).
   *   2. Clear local state (access token, id token, userInfo).
   *   3. In OIDC mode: probe Keycloak's reachability, then navigate to
   *      its end_session_endpoint so the IdP wipes its own session
   *      cookie. Without that step, the next "Sign in with SSO" would
   *      silent-login the user back in via KC's still-valid cookie.
   *
   * If Keycloak is unreachable in step 3, we skip the redirect and
   * return false. The caller (AppHeader.handleLogout) then routes the
   * user to /login locally with a `?logout=local-only` query hint so
   * the login page can surface "logged out locally; IdP session may
   * still be alive" rather than throwing them at the browser's native
   * "refused to connect" page with no way back to the SPA.
   *
   * Returns true if we navigated away (caller should NOT router.push
   * afterwards); false if we stayed in the SPA and the caller should
   * handle the post-logout navigation. The caller can read
   * {@link wasLastLogoutLocalOnly} to decide whether to attach the
   * `?logout=local-only` query.
   */
  let lastLogoutLocalOnly = false
  function wasLastLogoutLocalOnly() {
    const v = lastLogoutLocalOnly
    lastLogoutLocalOnly = false   // single-read, then reset
    return v
  }

  async function logout() {
    lastLogoutLocalOnly = false
    const idTokenSnapshot = idToken.value
    try { await logoutApi() } catch { /* best-effort */ }
    clearAuth()

    if (oidcConfig().enabled) {
      // Probe BEFORE the navigation commits — same defensive pattern as
      // login. If KC is down, navigating to end_session_endpoint dead-
      // ends on the browser's "refused to connect" page with no path
      // back. Local logout is already done (clearAuth above); KC's
      // session cookie stays alive until KC is reachable again, which
      // is a degraded state we surface explicitly on /login.
      const reachable = await isSsoReachable()
      if (reachable) {
        const postLogoutTo = window.location.origin + '/login'
        const url = keycloakLogoutUrl(idTokenSnapshot, postLogoutTo)
        if (url) {
          window.location.assign(url)
          return true
        }
      } else {
        lastLogoutLocalOnly = true
      }
    }
    return false
  }

  async function fetchUserInfo() {
    const res = await getMeApi()
    if (res.data.code === 0) userInfo.value = res.data.data
    return res.data.data
  }

  // ─── Platform-ops support sessions ────────────────────────────────
  // Reactivity hook for the banner — bumped whenever enter/terminate
  // mutates localStorage. localStorage isn't natively reactive in Vue,
  // so we make this an explicit signal the banner / nav components watch.
  const supportSessionBump = ref(0)

  /** True iff we're currently impersonating a tenant. */
  const isSupportSession = computed(() => {
    supportSessionBump.value   // dep for reactivity
    return !!localStorage.getItem(SUPPORT_ORIG_ACCESS_KEY)
  })

  /** Metadata for the active support session ({sessionId, tenantCode, displayName, expiresAt}) or null. */
  const supportSessionInfo = computed(() => {
    supportSessionBump.value
    const raw = localStorage.getItem(SUPPORT_SESSION_KEY)
    return raw ? JSON.parse(raw) : null
  })

  /**
   * Enter a support session. Stashes the current ops token + tenant_id
   * under the SUPPORT_ORIG_* keys, then overwrites them with the
   * support-session values. Existing axios Bearer plumbing keeps reading
   * access_token unchanged, so every subsequent API call automatically
   * hits the target tenant. Caller is responsible for navigating to a
   * fresh route (typically "/") so the UI re-renders with the new
   * tenant's menus + me.
   */
  function enterSupportSession(supportToken, info) {
    if (localStorage.getItem(SUPPORT_ORIG_ACCESS_KEY)) {
      throw new Error('already in a support session — terminate the current one first')
    }
    // Stash originals.
    const origAccess = localStorage.getItem(ACCESS_KEY) || ''
    const origId     = localStorage.getItem(ID_TOKEN_KEY) || ''
    const origTenant = localStorage.getItem(TENANT_LS_KEY) || ''
    localStorage.setItem(SUPPORT_ORIG_ACCESS_KEY, origAccess)
    localStorage.setItem(SUPPORT_ORIG_ID_KEY, origId)
    localStorage.setItem(SUPPORT_ORIG_TENANT_KEY, origTenant)
    localStorage.setItem(SUPPORT_SESSION_KEY, JSON.stringify(info))

    // Switch live state.
    setAccessToken(supportToken)
    // id_token stays as ops's — the support session is HS256, no id_token.
    // (Keeping ops's id_token means logout still has it for the eventual
    // KC end_session call after we terminate back to ops.)
    localStorage.setItem(TENANT_LS_KEY, info.tenantCode)
    clearTenantCache()         // utils/tenant.js memoises; force re-resolve
    userInfo.value = null      // force /me refetch under new identity
    // Drop the ops identity's open tabs. They reference ops-only routes
    // (e.g. /platform/tenants) that don't exist under the support menu, so
    // leaving them in sessionStorage would resurrect dead 404 tabs after the
    // reload. The tab bar rebuilds from the support landing route instead.
    sessionStorage.removeItem('access_matrix_tabs')
    supportSessionBump.value++
  }

  /**
   * Terminate the active support session: restore the stashed ops token +
   * tenant_id, clear the support-* keys. Caller should navigate to a
   * sensible landing page (typically /platform/tenants) afterwards.
   */
  function terminateSupportSession() {
    const origAccess = localStorage.getItem(SUPPORT_ORIG_ACCESS_KEY)
    if (origAccess == null) return false   // not in a support session
    const origId     = localStorage.getItem(SUPPORT_ORIG_ID_KEY) || ''
    const origTenant = localStorage.getItem(SUPPORT_ORIG_TENANT_KEY) || ''

    setAccessToken(origAccess)
    setIdToken(origId)
    if (origTenant) localStorage.setItem(TENANT_LS_KEY, origTenant)
    else            localStorage.removeItem(TENANT_LS_KEY)
    clearTenantCache()
    userInfo.value = null

    localStorage.removeItem(SUPPORT_ORIG_ACCESS_KEY)
    localStorage.removeItem(SUPPORT_ORIG_ID_KEY)
    localStorage.removeItem(SUPPORT_ORIG_TENANT_KEY)
    localStorage.removeItem(SUPPORT_SESSION_KEY)
    // Drop the support identity's open tabs for the same reason as on entry:
    // tenant pages opened during the session would otherwise linger as 404
    // tabs once we're back on the ops menu. Rebuilt from the ops landing route.
    sessionStorage.removeItem('access_matrix_tabs')
    supportSessionBump.value++
    return true
  }

  return {
    accessToken,
    idToken,
    userInfo,
    isAuthenticated,
    claims,
    userId,
    tenantId,
    username,
    roles,
    authorities,
    setAccessToken,
    setIdToken,
    clearAuth,
    login,
    refresh,
    logout,
    wasLastLogoutLocalOnly,
    fetchUserInfo,
    // support sessions
    isSupportSession,
    supportSessionInfo,
    enterSupportSession,
    terminateSupportSession
  }
})
