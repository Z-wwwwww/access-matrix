// @vitest-environment jsdom
import { describe, it, expect, beforeEach, vi } from 'vitest'

// Node 22+ ships an experimental built-in localStorage that requires the
// `--localstorage-file` CLI flag; without it `window.localStorage` is
// undefined and jsdom does NOT polyfill it for us. Install a tiny in-memory
// stub before importing the module under test so its top-of-file localStorage
// reads don't crash.
const _store = new Map()
const stub = {
  getItem:    (k) => (_store.has(k) ? _store.get(k) : null),
  setItem:    (k, v) => { _store.set(k, String(v)) },
  removeItem: (k) => { _store.delete(k) },
  clear:      () => { _store.clear() },
  key:        (i) => Array.from(_store.keys())[i] ?? null,
  get length() { return _store.size }
}
vi.stubGlobal('localStorage', stub)
Object.defineProperty(window, 'localStorage', { value: stub, configurable: true })

// jsdom defaults the document origin to `about:blank`, which makes
// `history.replaceState` throw SecurityError when we point it at an
// http://localhost URL. Override `window.location` outright — tenant.js
// only reads `search` and `hostname`, so a plain object suffices.
function setLocation(url) {
  const u = new URL(url)
  Object.defineProperty(window, 'location', {
    value: { search: u.search, hostname: u.hostname, origin: u.origin, href: u.href },
    writable: true,
    configurable: true
  })
}

const { currentTenant, setTenantOverride, clearTenantCache, tenantFromHost } = await import('./tenant')

describe('tenantFromHost — pure parser', () => {
  it('returns null for localhost (single-host dev)', () => {
    expect(tenantFromHost('localhost')).toBeNull()
    expect(tenantFromHost('localhost:5273')).toBeNull()
  })

  it('returns null for apex / two-label hosts (no subdomain)', () => {
    expect(tenantFromHost('access-matrix.com')).toBeNull()
    expect(tenantFromHost('example.io')).toBeNull()
  })

  it('returns null for IPv4 literals', () => {
    expect(tenantFromHost('127.0.0.1')).toBeNull()
    expect(tenantFromHost('192.168.1.10')).toBeNull()
  })

  it('returns null for IPv6 bracket literals', () => {
    expect(tenantFromHost('[::1]')).toBeNull()
  })

  it('extracts subdomain on a 3+ label host', () => {
    expect(tenantFromHost('acme.access-matrix.com')).toBe('acme')
    expect(tenantFromHost('ACME.access-matrix.com')).toBe('acme') // lowercased
    expect(tenantFromHost('tenant-7.access-matrix.com')).toBe('tenant-7')
  })

  it('strips port before parsing', () => {
    expect(tenantFromHost('acme.access-matrix.com:8443')).toBe('acme')
  })

  it('rejects reserved subdomains (www / app / api / kc / ...)', () => {
    expect(tenantFromHost('www.access-matrix.com')).toBeNull()
    expect(tenantFromHost('app.access-matrix.com')).toBeNull()
    expect(tenantFromHost('api.access-matrix.com')).toBeNull()
    expect(tenantFromHost('kc.access-matrix.com')).toBeNull()
    expect(tenantFromHost('auth.access-matrix.com')).toBeNull()
  })

  it('rejects RFC1035-invalid labels', () => {
    expect(tenantFromHost('-leading-hyphen.access-matrix.com')).toBeNull()
    expect(tenantFromHost('UPPER_under.access-matrix.com')).toBeNull()
  })
})

describe('currentTenant — full resolution chain', () => {
  beforeEach(() => {
    // jsdom keeps localStorage hot across tests in the same file — wipe.
    localStorage.clear()
    clearTenantCache()
    // Reset URL to a benign localhost shape; individual tests rewrite as needed.
    setLocation('http://localhost:5273/')
  })

  it('falls back to "demo" with nothing set', () => {
    expect(currentTenant()).toBe('demo')
  })

  it('honors ?tenant=... query and persists it to localStorage', () => {
    setLocation('http://localhost:5273/?tenant=acme')
    expect(currentTenant()).toBe('acme')
    expect(localStorage.getItem('tenant_id')).toBe('acme')
  })

  it('ignores ?tenant= with invalid value (falls through to default)', () => {
    setLocation('http://localhost:5273/?tenant=NOT_VALID!')
    expect(currentTenant()).toBe('demo')
    expect(localStorage.getItem('tenant_id')).toBeNull()
  })

  it('honors localStorage when no query / subdomain (sticky pick)', () => {
    localStorage.setItem('tenant_id', 'beta')
    expect(currentTenant()).toBe('beta')
  })

  it('ignores garbage localStorage value', () => {
    localStorage.setItem('tenant_id', '!!bogus!!')
    expect(currentTenant()).toBe('demo')
  })

  it('caches the resolved value across calls', () => {
    setLocation('http://localhost:5273/?tenant=acme')
    expect(currentTenant()).toBe('acme')
    // Nuke the URL — cached value should still win.
    setLocation('http://localhost:5273/')
    expect(currentTenant()).toBe('acme')
  })

  it('setTenantOverride writes and updates cache', () => {
    setTenantOverride('zeta')
    expect(currentTenant()).toBe('zeta')
    expect(localStorage.getItem('tenant_id')).toBe('zeta')
  })

  it('setTenantOverride throws on invalid name', () => {
    expect(() => setTenantOverride('-bad')).toThrow(/invalid tenant/)
    expect(() => setTenantOverride('UPPER')).toThrow(/invalid tenant/)
  })
})

describe('priority — query > host > localStorage > default', () => {
  beforeEach(() => {
    localStorage.clear()
    clearTenantCache()
  })

  it('query wins over a sticky localStorage value', () => {
    localStorage.setItem('tenant_id', 'beta')
    setLocation('http://localhost:5273/?tenant=acme')
    expect(currentTenant()).toBe('acme')
  })

  // host-based subdomain branch is exercised by tenantFromHost above;
  // jsdom doesn't let us flip window.location.hostname without reloading
  // the page, so we don't pin it from currentTenant() here.
})
