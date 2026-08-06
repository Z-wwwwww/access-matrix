import { defineStore } from 'pinia'
import { reactive } from 'vue'
import { getDictApi } from '@/services/dict'

/**
 * Per-session cache for dictionary reads (GET /dict/{code}). Each code is fetched
 * at most once; components consume via the `useDict` composable. Built-in and
 * managed dicts share the same shape, so the store is source-agnostic.
 */
export const useDictStore = defineStore('dict', () => {
  // code -> { builtin, items, loaded, loading }
  const cache = reactive({})

  /**
   * code -> in-flight fetch. Deliberately a plain Map, outside `cache`: it holds
   * promises, which have no business being reactive, and nothing renders from it.
   */
  const inflight = new Map()

  function entryOf(code) {
    if (!cache[code]) {
      cache[code] = reactive({ builtin: false, items: [], loaded: false, loading: false })
    }
    return cache[code]
  }

  function fetchInto(e, code) {
    const p = (async () => {
      try {
        const res = await getDictApi(code)
        if (res.data.code === 0 && res.data.data) {
          e.builtin = !!res.data.data.builtin
          e.items = res.data.data.items || []
          e.loaded = true
        }
      } catch (err) {
        // leave as-is — useDict.label() falls back to the raw value, never throws,
        // and `loaded` stays false so the next ensure() retries (no negative cache)
      } finally {
        e.loading = false
        if (inflight.get(code) === p) inflight.delete(code)
      }
    })()
    inflight.set(code, p)
    e.loading = true
    return p
  }

  /**
   * Load `code` if needed and resolve once it really is loaded.
   *
   * <p>Awaiting a concurrent load rather than returning early is the contract, not
   * an optimisation. This used to be `if (e.loaded || e.loading) return e`, which
   * resolves immediately with `items` still empty whenever a fetch is already in
   * flight. `useDict` tolerated that — it reads the reactive entry and re-renders
   * later — but the imperative callers did not. {@link invalidate} calls `ensure`
   * to pull post-write data and got no fetch at all, so the in-flight response
   * (issued BEFORE the write) landed and marked the stale copy `loaded`; the
   * retired option stayed in every open dropdown for the rest of the session,
   * which is the exact failure `invalidate` exists to prevent. `resolveDictLabel`
   * likewise fell through to the raw value.
   */
  async function ensure(code) {
    const e = entryOf(code)
    if (e.loaded) return e
    const pending = inflight.get(code)
    if (pending) {
      await pending
      return e
    }
    await fetchInto(e, code)
    return e
  }

  /**
   * Drop the cached copy of `code` AND pull a fresh one right away.
   *
   * <p>The refetch is the point, not a nicety. `useDict` calls `ensure` exactly
   * once — in the composable body, i.e. during setup — so merely flipping
   * `loaded` back to false refreshes nothing that is already on screen: the
   * consumer keeps reading the same `entry.items` array, and its setup never
   * runs again because every business page lives behind keep-alive (tabs). The
   * one caller, `Dict.vue`, invokes this right after saving/deleting an item
   * with the comment "让正在使用该字典的下拉刷新" — which is precisely what did
   * not happen: an already-open Task tab went on offering the retired option for
   * the rest of the session. (The server already refuses such a value —
   * `TaskService.validateDictValue` → `isSelectableValue` — so the user hit a
   * validation error on a choice the UI was still showing them.)
   *
   * <p>`ensure` mutates the shared reactive entry in place, so a single refetch
   * updates every live consumer of this code at once.
   *
   * @returns the in-flight refresh, so callers may await it
   */
  function invalidate(code) {
    const e = cache[code]
    if (!e) return Promise.resolve(undefined)
    e.loaded = false
    // A fetch already in flight was issued BEFORE the write that triggered this
    // invalidation, so its answer is stale by construction. Chain a fresh request
    // after it instead of adopting it — `ensure` alone would do the latter.
    const stale = inflight.get(code)
    if (!stale) return ensure(code)
    return stale.then(() => {
      e.loaded = false
      return ensure(code)
    })
  }

  return { cache, entryOf, ensure, invalidate }
})
