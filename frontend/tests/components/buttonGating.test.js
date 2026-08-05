import { describe, it, expect, vi } from 'vitest'
import { defineComponent } from 'vue'
import { mount } from '@vue/test-utils'
import { createPinia, setActivePinia, defineStore } from 'pinia'
import { readFileSync } from 'node:fs'
import { resolve } from 'node:path'

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

// Platform Job console. The backend splits jobs into FOUR permissions
// (platform:job:read / :config / :run / :toggle) and enforces each one
// separately on JobAdminController — but the page shipped with no gating at
// all, so a holder of platform:job:read alone was offered "edit cron",
// "run now" and the enable/disable switch, every one of which 403s. Backend
// enforcement means it was never a security hole; it is the console lying
// about what the operator can do. Note the toggle is NOT in this inventory:
// it can't be v-permission'd away without also hiding the only display of the
// enabled state, so Job.vue probes the permission and renders a read-only
// badge instead (see canToggle there).
const JOB_PAGE_BUTTONS = [
  { id: 'job-config', perm: 'platform:job:config' },
  { id: 'job-run', perm: 'platform:job:run' }
]

// Platform Dict console — same story: platform:dict:create / :update / :delete
// are enforced per endpoint on DictAdminController while the page rendered
// create / edit / delete unconditionally, for both dict types and items.
const DICT_PAGE_BUTTONS = [
  { id: 'dict-create', perm: 'platform:dict:create' },
  { id: 'dict-update', perm: 'platform:dict:update' },
  { id: 'dict-delete', perm: 'platform:dict:delete' }
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

describe('Job.vue — button permission gating', () => {
  it('platform:job:read alone offers no write action', () => {
    // The exact shape of a custom read-only ops role: the console must not
    // show buttons that JobAdminController will answer with 403.
    const wrapper = mountPage(JOB_PAGE_BUTTONS, ['platform:job:read'])
    for (const b of JOB_PAGE_BUTTONS) {
      expect(wrapper.find(`.${b.id}`).exists()).toBe(false)
    }
  })

  it('platform:job:run shows run-now but not edit-cron', () => {
    const wrapper = mountPage(JOB_PAGE_BUTTONS, ['platform:job:read', 'platform:job:run'])
    expect(wrapper.find('.job-run').exists()).toBe(true)
    expect(wrapper.find('.job-config').exists()).toBe(false)
  })

  it('platform:* (PLATFORM_OPERATOR, V55) sees every job action', () => {
    const wrapper = mountPage(JOB_PAGE_BUTTONS, ['platform:*'])
    for (const b of JOB_PAGE_BUTTONS) {
      expect(wrapper.find(`.${b.id}`).exists()).toBe(true)
    }
  })

  it('a business super (tenant:*) sees no platform job action', () => {
    const wrapper = mountPage(JOB_PAGE_BUTTONS, ['tenant:*'])
    for (const b of JOB_PAGE_BUTTONS) {
      expect(wrapper.find(`.${b.id}`).exists()).toBe(false)
    }
  })
})

describe('Dict.vue — button permission gating', () => {
  it('platform:dict:read alone offers no write action', () => {
    const wrapper = mountPage(DICT_PAGE_BUTTONS, ['platform:dict:read'])
    for (const b of DICT_PAGE_BUTTONS) {
      expect(wrapper.find(`.${b.id}`).exists()).toBe(false)
    }
  })

  it('platform:dict:update shows only edit', () => {
    const wrapper = mountPage(DICT_PAGE_BUTTONS, ['platform:dict:read', 'platform:dict:update'])
    expect(wrapper.find('.dict-create').exists()).toBe(false)
    expect(wrapper.find('.dict-update').exists()).toBe(true)
    expect(wrapper.find('.dict-delete').exists()).toBe(false)
  })

  it('platform:* sees every dict action', () => {
    const wrapper = mountPage(DICT_PAGE_BUTTONS, ['platform:*'])
    for (const b of DICT_PAGE_BUTTONS) {
      expect(wrapper.find(`.${b.id}`).exists()).toBe(true)
    }
  })
})

/**
 * The suites above pin the permission CODES but render synthetic buttons, so a
 * page that forgets `v-permission` entirely still passes them — which is exactly
 * how Job.vue and Dict.vue shipped ungated while every sibling console gated.
 *
 * This suite closes that hole for the write-action pages: it reads the real
 * `.vue` sources and asserts each write permission actually appears in a
 * `v-permission` expression. It is a drift guard, not a rendering test — it
 * cannot tell WHICH button carries the code, but it does fail the moment a page
 * with permission-gated endpoints has no gate for one of them.
 */
// vitest runs with the frontend package root as cwd (see vitest.config.js).
const src = (rel) => readFileSync(resolve(process.cwd(), 'src', rel), 'utf8')

const GATED_PAGES = [
  { file: 'views/system/Role/Role.vue',            perms: ['role:create', 'role:update', 'role:delete'] },
  { file: 'views/system/Dept/Dept.vue',            perms: ['dept:create', 'dept:update', 'dept:delete'] },
  { file: 'views/demo/Task/Task.vue',              perms: ['task:create', 'task:update', 'task:delete'] },
  { file: 'views/platform/Menu/Menu.vue',          perms: ['platform:menu:create', 'platform:menu:update', 'platform:menu:delete'] },
  { file: 'views/platform/Job/Job.vue',            perms: ['platform:job:config', 'platform:job:run'] },
  { file: 'views/platform/Dict/Dict.vue',          perms: ['platform:dict:create', 'platform:dict:update', 'platform:dict:delete'] }
]

describe('admin pages actually carry their v-permission gates', () => {
  for (const page of GATED_PAGES) {
    it(`${page.file} gates every write action`, () => {
      const text = src(page.file)
      const gated = new Set(
        [...text.matchAll(/v-permission="'([^']+)'"/g)].map((m) => m[1])
      )
      for (const perm of page.perms) {
        expect(gated, `${page.file} is missing v-permission="'${perm}'"`).toContain(perm)
      }
    })
  }

  it("Job.vue's enable/disable switch is permission-probed rather than left open", () => {
    // The switch can't be removed by the directive (it is also the only display
    // of the enabled state), so it must be guarded by an explicit probe.
    const text = src('views/platform/Job/Job.vue')
    expect(text).toContain("hasPermission('platform:job:toggle')")
    expect(text).toMatch(/v-if="canToggle"/)
  })
})
