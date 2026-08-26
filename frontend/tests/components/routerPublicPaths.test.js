import { describe, it, expect, vi } from 'vitest'

// Node 22+ / jsdom don't give us working Web Storage by default (see
// stores/auth.test.js) — install in-memory stubs before importing the router,
// whose module graph (i18n, auth store) reads localStorage at import time.
function makeStorageStub() {
  const _store = new Map()
  return {
    getItem:    (k) => (_store.has(k) ? _store.get(k) : null),
    setItem:    (k, v) => { _store.set(k, String(v)) },
    removeItem: (k) => { _store.delete(k) },
    clear:      () => { _store.clear() },
    key:        (i) => Array.from(_store.keys())[i] ?? null,
    get length() { return _store.size }
  }
}
const localStorageStub   = makeStorageStub()
const sessionStorageStub = makeStorageStub()
vi.stubGlobal('localStorage', localStorageStub)
vi.stubGlobal('sessionStorage', sessionStorageStub)
Object.defineProperty(window, 'localStorage',   { value: localStorageStub,   configurable: true })
Object.defineProperty(window, 'sessionStorage', { value: sessionStorageStub, configurable: true })

const { isPublicPath } = await import('@/router')

/**
 * The router guard bounces every non-public path to /login when there is no
 * session. Pages reached from an EMAIL link are the ones that must never be
 * bounced: their recipient is by definition logged out, and in the
 * reset-password case cannot log in at all (that's the whole point of the
 * link). Pin the public set so a future guard edit can't silently strand one
 * of those flows.
 *
 *   /invite/{token}         — tenant-admin / ops-user onboarding invite
 *   /reset-password/{token} — SSO → password reverse migration
 *                             (SsoToPasswordMigrationService builds
 *                              baseUrl + "/reset-password/" + token)
 *
 * /signout is public for the opposite reason — its job is to end the session, so
 * a session-less hit must render rather than bounce. It used to bounce, and the
 * bounce carried `from=/signout`, which login/index.vue replays verbatim: the
 * next successful sign-in landed back on /signout and signed the user out again.
 */
describe('router public paths', () => {
  it.each([
    '/login',
    '/sso/callback',
    '/signout',
    '/invite/01HZX9ABCDEF',
    '/reset-password/01HZX9ABCDEF'
  ])('%s is reachable without a session', (path) => {
    expect(isPublicPath(path)).toBe(true)
  })

  it.each([
    '/',
    '/system/user',
    '/platform/tenants',
    '/profile',
    '/reset-password',
    '/invite'
  ])('%s requires a session', (path) => {
    expect(isPublicPath(path)).toBe(false)
  })
})
