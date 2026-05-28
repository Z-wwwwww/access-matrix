import { describe, it, expect, vi } from 'vitest'
import { defineComponent } from 'vue'
import { mount } from '@vue/test-utils'
import { createPinia, setActivePinia, defineStore } from 'pinia'

// Stub the auth store the directives read from — without this we'd need the
// full @/stores/auth which pulls in axios + router + i18n config.
vi.mock('@/stores/auth', () => {
  const useAuthStore = defineStore('auth', {
    state: () => ({ roles: [], authorities: [] })
  })
  return { useAuthStore }
})

import { useAuthStore } from '@/stores/auth'
import { vPermission, vAnyPermission, vRole, vAnyRole } from './permission'

// The directive removes the element via `el.parentNode.removeChild(el)`. If
// the guarded element is the wrapper's root, there's no parent to remove from,
// so every test wraps the button in a <div> and looks for the button by class.
function mountGuard({ directive, name, bindingExpr, patchStore }) {
  const Comp = defineComponent({
    directives: { [name]: directive },
    template: `<div class="host"><button class="guarded" v-${name}="${bindingExpr}">x</button></div>`
  })
  const pinia = createPinia()
  setActivePinia(pinia)
  patchStore(useAuthStore())
  return mount(Comp, { global: { plugins: [pinia] } })
}

describe('v-permission directive', () => {
  it('keeps the element when user holds the exact permission', () => {
    const wrapper = mountGuard({
      directive: vPermission,
      name: 'permission',
      bindingExpr: "'user:read'",
      patchStore: (s) => { s.authorities = ['user:read'] }
    })
    expect(wrapper.find('.guarded').exists()).toBe(true)
  })

  it('removes the element when user lacks the permission', () => {
    const wrapper = mountGuard({
      directive: vPermission,
      name: 'permission',
      bindingExpr: "'user:delete'",
      patchStore: (s) => { s.authorities = ['user:read'] }
    })
    expect(wrapper.find('.guarded').exists()).toBe(false)
  })

  it('business super (tenant:*) keeps every business-guarded button', () => {
    // The exact bug we fixed: previously a user with the super wildcard had
    // every v-permission button removed because the matcher only did exact compare.
    // After the *:* / tenant:* split, tenant:* is the business super wildcard
    // and should satisfy every business permission gate.
    const wrapper = mountGuard({
      directive: vPermission,
      name: 'permission',
      bindingExpr: "'user:read'",
      patchStore: (s) => { s.authorities = ['tenant:*'] }
    })
    expect(wrapper.find('.guarded').exists()).toBe(true)
  })

  it('array of permissions — ALL required', () => {
    const wrapper = mountGuard({
      directive: vPermission,
      name: 'permission',
      bindingExpr: "['user:read', 'user:write']",
      patchStore: (s) => { s.authorities = ['user:read'] } // missing user:write
    })
    expect(wrapper.find('.guarded').exists()).toBe(false)
  })

  it('resource:* covers a specific action button', () => {
    const wrapper = mountGuard({
      directive: vPermission,
      name: 'permission',
      bindingExpr: "'user:read'",
      patchStore: (s) => { s.authorities = ['user:*'] }
    })
    expect(wrapper.find('.guarded').exists()).toBe(true)
  })
})

describe('v-any-permission directive', () => {
  it('keeps element when ANY listed permission matches', () => {
    const wrapper = mountGuard({
      directive: vAnyPermission,
      name: 'any-permission',
      bindingExpr: "['user:write', 'user:read']",
      patchStore: (s) => { s.authorities = ['user:read'] }
    })
    expect(wrapper.find('.guarded').exists()).toBe(true)
  })

  it('removes element when NONE match', () => {
    const wrapper = mountGuard({
      directive: vAnyPermission,
      name: 'any-permission',
      bindingExpr: "['user:write', 'user:delete']",
      patchStore: (s) => { s.authorities = ['role:read'] }
    })
    expect(wrapper.find('.guarded').exists()).toBe(false)
  })
})

describe('v-role / v-any-role directives — exact match, no wildcards', () => {
  it('v-role removes element when role missing', () => {
    const wrapper = mountGuard({
      directive: vRole,
      name: 'role',
      bindingExpr: "'admin'",
      patchStore: (s) => { s.roles = ['editor'] }
    })
    expect(wrapper.find('.guarded').exists()).toBe(false)
  })

  it('v-any-role keeps element when any role matches', () => {
    const wrapper = mountGuard({
      directive: vAnyRole,
      name: 'any-role',
      bindingExpr: "['admin', 'editor']",
      patchStore: (s) => { s.roles = ['editor'] }
    })
    expect(wrapper.find('.guarded').exists()).toBe(true)
  })
})
