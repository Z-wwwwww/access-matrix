import request from './request'

/**
 * Pre-auth invite endpoints — both calls go out WITHOUT a Bearer token
 * (the cleartext invite token in the URL is the only credential).
 *
 * Backend lives under /auth/invite/{token} (mapped by /api context-path
 * in services/request.js).
 */

export const probeInviteApi  = (token)            => request.get(`/auth/invite/${token}`)
export const acceptInviteApi = (token, password) =>
  request.post(`/auth/invite/${token}`, { password })
