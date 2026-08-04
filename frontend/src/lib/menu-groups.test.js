import { describe, it, expect } from 'vitest'
import {
  ADMIN_GROUP_MIN_SORT,
  FOOTER_MIN_SORT,
  topLevelLeafsOf,
  topLevelGroupsOf,
  adminMenusOf,
  footerMenusOf
} from './menu-groups'

/**
 * The regression these exist for: the sidebar used to threshold on `m.sort`,
 * a field the backend never sends (`MenuNode` has **sortOrder** — same name used
 * by /menu/me, MenuPicker and the menu-admin console). `undefined ?? 0 === 0`
 * put every menu in the "regular" band, so the 管理者設定 group and the pinned
 * footer band were permanently empty — their markup and i18n exist but could
 * never render, and an admin bumping a menu's sort to 9500 in the console saw
 * no effect and no error.
 *
 * Every case below therefore feeds `sortOrder` only. A `sort`-reading
 * implementation fails all three band assertions.
 */
const menu = (code, sortOrder, extra = {}) => ({
  id: code, code, path: '/' + code, title: code, sortOrder, ...extra
})
const group = (code, sortOrder, extra = {}) =>
  menu(code, sortOrder, { children: [menu(code + '.child', 1)], ...extra })

describe('menu-groups — bands are keyed on sortOrder', () => {
  const menus = [
    menu('overview', 0),
    group('system', 10),
    menu('reports', ADMIN_GROUP_MIN_SORT),        // boundary: still regular
    menu('settings', ADMIN_GROUP_MIN_SORT + 1),   // first admin-group value
    menu('audit', FOOTER_MIN_SORT),               // boundary: still admin group
    menu('help', FOOTER_MIN_SORT + 1)             // first footer value
  ]

  it('regular leaves are everything up to and including 9000', () => {
    expect(topLevelLeafsOf(menus).map((m) => m.code)).toEqual(['overview', 'reports'])
  })

  it('groups (with children) are split out of the leaves', () => {
    expect(topLevelGroupsOf(menus).map((m) => m.code)).toEqual(['system'])
  })

  it('the 管理者設定 band is 9000 < sortOrder <= 10000 — and is NOT empty', () => {
    expect(adminMenusOf(menus).map((m) => m.code)).toEqual(['settings', 'audit'])
  })

  it('the pinned footer band is sortOrder > 10000', () => {
    expect(footerMenusOf(menus).map((m) => m.code)).toEqual(['help'])
  })

  it('the four bands are mutually exclusive and cover every visible menu', () => {
    const all = [
      ...topLevelLeafsOf(menus),
      ...topLevelGroupsOf(menus),
      ...adminMenusOf(menus),
      ...footerMenusOf(menus)
    ].map((m) => m.code)
    expect(new Set(all).size).toBe(all.length)               // no duplicates
    expect(all.sort()).toEqual(menus.map((m) => m.code).sort()) // nothing dropped
  })
})

describe('menu-groups — edge cases', () => {
  it('a menu with no sortOrder is treated as 0 (regular band)', () => {
    const m = [{ id: 'x', code: 'x' }]
    expect(topLevelLeafsOf(m).map((n) => n.code)).toEqual(['x'])
    expect(adminMenusOf(m)).toEqual([])
    expect(footerMenusOf(m)).toEqual([])
  })

  it('hidden menus are excluded from every band', () => {
    const m = [
      menu('h1', 0, { hide: 1 }),
      group('h2', 10, { hide: 1 }),
      menu('h3', 9500, { hide: 1 }),
      menu('h4', 20000, { hide: 1 })
    ]
    expect(topLevelLeafsOf(m)).toEqual([])
    expect(topLevelGroupsOf(m)).toEqual([])
    expect(adminMenusOf(m)).toEqual([])
    expect(footerMenusOf(m)).toEqual([])
  })

  it('an empty children array counts as a leaf, not a group', () => {
    const m = [menu('e', 0, { children: [] })]
    expect(topLevelLeafsOf(m).map((n) => n.code)).toEqual(['e'])
    expect(topLevelGroupsOf(m)).toEqual([])
  })

  it('tolerates null / undefined input', () => {
    for (const fn of [topLevelLeafsOf, topLevelGroupsOf, adminMenusOf, footerMenusOf]) {
      expect(fn(null)).toEqual([])
      expect(fn(undefined)).toEqual([])
    }
  })
})
