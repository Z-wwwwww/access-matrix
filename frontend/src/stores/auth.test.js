// @vitest-environment jsdom
import { describe, it, expect, beforeEach, afterEach, vi } from 'vitest'
import { setActivePinia, createPinia } from 'pinia'

// Node 22+ / jsdom don't give us working Web Storage by default (see
// tenant.test.js for the long version) — install in-memory stubs before
// importing the store, whose module top reads localStorage.
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

// services/auth pulls in the axios instance → router → i18n; stub the lot.
vi.mock('@/services/auth', () => ({
  loginApi:   vi.fn(),
  refreshApi: vi.fn(),
  logoutApi:  vi.fn(),
  getMeApi:   vi.fn()
}))
vi.mock('@/utils/oidc', () => ({
  oidcConfig:        vi.fn(() => ({ enabled: true })),
  refreshTokens:     vi.fn(),
  keycloakLogoutUrl: vi.fn(),
  isSsoReachable:    vi.fn()
}))
vi.mock('@/utils/tenant', () => ({ clearTenantCache: vi.fn() }))

const { oidcConfig, refreshTokens } = await import('@/utils/oidc')
const { refreshApi } = await import('@/services/auth')
const { useAuthStore } = await import('./auth')

/** Unsigned JWT with the given payload — decodeJwt only reads the middle part. */
function makeJwt(payload) {
  const b64 = (o) => btoa(JSON.stringify(o)).replace(/\+/g, '-').replace(/\//g, '_').replace(/=+$/, '')
  return `${b64({ alg: 'none' })}.${b64(payload)}.sig`
}

function expIn(seconds) {
  return Math.floor(Date.now() / 1000) + seconds
}

beforeEach(() => {
  localStorageStub.clear()
  sessionStorageStub.clear()
  vi.clearAllMocks()
  oidcConfig.mockReturnValue({ enabled: true })
  setActivePinia(createPinia())
})

afterEach(() => {
  vi.useRealTimers()
})

describe('refresh() — OIDC mode (KC refresh_token)', () => {
  it('renews via refreshTokens and persists the rotated token', async () => {
    localStorageStub.setItem('access_token', makeJwt({ exp: expIn(60) }))
    localStorageStub.setItem('kc_refresh_token', 'rt1')
    const store = useAuthStore()

    const newAccess = makeJwt({ exp: expIn(1800), sub: 'u1' })
    refreshTokens.mockResolvedValue({ accessToken: newAccess, idToken: 'id2', refreshToken: 'rt2' })

    await store.refresh()

    expect(refreshTokens).toHaveBeenCalledWith('rt1')
    expect(store.accessToken).toBe(newAccess)
    expect(store.idToken).toBe('id2')
    expect(localStorageStub.getItem('kc_refresh_token')).toBe('rt2')
    expect(refreshApi).not.toHaveBeenCalled()
  })

  it('keeps the previous refresh token when KC does not rotate', async () => {
    localStorageStub.setItem('access_token', makeJwt({ exp: expIn(60) }))
    localStorageStub.setItem('kc_refresh_token', 'rt1')
    const store = useAuthStore()
    refreshTokens.mockResolvedValue({ accessToken: makeJwt({ exp: expIn(1800) }), refreshToken: undefined })

    await store.refresh()

    expect(localStorageStub.getItem('kc_refresh_token')).toBe('rt1')
  })

  it('drops the stored token on invalid_grant so the next login starts clean', async () => {
    localStorageStub.setItem('access_token', makeJwt({ exp: expIn(60) }))
    localStorageStub.setItem('kc_refresh_token', 'rt-dead')
    const store = useAuthStore()
    const err = new Error('OIDC token refresh failed (400) Session not active')
    err.invalidGrant = true
    refreshTokens.mockRejectedValue(err)

    await expect(store.refresh()).rejects.toThrow('Session not active')
    expect(store.kcRefreshToken).toBe('')
    expect(localStorageStub.getItem('kc_refresh_token')).toBeNull()
  })

  it('keeps the stored token on transient (non-invalid_grant) failures', async () => {
    localStorageStub.setItem('access_token', makeJwt({ exp: expIn(60) }))
    localStorageStub.setItem('kc_refresh_token', 'rt1')
    const store = useAuthStore()
    refreshTokens.mockRejectedValue(new Error('network down'))

    await expect(store.refresh()).rejects.toThrow('network down')
    expect(localStorageStub.getItem('kc_refresh_token')).toBe('rt1')
  })

  it('single-flights concurrent refresh calls', async () => {
    localStorageStub.setItem('access_token', makeJwt({ exp: expIn(60) }))
    localStorageStub.setItem('kc_refresh_token', 'rt1')
    const store = useAuthStore()
    let resolve
    refreshTokens.mockReturnValue(new Promise((r) => { resolve = r }))

    const p1 = store.refresh()
    const p2 = store.refresh()
    resolve({ accessToken: makeJwt({ exp: expIn(1800) }), refreshToken: 'rt2' })
    await Promise.all([p1, p2])

    expect(refreshTokens).toHaveBeenCalledTimes(1)
  })

  it('refuses to refresh during a support session (would clobber the support token)', async () => {
    localStorageStub.setItem('access_token', makeJwt({ exp: expIn(60) }))
    localStorageStub.setItem('kc_refresh_token', 'rt1')
    localStorageStub.setItem('support_orig_access_token', makeJwt({ exp: expIn(9999) }))
    const store = useAuthStore()

    await expect(store.refresh()).rejects.toThrow('support session expired')
    expect(refreshTokens).not.toHaveBeenCalled()
  })
})

describe('refresh() — password mode (backend cookie)', () => {
  it('falls back to the backend /auth/refresh when no KC refresh token is stored', async () => {
    localStorageStub.setItem('access_token', makeJwt({ exp: expIn(60) }))
    const store = useAuthStore()
    const newAccess = makeJwt({ exp: expIn(900) })
    refreshApi.mockResolvedValue({ data: { code: 0, data: { access_token: newAccess } } })

    await store.refresh()

    expect(refreshApi).toHaveBeenCalled()
    expect(refreshTokens).not.toHaveBeenCalled()
    expect(store.accessToken).toBe(newAccess)
  })
})

describe('proactive KC refresh timer', () => {
  it('refreshes ~2 min before the access token expires', async () => {
    vi.useFakeTimers()
    localStorageStub.setItem('access_token', makeJwt({ exp: expIn(600) }))   // 10 min
    localStorageStub.setItem('kc_refresh_token', 'rt1')
    const store = useAuthStore()   // schedules on init
    refreshTokens.mockResolvedValue({ accessToken: makeJwt({ exp: expIn(1800) }), refreshToken: 'rt2' })

    await vi.advanceTimersByTimeAsync(7 * 60_000)   // < exp-2min: nothing yet
    expect(refreshTokens).not.toHaveBeenCalled()
    await vi.advanceTimersByTimeAsync(2 * 60_000)   // past exp-2min
    expect(refreshTokens).toHaveBeenCalledWith('rt1')
    expect(localStorageStub.getItem('kc_refresh_token')).toBe('rt2')
    store.clearAuth()   // don't leak the re-armed timer into other tests
  })

  it('does not schedule anything without a KC refresh token (password mode)', async () => {
    vi.useFakeTimers()
    localStorageStub.setItem('access_token', makeJwt({ exp: expIn(600) }))
    useAuthStore()

    await vi.advanceTimersByTimeAsync(60 * 60_000)
    expect(refreshTokens).not.toHaveBeenCalled()
    expect(refreshApi).not.toHaveBeenCalled()
  })

  it('clearAuth cancels the pending timer', async () => {
    vi.useFakeTimers()
    localStorageStub.setItem('access_token', makeJwt({ exp: expIn(600) }))
    localStorageStub.setItem('kc_refresh_token', 'rt1')
    const store = useAuthStore()

    store.clearAuth()
    await vi.advanceTimersByTimeAsync(60 * 60_000)
    expect(refreshTokens).not.toHaveBeenCalled()
  })
})
