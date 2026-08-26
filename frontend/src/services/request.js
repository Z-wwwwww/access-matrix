/**
 * Single axios instance for all backend calls.
 *
 *   - baseURL `/proxy_url` is routed by Vite to the new backend (port 9135).
 *   - Access token attached as `Authorization: Bearer <jwt>` from the auth store.
 *   - Refresh: SSO mode renews against KC's token endpoint with the stored
 *     refresh_token (stores/auth.js); password mode rides the HttpOnly cookie
 *     set on /auth/login (`withCredentials: true` ships it on /auth/refresh).
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

// Locale id (the src/lang/*.js file names, which is what `i18n-lang` stores)
// -> the BCP-47 tag the backend's Accept-Language parsing understands. Must
// cover EVERY locale the switcher offers: an unmapped one falls through to the
// 'ja-JP' default below, so those users get Japanese from every locale-aware
// backend path — including the invite / password-reset / break-glass emails,
// which pick their template and subject off this header. zh_TW was missing.
const LANG_MAP = {
  ja_JP: 'ja-JP',
  zh_CN: 'zh-CN',
  zh_TW: 'zh-TW',
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
    // 701 = bean-validation failure. The backend puts the per-field messages in
    // `data` ({ field: message }, already server-localized); the bare msg
    // ("Validation failed") is useless on its own. Fold the field errors into
    // `data.msg` so every caller that shows `res.data.msg` (or catches the
    // rejection below) gets the actual reason instead of the generic label.
    if (code === 701 && data.data && typeof data.data === 'object' && !Array.isArray(data.data)) {
      const fields = Object.entries(data.data)
      if (fields.length) data.msg = fields.map(([f, m]) => `${f}: ${m}`).join('; ')
    }
    const msg = data.msg

    // 401 returned via JsonResult body (e.g., business-level unauthenticated).
    //
    // NOT for the auth endpoints themselves — same exclusion the HTTP-status 401
    // branch below already makes, and for the same reason. ErrorCode gives
    // BAD_CREDENTIALS / INVALID_TOKEN / EXPIRED_TOKEN the numeric code 401 and
    // BusinessException bodies ride on HTTP 200, so a plain "wrong password" on
    // POST /auth/login arrives here indistinguishable from an expired session.
    // Bouncing on it re-pushes /login with the CURRENT url as ?from= — which on
    // the login page is the login page:
    //   expire on /system/user   → /login?from=/system/user
    //   one mistyped password    → /login?from=/login%3Ffrom%3D/system/user
    //   then a successful login  → replace('/login?from=/system/user') → '/'
    // i.e. the typo silently threw away the deep link the user was headed for.
    // It also clearAuth()s, which on a shared-origin tab wipes a live token.
    // The caller (login page / authStore.refresh) surfaces the error itself.
    if (code === 401 && !isAuthEndpoint(res.config?.url)) {
      const authStore = useAuthStore()
      authStore.clearAuth()
      const currentPath = router.currentRoute.value.fullPath
      router.push({ path: '/login', query: { from: currentPath } })
      return Promise.reject(new Error(msg || 'ログインの有効期限が切れました'))
    }
    if (code === 401) {
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

    // Every error status — not just 500 — carries a JsonResult body with a usable
    // `msg`. This used to read it for 500 ONLY, so a 400/403/404/405/415/429 was
    // rejected as the raw AxiosError and callers (`catch (e) => toast.error(e.message)`)
    // showed the user "Request failed with status code 400" while the server had
    // sent a perfectly good localized reason. That got worse once the Spring MVC
    // client errors (malformed JSON / bad param / wrong verb) were mapped off 500:
    // those responses moved out of the one branch that surfaced `msg`.
    //
    // The message is written ONTO the AxiosError rather than replacing it with a
    // bare Error: ExportFileButton recovers a failed download's real message via
    // `e.response.data instanceof Blob`, which `new Error(msg)` would destroy —
    // as the old 500 branch in fact did.
    if (error.response) {
      const msg = localizeError(error.response.data?.msg)
      error.message = msg || (status === 500 ? 'サーバーエラー' : 'リクエストに失敗しました')
    }
    return Promise.reject(error)
  }
)

export default request
