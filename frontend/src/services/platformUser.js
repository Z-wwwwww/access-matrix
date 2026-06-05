import request from './request'

/**
 * Platform-ops staff (system-tenant users) management. List/create gated by
 * opsuser:read/create; disable/enable/reset-password by opsuser:update; delete by
 * opsuser:delete. Only the super 'ops' (PLATFORM_ADMIN) holds opsuser:* — regular
 * operators (PLATFORM_OPERATOR) cannot reach these.
 */
export const listPlatformUsersApi    = (params) => request.get('/platform/users', { params })
export const createPlatformUserApi   = (body)   => request.post('/platform/users', body)
export const disablePlatformUserApi  = (id)     => request.post(`/platform/users/${id}/disable`)
export const enablePlatformUserApi   = (id)     => request.post(`/platform/users/${id}/enable`)
export const resetPlatformUserPwApi  = (id)     => request.post(`/platform/users/${id}/reset-password`)
export const deletePlatformUserApi   = (id)     => request.delete(`/platform/users/${id}`)
