/**
 * Auth store — JWT-based (new backend at port 9135).
 *
 * Internal model:
 *   - accessToken : short-lived JWT in localStorage, attached as Bearer.
 *   - refresh     : opaque token in HttpOnly cookie; never visible to JS.
 *   - userInfo    : profile + roles + authorities from GET /user/me.
 *
 * The legacy `useAuthStore` surface (token / currentUser / companyId / companyName /
 * roles / authorities / permission / cacheToken / setUserFromMenu / setToken) is kept
 * as thin aliases so existing business views compile unchanged. Once those views are
 * migrated to the new backend, the aliases can be deleted.
 */
import { defineStore } from 'pinia'
import { ref, computed } from 'vue'
import { loginApi, refreshApi, logoutApi, getMeApi } from '../../services/auth'
import { decodeJwt } from '@/utils/jwt-decode'

const ACCESS_KEY = 'access_token'

export const useAuthStore = defineStore('auth', () => {
  const accessToken = ref(localStorage.getItem(ACCESS_KEY) || '')
  const userInfo    = ref(null)

  // --- JWT-derived ---
  const isAuthenticated = computed(() => !!accessToken.value)
  const claims   = computed(() => (accessToken.value ? decodeJwt(accessToken.value) : {}))
  const userId   = computed(() => claims.value.sub || '')
  const tenantId = computed(() => claims.value.tid || '')
  const username = computed(() => claims.value.preferred_username || '')

  // --- Legacy-compatible alias surface (kept until business views migrate) ---
  const token       = computed(() => accessToken.value)
  const currentUser = computed(() => userInfo.value || {})
  const companyId   = computed(() => tenantId.value || currentUser.value.companyId || '')
  const companyName = computed(() => currentUser.value.companyName || '')
  const roles       = computed(() => {
    const r = userInfo.value?.roles || []
    return r.map((x) => (typeof x === 'string' ? x : x.roleCode))
  })
  const authorities = computed(() => userInfo.value?.authorities || [])
  const permission  = computed(() => authorities.value)

  function setAccessToken(t) {
    accessToken.value = t || ''
    if (accessToken.value) localStorage.setItem(ACCESS_KEY, accessToken.value)
    else                   localStorage.removeItem(ACCESS_KEY)
  }

  /** Legacy compat: accepts a raw token string or { token, remember } object. */
  function setToken(arg) {
    setAccessToken(typeof arg === 'string' ? arg : arg?.token)
  }

  /** Legacy compat: silent token swap. With cookie-based refresh this is now a plain set. */
  function cacheToken(t) {
    setAccessToken(t)
  }

  function clearAuth() {
    accessToken.value = ''
    userInfo.value    = null
    localStorage.removeItem(ACCESS_KEY)
    // Cleanup of stale keys from older builds so users carrying them across upgrades reset cleanly.
    localStorage.removeItem('access_token_v2')
    localStorage.removeItem('refresh_token_v2')
    localStorage.removeItem('user')
    localStorage.removeItem('remember')
    sessionStorage.removeItem('access_matrix_tabs')
  }

  /** Legacy compat — menus no longer carry user data in the new backend. */
  function setUserFromMenu() {}

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

  async function logout() {
    try { await logoutApi() } finally { clearAuth() }
  }

  async function fetchUserInfo() {
    const res = await getMeApi()
    if (res.data.code === 0) userInfo.value = res.data.data
    return res.data.data
  }

  return {
    // primary
    accessToken,
    userInfo,
    isAuthenticated,
    claims,
    userId,
    tenantId,
    username,
    // legacy aliases
    token,
    currentUser,
    companyId,
    companyName,
    roles,
    authorities,
    permission,
    // actions
    setAccessToken,
    setToken,
    cacheToken,
    clearAuth,
    setUserFromMenu,
    login,
    refresh,
    logout,
    fetchUserInfo
  }
})
