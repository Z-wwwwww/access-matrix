import { describe, it, expect, vi } from 'vitest'
import { defineComponent } from 'vue'
import { mount } from '@vue/test-utils'
import { createPinia, setActivePinia, defineStore } from 'pinia'

// Stub auth store the directives read from.
vi.mock('@/stores/auth', () => {
  const useAuthStore = defineStore('auth', {
    state: () => ({ roles: [], authorities: [] })
  })
  return { useAuthStore }
})

import { useAuthStore } from '@/stores/auth'
import { vPermission } from '@/directives/permission'

/**
 * Pin the EXACT permission codes used to gate the production admin pages.
 *
 * If anyone renames a button's v-permission expression on Role.vue / Dept.vue
 * (e.g. "role:delete" → "role:remove") without updating the backend
 * @RequiresPermission counterpart, this test still passes — but it forces an
 * intentional update to the inventory below, so the rename can't slip through
 * silently.
 *
 * The pages themselves are too heavy to mount in unit tests (pull in router,
 * vue-query, axios, i18n, …). Instead we mount a tiny clone of just the
 * gated buttons. This is enough to verify the matcher + directive wiring
 * still gates the right codes.
 */

const ROLE_PAGE_BUTTONS = [
  { id: 'role-create', perm: 'role:create' },
  { id: 'role-update', perm: 'role:update' },
  { id: 'role-delete', perm: 'role:delete' }
]

const DEPT_PAGE_BUTTONS = [
  { id: 'dept-create', perm: 'dept:create' },
  { id: 'dept-update', perm: 'dept:update' },
  { id: 'dept-delete', perm: 'dept:delete' }
]

// business-demo's Task page. Worth pinning even though it's a demo module:
// docs/data-scope-demo.md ships real demo logins whose roles hold a NARROW
// task permission set (takahashi_shinichi = task:read only; suzuki_misaki =
// task:create+read; the two 支社 roles have no task:delete), so an ungated
// button here is immediately visible-but-403 for those accounts. It is also
// the reference implementation new business modules get copied from.
const TASK_PAGE_BUTTONS = [
  { id: 'task-create', perm: 'task:create' },
  { id: 'task-update', perm: 'task:update' },
  { id: 'task-delete', perm: 'task:delete' }
]

function mountPage(buttons, authorities) {
  // Render each button inside a parent div so v-permission can remove them
  // via parentNode.removeChild without losing the test wrapper's root.
  const template = `<div class="page">
    ${buttons.map((b) => `<div class="row"><button v-permission="'${b.perm}'" class="btn ${b.id}">x</button></div>`).join('')}
  </div>`

  const Comp = defineComponent({
    directives: { permission: vPermission },
    template
  })

  const pinia = createPinia()
  setActivePinia(pinia)
  useAuthStore().authorities = authorities
  return mount(Comp, { global: { plugins: [pinia] } })
}

describe('Role.vue — button permission gating', () => {
  it('hides every action button when authorities are empty', () => {
    const wrapper = mountPage(ROLE_PAGE_BUTTONS, [])
    for (const b of ROLE_PAGE_BUTTONS) {
      expect(wrapper.find(`.${b.id}`).exists()).toBe(false)
    }
  })

  it('shows ONLY the button matching the granted permission', () => {
    const wrapper = mountPage(ROLE_PAGE_BUTTONS, ['role:update'])
    expect(wrapper.find('.role-create').exists()).toBe(false)
    expect(wrapper.find('.role-update').exists()).toBe(true)
    expect(wrapper.find('.role-delete').exists()).toBe(false)
  })

  it('role:* grants every action on roles', () => {
    const wrapper = mountPage(ROLE_PAGE_BUTTONS, ['role:*'])
    for (const b of ROLE_PAGE_BUTTONS) {
      expect(wrapper.find(`.${b.id}`).exists()).toBe(true)
    }
  })

  it('business super (tenant:*) sees every business action', () => {
    // tenant:* is the business-tenant SUPER_ADMIN's wildcard after the
    // *:* / tenant:* split — should satisfy every business-namespace gate.
    const wrapper = mountPage(ROLE_PAGE_BUTTONS, ['tenant:*'])
    for (const b of ROLE_PAGE_BUTTONS) {
      expect(wrapper.find(`.${b.id}`).exists()).toBe(true)
    }
  })

  it('user:* does NOT leak into role actions', () => {
    // Defence against accidental wildcard scope bleed.
    const wrapper = mountPage(ROLE_PAGE_BUTTONS, ['user:*'])
    for (const b of ROLE_PAGE_BUTTONS) {
      expect(wrapper.find(`.${b.id}`).exists()).toBe(false)
    }
  })
})

describe('Dept.vue — button permission gating', () => {
  it('hides every action button when authorities are empty', () => {
    const wrapper = mountPage(DEPT_PAGE_BUTTONS, [])
    for (const b of DEPT_PAGE_BUTTONS) {
      expect(wrapper.find(`.${b.id}`).exists()).toBe(false)
    }
  })

  it('dept:* grants every action on departments', () => {
    const wrapper = mountPage(DEPT_PAGE_BUTTONS, ['dept:*'])
    for (const b of DEPT_PAGE_BUTTONS) {
      expect(wrapper.find(`.${b.id}`).exists()).toBe(true)
    }
  })

  it('a user with only role:* still sees no dept buttons', () => {
    const wrapper = mountPage(DEPT_PAGE_BUTTONS, ['role:*'])
    for (const b of DEPT_PAGE_BUTTONS) {
      expect(wrapper.find(`.${b.id}`).exists()).toBe(false)
    }
  })

  it('mixed exact + wildcard — only dept:create granted', () => {
    const wrapper = mountPage(DEPT_PAGE_BUTTONS, ['dept:create', 'role:read'])
    expect(wrapper.find('.dept-create').exists()).toBe(true)
    expect(wrapper.find('.dept-update').exists()).toBe(false)
    expect(wrapper.find('.dept-delete').exists()).toBe(false)
  })
})

describe('Task.vue — button permission gating', () => {
  it('read-only role (task:read) sees no write buttons', () => {
    // takahashi_shinichi / 京都連絡担当 in the seeded demo tenant.
    const wrapper = mountPage(TASK_PAGE_BUTTONS, ['task:read'])
    for (const b of TASK_PAGE_BUTTONS) {
      expect(wrapper.find(`.${b.id}`).exists()).toBe(false)
    }
  })

  it('task:create+read shows only 新規, not edit/delete', () => {
    // suzuki_misaki / 一般社員 in the seeded demo tenant.
    const wrapper = mountPage(TASK_PAGE_BUTTONS, ['task:create', 'task:read'])
    expect(wrapper.find('.task-create').exists()).toBe(true)
    expect(wrapper.find('.task-update').exists()).toBe(false)
    expect(wrapper.find('.task-delete').exists()).toBe(false)
  })

  it('a role without task:delete hides only the delete button', () => {
    // yamada_hanako / 東京支社長 and sato_ken / 大阪支社課長.
    const wrapper = mountPage(TASK_PAGE_BUTTONS, ['task:create', 'task:read', 'task:update'])
    expect(wrapper.find('.task-create').exists()).toBe(true)
    expect(wrapper.find('.task-update').exists()).toBe(true)
    expect(wrapper.find('.task-delete').exists()).toBe(false)
  })

  it('business super (tenant:*) sees every task action', () => {
    const wrapper = mountPage(TASK_PAGE_BUTTONS, ['tenant:*'])
    for (const b of TASK_PAGE_BUTTONS) {
      expect(wrapper.find(`.${b.id}`).exists()).toBe(true)
    }
  })

  it('platform super (*:*) does NOT leak into business task actions', () => {
    // *:* only covers the platform: namespace — a platform admin must not
    // inherit business-tenant task writes.
    const wrapper = mountPage(TASK_PAGE_BUTTONS, ['*:*'])
    for (const b of TASK_PAGE_BUTTONS) {
      expect(wrapper.find(`.${b.id}`).exists()).toBe(false)
    }
  })
})
