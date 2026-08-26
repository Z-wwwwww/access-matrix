import { describe, it, expect } from 'vitest'
import { readFileSync, readdirSync, statSync } from 'node:fs'
import { resolve, join, relative } from 'node:path'

/**
 * Source-drift guard: every handler that calls a WRITE endpoint must react to a
 * non-zero business code.
 *
 * `services/request.js` REJECTS on only two codes — 401 and 700. Everything else
 * (404 NOT_FOUND, 701 VALIDATION_FAILED, 702 OPTIMISTIC_LOCK_CONFLICT, 703 IN_USE,
 * 710/720/730) rides HTTP 200 through `GlobalExceptionHandler`'s
 * `ResponseEntity.ok(...)` and RESOLVES. So a `try { … } catch { toast.error }`
 * around the call catches nothing for those codes: the handler must inspect
 * `res.data.code` itself.
 *
 * The defect this pins is silent, not loud. `User.vue`'s status toggle had
 *
 *   if (res.data.code === 0) { toast.success(...); fetchData() }
 *
 * with no `else`. Disabling a user the server refused (the row was deleted in
 * another tab → 404; ConcurrentEdit.requireApplied lost the version race → 702)
 * produced NOTHING: no toast, and no refetch, so the row kept rendering the old
 * status. The admin's only signal was that the button appeared to do nothing.
 * Every sibling handler in the same file already had the branch — which is
 * exactly why an eyeball review misses it and a scan doesn't.
 *
 * The scan is over SOURCE, not over rendered components, for the same reason
 * `buttonGating.test.js` reads `.vue` files: a synthetic clone of the handler
 * would pass while the real page kept the bug. It accepts either project idiom —
 * an early-return `code !== 0` check, or a `code === 0` block with an `else` —
 * and does not try to prove the branch is reachable; the point is to make the
 * omission impossible to add silently.
 */

const SRC = resolve(__dirname, '../../src')

/**
 * API names whose prefix marks them as a write. Read helpers (`getX`, `listX`,
 * `probeX`) are out of scope: a failed read shows an empty list, which is its own
 * (visible) signal, and several of them are deliberately non-fatal decoration.
 */
const WRITE_VERBS = [
  'add', 'create', 'update', 'edit', 'delete', 'remove', 'bind', 'assign',
  'change', 'reset', 'force', 'enable', 'disable', 'run', 'redrive', 'suspend',
  'resume', 'start', 'terminate', 'resend', 'mark', 'accept', 'save', 'hardDelete'
]

/**
 * Handlers that deliberately ignore the response code. Each entry needs a reason
 * — an empty allowlist is the goal, and adding to it should feel deliberate.
 */
const ALLOWLIST = new Map([
  [
    'components/layout/SupportSessionBanner.vue::terminate',
    'Bookkeeping-only: the ops token is already restored locally and the banner ' +
      'is gone before the call is made. Its own comment says "Best-effort — never ' +
      'block the exit on this bookkeeping call", and the handler then hard-navigates ' +
      'away, so there is no surface left to report onto.'
  ]
])

const callPattern = new RegExp(
  String.raw`await\s+(?:${WRITE_VERBS.join('|')})[A-Za-z0-9_]*Api\s*\(`
)
const handlerPattern = /async\s+function\s+([A-Za-z0-9_$]+)\s*\(/g
const earlyReturnCheck = /\.code\s*!==?\s*0/
const zeroCheck = /\.code\s*===?\s*0/

function vueFiles(dir) {
  const out = []
  for (const entry of readdirSync(dir)) {
    const full = join(dir, entry)
    if (statSync(full).isDirectory()) out.push(...vueFiles(full))
    else if (entry.endsWith('.vue')) out.push(full)
  }
  return out
}

/** Body of the function starting at `from`, matched by brace depth. */
function functionBody(source, from) {
  const open = source.indexOf('{', from)
  if (open === -1) return ''
  let depth = 0
  for (let i = open; i < source.length; i++) {
    if (source[i] === '{') depth++
    else if (source[i] === '}' && --depth === 0) return source.slice(open, i + 1)
  }
  return source.slice(open)
}

function collectWriteHandlers() {
  const found = []
  for (const file of vueFiles(SRC)) {
    const source = readFileSync(file, 'utf-8')
    const id = relative(SRC, file).replace(/\\/g, '/')
    handlerPattern.lastIndex = 0
    let m
    while ((m = handlerPattern.exec(source)) !== null) {
      const body = functionBody(source, m.index)
      if (!callPattern.test(body)) continue
      found.push({ key: `${id}::${m[1]}`, body })
    }
  }
  return found
}

describe('write handlers must surface a non-zero business code', () => {
  const handlers = collectWriteHandlers()

  it('finds the write handlers at all (guards against a broken scan)', () => {
    // A refactor that renames the API suffix or switches to arrow-function
    // handlers would silently empty this list and make every assertion vacuous.
    expect(handlers.length).toBeGreaterThan(25)
    expect(handlers.map((h) => h.key)).toContain('views/system/User/User.vue::toggleStatus')
  })

  it.each(handlers.filter((h) => !ALLOWLIST.has(h.key)).map((h) => [h.key, h.body]))(
    '%s checks the response code and has a failure path',
    (key, body) => {
      const hasEarlyReturn = earlyReturnCheck.test(body)
      const hasElseBranch = zeroCheck.test(body) && /\belse\b/.test(body)
      expect(
        hasEarlyReturn || hasElseBranch,
        `${key} calls a write API but never branches on a non-zero res.data.code. ` +
          'request.js only rejects 401/700, so 404/701/702/703 resolve and the ' +
          'handler reports success (or silence) for a write the server refused.'
      ).toBe(true)
    }
  )

  it('every allowlist entry still names a real handler', () => {
    const keys = new Set(handlers.map((h) => h.key))
    for (const key of ALLOWLIST.keys()) expect(keys).toContain(key)
  })
})
