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

  function entryOf(code) {
    if (!cache[code]) {
      cache[code] = reactive({ builtin: false, items: [], loaded: false, loading: false })
    }
    return cache[code]
  }

  async function ensure(code) {
    const e = entryOf(code)
    if (e.loaded || e.loading) return e
    e.loading = true
    try {
      const res = await getDictApi(code)
      if (res.data.code === 0 && res.data.data) {
        e.builtin = !!res.data.data.builtin
        e.items = res.data.data.items || []
        e.loaded = true
      }
    } catch (err) {
      // leave empty — useDict.label() falls back to the raw value, never throws
    } finally {
      e.loading = false
    }
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
    return ensure(code)
  }

  return { cache, entryOf, ensure, invalidate }
})
