import request from './request'

export const loginApi   = (data) => request.post('/auth/login', data)
// Refresh / logout take no body — the refresh token rides as the HttpOnly
// cookie issued by the backend on /auth/login. axios ships it because the
// shared instance has `withCredentials: true`.
export const refreshApi = ()     => request.post('/auth/refresh')
export const logoutApi  = ()     => request.post('/auth/logout')
export const getMeApi   = ()     => request.get('/user/me')

// Legacy alias (existing callers expect this name); points at the same /user/me.
export const getUserInfoApi = getMeApi

// ─────────────────────────────────────────────────────────────────────────
// Self-service password change — the new backend's admin-only flow lives
// under /admin/auth/reset-password. We don't have a dedicated self-service
// endpoint yet, so we expose a stub the ChangePasswordDialog can call. It
// throws with a friendly error so the user sees a clear "not available"
// message instead of an exception trace.
// TODO(stage5): wire to /auth/change-password once the backend lands.
// ─────────────────────────────────────────────────────────────────────────
export function updatePwdApi(/* { oldPwd, newPwd } */) {
  return Promise.resolve({
    data: { code: 700, msg: 'パスワード変更は管理者経由でリセットしてください', data: null }
  })
}

// Forget-password captcha — same story, no captcha endpoint in the new
// backend yet. Returns an empty body so the Forget page renders without
// crashing; the form-submit path will surface a "not implemented" toast.
// TODO(stage5): wire to /auth/captcha + /auth/forget-password.
export function getCaptchaApi() {
  return Promise.resolve({
    data: { code: 0, msg: 'success', data: { captchaId: '', captchaImg: '' } }
  })
}
