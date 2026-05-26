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

// Self-service password change + reset live in Keycloak's account console
// and forgot-password flow — see utils/oidc.js for keycloakAccountUrl() and
// keycloakForgotPasswordUrl(). We don't proxy them through the backend.
