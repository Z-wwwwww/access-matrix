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

describe('RoleEdit — a failed selection load must not be saved as empty', () => {
  it('tracks the failure when any selection GET does not succeed', () => {
    expect(SRC, 'no selectionLoadFailed flag at all').toContain('selectionLoadFailed')
    // Set on the failure branch of the per-role loads.
    expect(SRC).toMatch(/selectionLoadFailed\.value\s*=\s*true/)
    // Cleared on every open, so reopening genuinely retries.
    expect(SRC).toMatch(/selectionLoadFailed\.value\s*=\s*false/)
  })

  /** Body of `async function save()`, comments stripped — a mention in a comment is not a guard. */
  function saveBody() {
    const start = SRC.indexOf('async function save()')
    expect(start, 'save() not found — did it get renamed?').toBeGreaterThan(-1)
    const open = SRC.indexOf('{', start)
    let depth = 0
    let end = -1
    for (let i = open; i < SRC.length; i++) {
      if (SRC[i] === '{') depth++
      else if (SRC[i] === '}') {
        depth--
        if (depth === 0) { end = i; break }
      }
    }
    expect(end, 'could not find the end of save()').toBeGreaterThan(-1)
    return SRC.slice(open, end)
      .replace(/\/\*[\s\S]*?\*\//g, '')
      .replace(/\/\/[^\n]*/g, '')
  }

  it('save() refuses while the flag is set, before any bind call runs', () => {
    const body = saveBody()
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
