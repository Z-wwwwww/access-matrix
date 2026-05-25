/**
 * Auth store — JWT-based.
 *
 * Internal model:
 *   - accessToken : short-lived JWT in localStorage, attached as Bearer.
 *   - refresh     : opaque token in HttpOnly cookie; never visible to JS.
 *   - userInfo    : profile + roles + authorities from GET /user/me.
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

  function clearAuth() {
    accessToken.value = ''
    userInfo.value    = null
    localStorage.removeItem(ACCESS_KEY)
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

  async function logout() {
    try { await logoutApi() } finally { clearAuth() }
  }

  async function fetchUserInfo() {
    const res = await getMeApi()
    if (res.data.code === 0) userInfo.value = res.data.data
    return res.data.data
  }

  return {
    accessToken,
    userInfo,
    isAuthenticated,
    claims,
    userId,
    tenantId,
    username,
    roles,
    authorities,
    setAccessToken,
    clearAuth,
    login,
    refresh,
    logout,
    fetchUserInfo
  }
})
