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

  /** Force a refetch next access (call after admin edits a managed dict). */
  function invalidate(code) {
    if (cache[code]) cache[code].loaded = false
  }

  return { cache, entryOf, ensure, invalidate }
})
