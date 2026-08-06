import { describe, it, expect, vi, beforeEach } from 'vitest'

// jsdom here runs without a real localStorage (node is started without
// --localstorage-file), and both the request interceptor and the auth store
// read it. Minimal in-memory stand-in.
function memoryStorage() {
  const map = new Map()
  return {
    getItem: (k) => (map.has(k) ? map.get(k) : null),
    setItem: (k, v) => map.set(k, String(v)),
    removeItem: (k) => map.delete(k),
    clear: () => map.clear()
  }
}
vi.stubGlobal('localStorage', memoryStorage())
vi.stubGlobal('sessionStorage', memoryStorage())

// The response interceptor imports these three; stub them so we can observe the
// navigation it performs and keep i18n out of the picture.
vi.mock('@/router', () => ({
  default: {
    currentRoute: { value: { fullPath: '/login?from=/system/user' } },
    push: vi.fn(),
    replace: vi.fn()
  }
}))
vi.mock('@/lang', () => ({
  default: { global: { te: () => false, t: (k) => k } }
}))
vi.mock('@/utils/tenant', () => ({ currentTenant: () => 'demo' }))

import router from '@/router'
import request from '@/services/request'

/**
 * A failed sign-in must not be mistaken for an expired session.
 *
 * `ErrorCode.BAD_CREDENTIALS`, `INVALID_TOKEN` and `EXPIRED_TOKEN` all carry the
 * numeric code 401, and BusinessException bodies come back on HTTP 200 — so a
 * plain "wrong password" on POST /auth/login lands in the same body-code branch
 * the interceptor uses for "your session expired". That branch redirects to
 * /login carrying the CURRENT url as `?from=`, which on the login page is the
 * login page itself:
 *
 *   session expires on /system/user  → /login?from=/system/user
 *   user mistypes the password once  → /login?from=/login%3Ffrom%3D/system/user
 *   user then signs in successfully  → replace('/login?from=/system/user')
 *                                    → already authenticated → replace('/')
 *
 * i.e. one typo silently discards the deep link the user was trying to reach.
 * The sibling branch for HTTP-status 401 (30 lines below in the same file)
 * already excludes auth endpoints via isAuthEndpoint(); the body-code branch
 * did not.
 */
describe('request interceptor — body code 401', () => {
  beforeEach(() => {
    router.push.mockClear()
    // Drive real responses through the real interceptor chain.
    request.defaults.adapter = async (config) => ({
      data: { code: 401, msg: 'Bad credentials' },
      status: 200,
      statusText: 'OK',
      headers: {},
      config
    })
  })

  it('does not bounce to /login when the 401 came from /auth/login itself', async () => {
    await expect(request.post('/auth/login', { username: 'x', password: 'wrong' }))
      .rejects.toThrow('Bad credentials')

    expect(router.push, 'a rejected sign-in must not rewrite ?from=')
      .not.toHaveBeenCalled()
  })

  it('does not bounce to /login for /auth/refresh or /auth/logout either', async () => {
    await expect(request.post('/auth/refresh')).rejects.toThrow()
    await expect(request.post('/auth/logout')).rejects.toThrow()

    expect(router.push).not.toHaveBeenCalled()
  })

  it('still bounces to /login when a normal endpoint reports 401', async () => {
    // The session-expiry case the branch exists for — must keep working.
    await expect(request.get('/admin/user/list')).rejects.toThrow()

    expect(router.push).toHaveBeenCalledWith({
      path: '/login',
      query: { from: '/login?from=/system/user' }
    })
  })
})
