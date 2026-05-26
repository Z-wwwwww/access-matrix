import { describe, it, expect } from 'vitest'
import {
  arrayHas,
  arrayHasAny,
  matchPermission,
  hasAllPermissions,
  hasAnyPermission
} from './permission'

// These tests mirror the backend PermissionMatcherTest. The two implementations
// MUST agree — otherwise a button is shown on the page but the API rejects the
// request, or vice versa.

describe('matchPermission — wildcard semantics', () => {
  it('exact-match a single permission', () => {
    expect(matchPermission(['user:read'], 'user:read')).toBe(true)
    expect(matchPermission(['user:read'], 'user:delete')).toBe(false)
    expect(matchPermission(['user:read'], 'role:read')).toBe(false)
  })

  it('*:* grants every permission', () => {
    expect(matchPermission(['*:*'], 'user:read')).toBe(true)
    expect(matchPermission(['*:*'], 'role:delete')).toBe(true)
    expect(matchPermission(['*:*'], 'whatever:goes-here')).toBe(true)
  })

  it('resource:* grants every action on that resource only', () => {
    expect(matchPermission(['user:*'], 'user:read')).toBe(true)
    expect(matchPermission(['user:*'], 'user:delete')).toBe(true)
    expect(matchPermission(['user:*'], 'role:read')).toBe(false)
  })

  it('fail-closed on null / empty / blank inputs', () => {
    expect(matchPermission(null, 'user:read')).toBe(false)
    expect(matchPermission(undefined, 'user:read')).toBe(false)
    expect(matchPermission([], 'user:read')).toBe(false)
    expect(matchPermission(['user:read'], null)).toBe(false)
    expect(matchPermission(['user:read'], undefined)).toBe(false)
    expect(matchPermission(['user:read'], '')).toBe(false)
  })

  it('non-array perms argument is treated as empty', () => {
    // Defensive: callers may pass `authStore.authorities` before the store is
    // hydrated; we must not crash with `.includes is not a function`.
    expect(matchPermission('not-an-array', 'user:read')).toBe(false)
    expect(matchPermission({}, 'user:read')).toBe(false)
  })

  it('malformed required permission (no colon) cannot leak through resource:*', () => {
    // We do NOT want "user:*" to accidentally match a malformed "userread"
    // string just because it starts with "user". Only *:* should cover anything.
    expect(matchPermission(['user:*'], 'userread')).toBe(false)
    expect(matchPermission(['*:*'], 'userread')).toBe(true)
  })
})

describe('hasAllPermissions / hasAnyPermission', () => {
  it('hasAllPermissions requires every one (wildcard-aware)', () => {
    const userPerms = ['user:*', 'role:read']
    expect(hasAllPermissions(userPerms, ['user:read', 'user:delete', 'role:read'])).toBe(true)
    expect(hasAllPermissions(userPerms, ['user:read', 'role:delete'])).toBe(false)
  })

  it('hasAnyPermission needs only one (wildcard-aware)', () => {
    const userPerms = ['role:read']
    expect(hasAnyPermission(userPerms, ['user:read', 'role:read'])).toBe(true)
    expect(hasAnyPermission(userPerms, ['user:read', 'menu:read'])).toBe(false)
  })

  it('super admin (*:*) matches every request', () => {
    expect(hasAllPermissions(['*:*'], ['user:read', 'role:delete', 'menu:write'])).toBe(true)
    expect(hasAnyPermission(['*:*'], ['definitely:not-real'])).toBe(true)
  })

  it('non-array wants returns false', () => {
    expect(hasAllPermissions(['*:*'], null)).toBe(false)
    expect(hasAnyPermission(['*:*'], 'user:read')).toBe(false)
  })
})

describe('arrayHas / arrayHasAny — exact-match role checks', () => {
  // Role checks intentionally have no wildcard semantics: roles are role-name
  // strings, not resource:action codes.

  it('arrayHas requires every target', () => {
    expect(arrayHas(['admin', 'editor'], ['admin'])).toBe(true)
    expect(arrayHas(['admin', 'editor'], ['admin', 'editor'])).toBe(true)
    expect(arrayHas(['admin'], ['admin', 'editor'])).toBe(false)
  })

  it('arrayHasAny matches on any target', () => {
    expect(arrayHasAny(['admin'], ['admin', 'editor'])).toBe(true)
    expect(arrayHasAny(['guest'], ['admin', 'editor'])).toBe(false)
  })

  it('both fail-closed on non-array inputs', () => {
    expect(arrayHas(null, ['admin'])).toBe(false)
    expect(arrayHas(['admin'], null)).toBe(false)
    expect(arrayHasAny(null, ['admin'])).toBe(false)
    expect(arrayHasAny(['admin'], null)).toBe(false)
  })
})
