import { describe, it, expect, vi, beforeEach } from 'vitest'
import { createPinia, setActivePinia } from 'pinia'

/**
 * `ensure()` returns a promise; awaiting it must mean the dict is actually loaded.
 *
 * It used to bail out with `if (e.loaded || e.loading) return e`, which resolves
 * immediately — with `items` still `[]` — whenever a fetch for that code is
 * already in flight. `useDict` never noticed: it reads the reactive entry and
 * re-renders when the entry fills in. `invalidate()` did.
 *
 * `invalidate` sets `loaded = false` and calls `ensure` to pull fresh data. With a
 * fetch in flight, `ensure` returned without issuing one — and that in-flight
 * response, issued BEFORE the write that triggered the invalidation, then landed
 * and set `loaded = true`. The store kept pre-write data and marked it fresh,
 * which is precisely the failure `invalidate` exists to prevent: `Dict.vue` calls
 * it after saving or deleting an item so already-open tabs stop offering a
 * retired option.
 *
 * Both tests force the interleaving rather than racing for it. The first leans on
 * microtasks-before-macrotasks: the buggy version resolves on a microtask, so it
 * observes the entry before the `setTimeout` hands over the response.
 */

let deferred
const getDictApi = vi.fn(() => deferred.promise)
vi.mock('@/services/dict', () => ({ getDictApi: (...a) => getDictApi(...a) }))

import { useDictStore } from '@/stores/dict'

function defer() {
  let resolve
  const promise = new Promise((res) => { resolve = res })
  return { promise, resolve }
}

const items = (...labels) => labels.map((label, i) => ({
  value: i + 1, labelI18n: { ja_JP: label }, cssClass: null, sortNo: i, enabled: true
}))

const ok = (its) => ({ data: { code: 0, data: { builtin: false, items: its } } })

describe('dict store — ensure() must await an in-flight load', () => {
  beforeEach(() => {
    setActivePinia(createPinia())
    getDictApi.mockClear()
    deferred = defer()
  })

  it('a second ensure() resolves only once the in-flight fetch has landed', async () => {
    const store = useDictStore()

    store.ensure('task_status')                 // request #1, in flight
    const second = store.ensure('task_status')  // arrives while #1 is pending

    // Macrotask: any promise that settles on the microtask queue — i.e. the old
    // early return — is guaranteed to observe the entry before this runs.
    setTimeout(() => deferred.resolve(ok(items('未着手', '進行中', '完了'))), 0)

    const entry = await second
    expect(entry.loaded).toBe(true)
    expect(entry.items).toHaveLength(3)
    expect(getDictApi).toHaveBeenCalledTimes(1)   // still de-duplicated
  })

  it('invalidate during an in-flight load still refetches', async () => {
    const store = useDictStore()

    store.ensure('task_status')                       // request #1, in flight
    const refreshed = store.invalidate('task_status')

    // #1 lands carrying the PRE-write options.
    const second = defer()
    getDictApi.mockImplementationOnce(() => second.promise)
    deferred.resolve(ok(items('未着手', '進行中', '完了')))
    await Promise.resolve()
    await Promise.resolve()
    await Promise.resolve()

    // The invalidation must queue a fresh request rather than adopt #1's answer.
    expect(getDictApi).toHaveBeenCalledTimes(2)
    second.resolve(ok(items('未着手', '進行中')))      // "完了" retired by the write

    const entry = await refreshed
    expect(entry.loaded).toBe(true)
    expect(entry.items.map((i) => i.labelI18n.ja_JP)).toEqual(['未着手', '進行中'])
  })
})
