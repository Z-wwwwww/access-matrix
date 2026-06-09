import request from './request'

/**
 * Platform-ops staff (system-tenant users) management. List/create gated by
 * opsuser:read/create; disable/enable/reset-password by opsuser:update; delete by
 * opsuser:delete. Only the super 'ops' (PLATFORM_ADMIN) holds opsuser:* — regular
 * operators (PLATFORM_OPERATOR) cannot reach these.
 */
export const listPlatformUsersApi    = (params) => request.get('/platform/users', { params })
export const createPlatformUserApi   = (body)   => request.post('/platform/users', body)
export const updatePlatformUserApi   = (id, body) => request.put(`/platform/users/${id}`, body)
export const disablePlatformUserApi  = (id)     => request.post(`/platform/users/${id}/disable`)
export const enablePlatformUserApi   = (id)     => request.post(`/platform/users/${id}/enable`)
// Reset = re-issue credentials: rotates the temp password AND (best-effort) emails
// the account credentials. Single path for both "forgot password" and "resend".
export const resetPlatformUserPwApi  = (id)     => request.post(`/platform/users/${id}/reset-password`)
// Resend = same re-issue logic (rotates temp password) but "account/welcome" email
// wording instead of the password-reset wording.
export const resendPlatformUserInviteApi = (id) => request.post(`/platform/users/${id}/resend-invite`)
// Force-logout: invalidate the user's active sessions (account stays enabled; can re-login).
export const forcePlatformUserLogoutApi = (id) => request.post(`/platform/users/${id}/force-logout`)
export const deletePlatformUserApi   = (id)     => request.delete(`/platform/users/${id}`)
