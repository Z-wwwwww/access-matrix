/**
 * Single axios instance for all backend calls.
 *
 *   - baseURL `/proxy_url` is routed by Vite to the new backend (port 9135).
 *   - Access token attached as `Authorization: Bearer <jwt>` from the auth store.
 *   - Refresh token rides as an HttpOnly cookie (Set by the backend on /auth/login);
 *     `withCredentials: true` ships it on /auth/refresh and /auth/logout.
 *   - On 401 we run a single-flight refresh and replay the failed request; if the
 *     refresh fails the user is bounced to /login.
 */
import axios from 'axios'
import qs from 'qs'
import { useAuthStore } from '@/stores/auth'
import router from '@/router'

const request = axios.create({
  baseURL: '/proxy_url',
  timeout: 15000,
  withCredentials: true,
  // 数组参数使用 repeat 格式: a=1&a=2（与原项目 NtsTable 一致）
  paramsSerializer: (params) => qs.stringify(params, { arrayFormat: 'repeat' })
})

const LANG_MAP = {
  ja_JP: 'ja-JP',
  zh_CN: 'zh-CN',
  en: 'en-US',
  ko_KR: 'ko'
}

let refreshing = null

function isAuthEndpoint(url) {
  if (!url) return false
  return url.includes('/auth/login') || url.includes('/auth/refresh') || url.includes('/auth/logout')
}

// ─── Request Interceptor ───
request.interceptors.request.use((config) => {
  const token = useAuthStore().accessToken
  if (token) config.headers['Authorization'] = 'Bearer ' + token
  const lang = localStorage.getItem('i18n-lang')
  config.headers['Accept-language'] = LANG_MAP[lang] || 'ja-JP'
  return config
})

// ─── Response Interceptor ───
request.interceptors.response.use(
  (res) => {
    const { code, msg } = res.data || {}

    // 401 returned via JsonResult body (e.g., business-level unauthenticated)
    if (code === 401) {
      const authStore = useAuthStore()
      authStore.clearAuth()
      const currentPath = router.currentRoute.value.fullPath
      router.push({ path: '/login', query: { from: currentPath } })
      return Promise.reject(new Error(msg || 'ログインの有効期限が切れました'))
    }

    // 700: business error (caller-handled most of the time, but reject so callers can catch)
    if (code === 700) {
      return Promise.reject(new Error(msg || '業務エラー'))
    }

    return res
  },
  async (error) => {
    const status = error.response?.status
    const original = error.config

    // 401 returned as HTTP status (Spring Security resource server style)
    if (status === 401 && !original?._retry && !isAuthEndpoint(original?.url)) {
      original._retry = true
      const authStore = useAuthStore()
      try {
        refreshing = refreshing || authStore.refresh()
        await refreshing
        refreshing = null
        original.headers['Authorization'] = 'Bearer ' + authStore.accessToken
        return request(original)
      } catch (e) {
        refreshing = null
        authStore.clearAuth()
        const currentPath = router.currentRoute.value.fullPath
        router.push({ path: '/login', query: { from: currentPath } })
        return Promise.reject(e)
      }
    }

    if (status === 500) {
      const msg = error.response?.data?.msg
      return Promise.reject(new Error(msg || 'サーバーエラー'))
    }
    return Promise.reject(error)
  }
)

export default request
