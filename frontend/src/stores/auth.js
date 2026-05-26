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
import { keycloakLogoutUrl, oidcConfig } from '@/utils/oidc'

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
   * Sign the user out. Two-stage:
   *
   *   1. Best-effort backend call to revoke the refresh token (password
   *      mode) and trigger ForceLogoutService (kills lingering access
   *      token across all backend pods).
   *   2. Clear local state. In OIDC mode, ALSO navigate to Keycloak's
   *      end_session_endpoint so the IdP wipes its session cookie;
   *      otherwise the next "Sign in with SSO" silent-logs the user
   *      back in and they can never actually log out. The Keycloak
   *      logout page redirects back to /login when done.
   *
   * Returns true if we navigated away (caller should NOT router.push
   * afterwards); false if we stayed in the SPA and the caller should
   * handle the post-logout navigation.
   */
  async function logout() {
    const idTokenSnapshot = idToken.value
    try { await logoutApi() } catch { /* best-effort */ }
    clearAuth()

    if (oidcConfig().enabled) {
      const postLogoutTo = window.location.origin + '/login'
      const url = keycloakLogoutUrl(idTokenSnapshot, postLogoutTo)
      if (url) {
        window.location.assign(url)
        return true
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
    fetchUserInfo
  }
})
