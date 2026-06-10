import request from './request'

export const loginApi   = (data) => request.post('/auth/login', data)
// Refresh / logout take no body — the refresh token rides as the HttpOnly
// cookie issued by the backend on /auth/login. axios ships it because the
// shared instance has `withCredentials: true`.
export const refreshApi = ()     => request.post('/auth/refresh')
export const logoutApi  = ()     => request.post('/auth/logout')
export const getMeApi   = ()     => request.get('/user/me')

// Self-service profile edit (Profile page) — contact fields only (email,
// displayName). The admin user console refuses self-targeted changes.
export const updateMyProfileApi = (data) => request.put('/user/me/profile', data)

// Legacy alias (existing callers expect this name); points at the same /user/me.
export const getUserInfoApi = getMeApi

// Self-service password change + reset live in Keycloak — see utils/oidc.js
// for beginPasswordUpdate() (UPDATE_PASSWORD application-initiated action)
// and keycloakForgotPasswordUrl(). We don't proxy them through the backend.
