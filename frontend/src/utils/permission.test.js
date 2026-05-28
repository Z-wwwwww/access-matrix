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

  it('*:* is PLATFORM super — matches platform: namespace only', () => {
    // *:* is the PLATFORM_ADMIN's wildcard. It DOES match every platform:* perm.
    expect(matchPermission(['*:*'], 'platform:tenant:read')).toBe(true)
    expect(matchPermission(['*:*'], 'platform:tenant:create')).toBe(true)
    expect(matchPermission(['*:*'], 'platform:anything')).toBe(true)
    // It does NOT match business permissions — a platform admin should not
    // be able to impersonate a business user (GDPR / SOC2 boundary).
    expect(matchPermission(['*:*'], 'user:read')).toBe(false)
    expect(matchPermission(['*:*'], 'role:delete')).toBe(false)
    expect(matchPermission(['*:*'], 'whatever:goes-here')).toBe(false)
  })

  it('tenant:* is TENANT super — matches every non-platform permission', () => {
    // tenant:* is the business-tenant SUPER_ADMIN's wildcard. Matches every
    // business permission but NOT the platform: namespace — a compromised
    // business admin should not be able to reach POST /platform/tenants.
    expect(matchPermission(['tenant:*'], 'user:read')).toBe(true)
    expect(matchPermission(['tenant:*'], 'role:delete')).toBe(true)
    expect(matchPermission(['tenant:*'], 'whatever:goes-here')).toBe(true)
    expect(matchPermission(['tenant:*'], 'platform:tenant:read')).toBe(false)
    expect(matchPermission(['tenant:*'], 'platform:tenant:create')).toBe(false)
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
    // Defensive: required permission strings should always be resource:action.
    // No wildcard should rescue a malformed value, EXCEPT tenant:* — which
    // matches anything not in the platform: namespace, including a no-colon
    // string like "userread".
    expect(matchPermission(['user:*'], 'userread')).toBe(false)
    expect(matchPermission(['*:*'], 'userread')).toBe(false)      // not platform:
    expect(matchPermission(['tenant:*'], 'userread')).toBe(true)  // non-platform
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

  it('business super (tenant:*) matches every non-platform request', () => {
    expect(hasAllPermissions(['tenant:*'], ['user:read', 'role:delete', 'menu:write'])).toBe(true)
    expect(hasAnyPermission(['tenant:*'], ['definitely:not-real'])).toBe(true)
    // But still bounded — platform: stays out of reach
    expect(hasAnyPermission(['tenant:*'], ['platform:tenant:read'])).toBe(false)
  })

  it('platform super (*:*) matches every platform request', () => {
    expect(hasAllPermissions(['*:*'], ['platform:tenant:read', 'platform:tenant:create'])).toBe(true)
    expect(hasAnyPermission(['*:*'], ['user:read'])).toBe(false)
  })

  it('non-array wants returns false', () => {
    expect(hasAllPermissions(['tenant:*'], null)).toBe(false)
    expect(hasAnyPermission(['tenant:*'], 'user:read')).toBe(false)
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
