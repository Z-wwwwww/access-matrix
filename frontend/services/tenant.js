import request from './request'

/**
 * Platform-ops tenant management REST surface. All calls land on the
 * backend's /platform/tenants/... endpoints, which are gated by the
 * platform:tenant:* permissions. Holders of those permissions (PLATFORM_ADMIN
 * in the 'system' tenant) get through; business-tenant SUPER_ADMIN does
 * NOT — see backend PermissionMatcher's platform:* carve-out.
 */

export const listTenantsApi          = (params)         => request.get('/platform/tenants', { params })
export const getTenantApi            = (id)             => request.get(`/platform/tenants/${id}`)
export const createTenantApi         = (body)           => request.post('/platform/tenants', body)
export const updateTenantApi         = (id, body)       => request.patch(`/platform/tenants/${id}`, body)
export const suspendTenantApi        = (id)             => request.post(`/platform/tenants/${id}/suspend`)
export const resumeTenantApi         = (id)             => request.post(`/platform/tenants/${id}/resume`)
export const hardDeleteTenantApi     = (id, confirmCode) => request.delete(`/platform/tenants/${id}`, { data: { confirmCode } })
export const startSupportSessionApi  = (id, reason)     => request.post(`/platform/tenants/${id}/support-session`, { reason })
// Resend the tenant admin's onboarding invite. `email` is optional: omit (or
// pass empty) to re-send to the current address; pass a value to CORRECT the
// admin's email (DB + Keycloak + tenant contact) before re-sending.
export const resendInviteApi         = (id, email)      => request.post(`/platform/tenants/${id}/resend-invite`, email ? { email } : {})
