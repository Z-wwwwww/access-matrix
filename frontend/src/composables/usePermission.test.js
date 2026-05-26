import { describe, it, expect, beforeEach } from 'vitest'
import { setActivePinia, createPinia, defineStore } from 'pinia'
import { usePermission } from './usePermission'

// We can't import the real `@/stores/auth` because that pulls in axios + router
// + i18n config. Stub a Pinia store that exposes the same shape (`roles`,
// `authorities`) that `usePermission` reads from.
//
// Because usePermission resolves the store by name ("auth"), we replace its
// import target with a same-named store via Vitest's module mock.

import { vi } from 'vitest'
vi.mock('@/stores/auth', () => {
  const useAuthStore = defineStore('auth', {
    state: () => ({ roles: [], authorities: [] })
  })
  return { useAuthStore }
})

import { useAuthStore } from '@/stores/auth'

describe('usePermission', () => {
  beforeEach(() => {
    setActivePinia(createPinia())
  })

  it('hasRole — exact match against the auth store', () => {
    const store = useAuthStore()
    store.roles = ['admin', 'editor']
    const { hasRole } = usePermission()

    expect(hasRole('admin')).toBe(true)
    expect(hasRole('viewer')).toBe(false)
  })

  it('hasAnyRole accepts a single string or an array', () => {
    const store = useAuthStore()
    store.roles = ['editor']
    const { hasAnyRole } = usePermission()

    expect(hasAnyRole('editor')).toBe(true)
    expect(hasAnyRole(['admin', 'editor'])).toBe(true)
    expect(hasAnyRole(['admin', 'viewer'])).toBe(false)
  })

  it('hasPermission honours *:* wildcard', () => {
    // The whole point of the wildcard-aware composable: a super admin holding
    // only `*:*` should still see every action button.
    const store = useAuthStore()
    store.authorities = ['*:*']
    const { hasPermission } = usePermission()

    expect(hasPermission('user:read')).toBe(true)
    expect(hasPermission('role:delete')).toBe(true)
    expect(hasPermission('whatever:goes')).toBe(true)
  })

  it('hasPermission honours resource:* wildcard', () => {
    const store = useAuthStore()
    store.authorities = ['user:*']
    const { hasPermission } = usePermission()

    expect(hasPermission('user:read')).toBe(true)
    expect(hasPermission('user:delete')).toBe(true)
    expect(hasPermission('role:read')).toBe(false)
  })

  it('hasAnyPermission accepts a single permission or an array', () => {
    const store = useAuthStore()
    store.authorities = ['role:read']
    const { hasAnyPermission } = usePermission()

    expect(hasAnyPermission('role:read')).toBe(true)
    expect(hasAnyPermission(['user:read', 'role:read'])).toBe(true)
    expect(hasAnyPermission(['user:read', 'menu:read'])).toBe(false)
  })

  it('empty authorities → every check returns false', () => {
    const store = useAuthStore()
    store.authorities = []
    store.roles = []
    const { hasRole, hasPermission, hasAnyPermission } = usePermission()

    expect(hasRole('admin')).toBe(false)
    expect(hasPermission('user:read')).toBe(false)
    expect(hasAnyPermission(['user:read', 'role:read'])).toBe(false)
  })
})
