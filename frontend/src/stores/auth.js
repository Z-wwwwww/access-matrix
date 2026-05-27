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

const ACCESS_KEY = 'access_token'
const ID_TOKEN_KEY = 'id_token'

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
    fetchUserInfo
  }
})
