import { describe, it, expect } from 'vitest'
import { readFileSync, readdirSync, statSync } from 'node:fs'
import { resolve, join, relative } from 'node:path'

import en from '@/lang/en.js'
import jaJP from '@/lang/ja_JP.js'
import zhCN from '@/lang/zh_CN.js'
import zhTW from '@/lang/zh_TW.js'
import koKR from '@/lang/ko_KR.js'

/**
 * Two things the five locale files must satisfy, neither of which any existing
 * test covers.
 *
 * <p><b>Parity.</b> A key added to one file and forgotten in the others renders
 * as the raw key string ("user.edit.title") for anyone on that language — the
 * project's own manual checklist has carried "语言切换:5 语言下新功能文案都不出现
 * key 原文 / `__TODO__`" as a human step for a long time, which is exactly the
 * kind of check a machine should be doing. Five files × a growing key set is
 * not something eyes keep in sync.
 *
 * <p><b>Reachability.</b> A `t('…')` call whose key does not exist anywhere is
 * the same failure in the other direction, and it survives review because the
 * page still renders — just with a key where a sentence should be.
 *
 * <p>Only STATIC single-token literals are checked. Interpolated keys
 * (`t(\`common.status.${x}\`)`) and variables are out of reach here and stay a
 * human concern; the dict system covers most of that ground anyway.
 */

const SRC = resolve(__dirname, '../../src')

const LOCALES = { en, ja_JP: jaJP, zh_CN: zhCN, zh_TW: zhTW, ko_KR: koKR }
/** ja_JP is the reference: the app's default locale and the one authored first. */
const REFERENCE = 'ja_JP'

function flatten(node, prefix = '', out = new Set()) {
  if (node && typeof node === 'object' && !Array.isArray(node)) {
    for (const [k, v] of Object.entries(node)) flatten(v, prefix ? `${prefix}.${k}` : k, out)
  } else {
    out.add(prefix)
  }
  return out
}

const keys = Object.fromEntries(Object.entries(LOCALES).map(([l, o]) => [l, flatten(o)]))

function sourceFiles(dir) {
  const out = []
  for (const entry of readdirSync(dir)) {
    if (entry === 'lang' || entry === 'generated') continue
    const full = join(dir, entry)
    if (statSync(full).isDirectory()) out.push(...sourceFiles(full))
    else if (/\.(vue|js)$/.test(entry) && !entry.endsWith('.test.js')) out.push(full)
  }
  return out
}

/** `t('a.b.c')` / `$t("a.b.c")` — static literals only, no template strings. */
const T_CALL = /\$?\bt\(\s*(['"])([A-Za-z0-9_$]+(?:\.[A-Za-z0-9_$]+)+)\1/g

function usedKeys() {
  const used = new Map()   // key → first file that uses it
  for (const file of sourceFiles(SRC)) {
    const source = readFileSync(file, 'utf-8')
    T_CALL.lastIndex = 0
    let m
    while ((m = T_CALL.exec(source)) !== null) {
      if (!used.has(m[2])) used.set(m[2], relative(SRC, file).replace(/\\/g, '/'))
    }
  }
  return used
}

describe('i18n key parity', () => {
  it('the reference locale is non-trivial (guards against a vacuous pass)', () => {
    expect(keys[REFERENCE].size).toBeGreaterThan(500)
  })

  it.each(Object.keys(LOCALES).filter((l) => l !== REFERENCE))(
    '%s defines exactly the keys ja_JP does',
    (locale) => {
      const missing = [...keys[REFERENCE]].filter((k) => !keys[locale].has(k)).sort()
      const extra = [...keys[locale]].filter((k) => !keys[REFERENCE].has(k)).sort()

      expect(missing, `${locale} is missing keys that ja_JP has — they render as the raw key`).toEqual([])
      expect(extra, `${locale} has keys ja_JP does not — the reference is incomplete`).toEqual([])
    }
  )

  it('no locale still carries a __TODO__ placeholder', () => {
    // I18nPermissionPatcher seeds permission labels as __TODO__ in dev; shipping
    // one means a permission renders as that literal in the role editor.
    const offenders = []
    for (const [locale, tree] of Object.entries(LOCALES)) {
      const walk = (node, path) => {
        if (node && typeof node === 'object') {
          for (const [k, v] of Object.entries(node)) walk(v, path ? `${path}.${k}` : k)
        } else if (typeof node === 'string' && node.includes('__TODO__')) {
          offenders.push(`${locale}:${path}`)
        }
      }
      walk(tree, '')
    }
    expect(offenders).toEqual([])
  })

  it('every statically-referenced t() key exists in the reference locale', () => {
    const used = usedKeys()
    expect(used.size, 'the scan found no t() calls — the matcher broke').toBeGreaterThan(100)

    const unknown = [...used.entries()]
      .filter(([k]) => !keys[REFERENCE].has(k))
      .map(([k, where]) => `${k}  (${where})`)
      .sort()

    expect(unknown, 'these render as the literal key string').toEqual([])
  })
})
