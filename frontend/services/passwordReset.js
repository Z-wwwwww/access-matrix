import request from './request'

/**
 * Pre-auth password-reset endpoints — sibling of services/invite.js, used by
 * ResetPasswordAccept.vue when the user lands from a "set your password"
 * email sent during the SSO → password reverse migration.
 *
 * Both calls go out WITHOUT a Bearer token (the cleartext reset token in
 * the URL is the only credential). Backend mounts these under
 * /auth/password-reset/{token}.
 */

export const probeResetApi  = (token)            => request.get(`/auth/password-reset/${token}`)
export const acceptResetApi = (token, password) =>
  request.post(`/auth/password-reset/${token}`, { password })
