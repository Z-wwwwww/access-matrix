import request from './request'

/**
 * Self-service break-glass credential endpoints. See backend
 * BreakGlassController for the contract.
 *
 * - GET /me/break-glass-password/status: { configured: boolean }
 * - POST /me/break-glass-password { password }
 *
 * Both require an authenticated session that holds SUPER_ADMIN role
 * (enforced server-side); the UI extra-defensively gates on the same
 * role before showing the menu entry.
 */

export const getBreakGlassStatusApi = () => request.get('/me/break-glass-password/status')
export const setBreakGlassPasswordApi = (password) =>
  request.post('/me/break-glass-password', { password })
