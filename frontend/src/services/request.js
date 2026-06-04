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
import i18n from '@/lang'
import { currentTenant } from '@/utils/tenant'

/**
 * Localize a backend error message. By convention the backend sends a stable i18n
 * KEY (e.g. `error.dict.itemInUse`) for user-facing business errors; we translate
 * it here. Legacy prose messages aren't keys → `te()` is false → passed through
 * unchanged. So this is safe to apply to every error message.
 */
function localizeError(msg) {
  return msg && i18n.global.te(msg) ? i18n.global.t(msg) : msg
}

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
  // X-Tenant-Id：post-auth は JWT の `tid` クレームが正、pre-auth (/auth/login など)
  // はこのヘッダがバックエンドの RequestContextFilter に拾われる。
  // utils/tenant.js が subdomain / ?tenant= / localStorage / "default" の
  // 優先順位で唯一の真実を返す（同じ値を oidcConfig() も使うので両者がズレない）。
  config.headers['X-Tenant-Id'] = currentTenant()
  return config
})

// ─── Response Interceptor ───
request.interceptors.response.use(
  (res) => {
    const data = res.data || {}
    const { code } = data
    // Localize any error message in place (key → localized; prose → unchanged), so
    // both the interceptor-reject path and the page's `res.data.msg` path are i18n'd.
    if (code && code !== 0 && data.msg) data.msg = localizeError(data.msg)
    const msg = data.msg

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
      const msg = localizeError(error.response?.data?.msg)
      return Promise.reject(new Error(msg || 'サーバーエラー'))
    }
    return Promise.reject(error)
  }
)

export default request
