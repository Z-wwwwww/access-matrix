import { describe, it, expect } from 'vitest'
import { readFileSync } from 'node:fs'
import { resolve } from 'node:path'

import en from '@/lang/en'
import ja from '@/lang/ja_JP'
import ko from '@/lang/ko_KR'
import zhCN from '@/lang/zh_CN'
import zhTW from '@/lang/zh_TW'

/**
 * RoleEdit's save() binds selectedPermIds / selectedMenuIds / selectedDeptIds as
 * the role's COMPLETE new set — the bind endpoints replace, they don't merge.
 *
 * Those refs are cleared to [] every time the drawer opens and only refilled
 * when their GET succeeds. The three GETs go through Promise.allSettled, so a
 * transient failure on getRolePermissionsApi is swallowed: no toast, no banner,
 * every checkbox simply renders unchecked — visually identical to "this role
 * genuinely has no permissions". An admin who opens the drawer to fix a typo in
 * the description and hits Save then writes the EMPTY set, silently stripping
 * every permission the role had.
 *
 * The fix is fail-closed: remember that a selection failed to load, tell the
 * user, and refuse to save until the drawer is reopened.
 *
 * This is a source-drift guard rather than a mounted-component test, following
 * buttonGating.test.js: these admin pages pull in router / vue-query / axios /
 * i18n and are too heavy to mount, and a synthetic clone would not prove
 * anything about the real save() path.
 */
const SRC = readFileSync(
  resolve(process.cwd(), 'src/views/system/Role/RoleEdit.vue'),
  'utf8'
)
const USER_SRC = readFileSync(
  resolve(process.cwd(), 'src/views/system/User/UserEdit.vue'),
  'utf8'
)

/** Body of a named function, comments stripped — a mention in a comment is not a guard. */
function functionBody(src, signature) {
  const start = src.indexOf(signature)
  expect(start, `${signature} not found — did it get renamed?`).toBeGreaterThan(-1)
  const open = src.indexOf('{', start)
  let depth = 0
  let end = -1
  for (let i = open; i < src.length; i++) {
    if (src[i] === '{') depth++
    else if (src[i] === '}') {
      depth--
      if (depth === 0) { end = i; break }
    }
  }
  expect(end, `could not find the end of ${signature}`).toBeGreaterThan(-1)
  return src.slice(open, end)
    .replace(/\/\*[\s\S]*?\*\//g, '')
    .replace(/\/\/[^\n]*/g, '')
}

describe('RoleEdit — a failed selection load must not be saved as empty', () => {
  it('tracks the failure when any selection GET does not succeed', () => {
    expect(SRC, 'no selectionLoadFailed flag at all').toContain('selectionLoadFailed')
    // Set on the failure branch of the per-role loads.
    expect(SRC).toMatch(/selectionLoadFailed\.value\s*=\s*true/)
    // Cleared on every open, so reopening genuinely retries.
    expect(SRC).toMatch(/selectionLoadFailed\.value\s*=\s*false/)
  })

  it('save() refuses while the flag is set, before any bind call runs', () => {
    const body = functionBody(SRC, 'async function save()')
    // An actual read of the flag followed by an early return — not a comment.
    expect(body, 'save() has no selectionLoadFailed guard')
      .toMatch(/if\s*\(\s*selectionLoadFailed\.value\s*\)[\s\S]{0,200}?return/)

    const guardAt = body.search(/if\s*\(\s*selectionLoadFailed\.value\s*\)/)
    const firstBindAt = body.indexOf('bindRolePermissionsApi')
    expect(firstBindAt, 'bind call not found in save()').toBeGreaterThan(-1)
    expect(guardAt, 'the guard must run before the bind calls').toBeLessThan(firstBindAt)
  })

  it('the user-facing message exists in all five locales', () => {
    for (const [name, msgs] of Object.entries({ en, ja_JP: ja, ko_KR: ko, zh_CN: zhCN, zh_TW: zhTW })) {
      expect(msgs.role?.edit?.message?.loadSelectionsFailed, `${name} is missing the key`)
        .toBeTruthy()
    }
  })
})

/**
 * Same invariant, higher stakes: UserEdit's save() calls assignUserRolesApi with
 * selectedRoleIds as the COMPLETE new set (the backend soft-deletes every link and
 * re-inserts). A swallowed load therefore doesn't just lose data — it revokes the
 * user's access. Two distinct failure modes had to be closed:
 *   - a thrown request → the ref was explicitly set to [] → save strips all roles;
 *   - a non-zero business code → the assignment simply didn't fire, so the ref kept
 *     the PREVIOUSLY opened user's roles → save copies user A's roles onto user B.
 */
describe('UserEdit — a failed role load must not be saved as the new role set', () => {
  it('clears the selection before loading, so a stale one cannot survive', () => {
    const body = functionBody(USER_SRC, 'watch(() => props.open')
    const clearAt = body.search(/selectedRoleIds\.value\s*=\s*\[\]/)
    const loadAt = body.indexOf('getUserRolesApi')
    expect(clearAt, 'selectedRoleIds is never cleared on open').toBeGreaterThan(-1)
    expect(loadAt, 'getUserRolesApi call not found').toBeGreaterThan(-1)
    expect(clearAt, 'the clear must happen before the load').toBeLessThan(loadAt)
  })

  it('flags both a thrown request and a non-zero business code', () => {
    const body = functionBody(USER_SRC, 'watch(() => props.open')
    expect(body).toMatch(/else\s+roleLoadFailed\.value\s*=\s*true/)   // code !== 0
    expect(body).toMatch(/catch\s*\{[\s\S]{0,120}?roleLoadFailed\.value\s*=\s*true/) // threw
  })

  it('save() refuses while the flag is set, before assignUserRolesApi runs', () => {
    const body = functionBody(USER_SRC, 'async function save()')
    expect(body, 'save() has no roleLoadFailed guard')
      .toMatch(/if\s*\(\s*roleLoadFailed\.value\s*\)[\s\S]{0,200}?return/)

    const guardAt = body.search(/if\s*\(\s*roleLoadFailed\.value\s*\)/)
    const assignAt = body.indexOf('assignUserRolesApi')
    expect(assignAt, 'assignUserRolesApi not found in save()').toBeGreaterThan(-1)
    expect(guardAt, 'the guard must run before the role assignment').toBeLessThan(assignAt)
  })

  it('the user-facing message exists in all five locales', () => {
    for (const [name, msgs] of Object.entries({ en, ja_JP: ja, ko_KR: ko, zh_CN: zhCN, zh_TW: zhTW })) {
      expect(msgs.user?.edit?.message?.loadRolesFailed, `${name} is missing the key`).toBeTruthy()
    }
  })
})
