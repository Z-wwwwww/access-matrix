import { describe, it, expect, vi, beforeEach } from 'vitest'
import { createPinia, setActivePinia } from 'pinia'

// Same storage stubs as routerPublicPaths.test.js — the router's module graph
// (i18n, auth store) touches Web Storage at import time.
function makeStorageStub() {
  const _store = new Map()
  return {
    getItem: (k) => (_store.has(k) ? _store.get(k) : null),
    setItem: (k, v) => { _store.set(k, String(v)) },
    removeItem: (k) => { _store.delete(k) },
    clear: () => { _store.clear() },
    key: (i) => Array.from(_store.keys())[i] ?? null,
    get length() { return _store.size }
  }
}
const localStorageStub = makeStorageStub()
const sessionStorageStub = makeStorageStub()
vi.stubGlobal('localStorage', localStorageStub)
vi.stubGlobal('sessionStorage', sessionStorageStub)
Object.defineProperty(window, 'localStorage', { value: localStorageStub, configurable: true })
Object.defineProperty(window, 'sessionStorage', { value: sessionStorageStub, configurable: true })

const router = (await import('@/router')).default

/**
 * The bounce to /login must carry the WHOLE target, query and hash included.
 *
 * `login/index.vue` uses `route.query.from` verbatim as the post-login redirect
 * (`const redirect = route.query.from || '/'`), so whatever the guard puts there
 * is exactly where the user lands. Three of the guard's four redirects already
 * pass `to.fullPath`; the unauthenticated branch passed `to.path`, which drops
 * `?query` and `#hash` — and the guard's own comment a few lines above spells
 * out that trap ("必须显式传 query/hash，path key 不会从 fullPath 解析它们").
 *
 * The dropped case is the common one: someone opens a shared deep link
 * (/demo/task?status=2&keyword=urgent) while logged out, signs in, and lands on
 * a bare list with the filters gone.
 */
describe('router — the login bounce preserves the full target', () => {
  beforeEach(() => {
    setActivePinia(createPinia())
    localStorageStub.clear()
    sessionStorageStub.clear()
  })

  it('keeps the query string of a deep link', async () => {
    await router.push('/demo/task?status=2&keyword=urgent')
    await router.isReady()

    expect(router.currentRoute.value.path).toBe('/login')
    expect(router.currentRoute.value.query.from).toBe('/demo/task?status=2&keyword=urgent')
  })

  it('keeps the hash too', async () => {
    await router.push('/system/user#tab-roles')
    await router.isReady()

    expect(router.currentRoute.value.path).toBe('/login')
    expect(router.currentRoute.value.query.from).toBe('/system/user#tab-roles')
  })

  it('still sends a bare root to /login without a from', async () => {
    await router.push('/')
    await router.isReady()

    expect(router.currentRoute.value.path).toBe('/login')
    expect(router.currentRoute.value.query.from).toBeUndefined()
  })
})
