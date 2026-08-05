import { describe, it, expect, vi, beforeEach } from 'vitest'
import { defineComponent, h } from 'vue'
import { mount } from '@vue/test-utils'
import { createPinia, setActivePinia } from 'pinia'

let payload
const getDictApi = vi.fn(async () => ({ data: { code: 0, data: payload } }))
vi.mock('@/services/dict', () => ({ getDictApi: (...a) => getDictApi(...a) }))

vi.mock('vue-i18n', () => ({
  useI18n: () => ({ t: (k) => k, locale: { value: 'ja_JP' } })
}))

import { useDictStore } from '@/stores/dict'
import { useDict } from '@/composables/useDict'

const flush = async () => {
  await Promise.resolve()
  await Promise.resolve()
  await Promise.resolve()
}

const item = (value, label, enabled = true) => ({
  value, labelI18n: { ja_JP: label }, cssClass: null, sortNo: 0, enabled
})

/**
 * `useDict` calls `store.ensure(code)` once, in the composable body — i.e. during
 * setup. Every business page sits behind keep-alive (the tab bar), so setup does
 * not run again when the user comes back to an already-open tab. That makes the
 * store the ONLY thing that can refresh a dict for a screen that is already
 * mounted, and `invalidate` the only lever: it used to just set `loaded = false`
 * and return, which refreshes nothing at all. `Dict.vue` calls it right after
 * saving or deleting an item with the comment "让正在使用该字典的下拉刷新" — so an
 * already-open Task tab kept offering a retired option for the rest of the
 * session, and picking it produced a server-side `error.dict.invalidValue`
 * (TaskService validates against `isSelectableValue`) on a choice the UI itself
 * had just shown.
 */
describe('dict store — invalidate refreshes live consumers', () => {
  let seen

  const Host = defineComponent({
    setup() {
      seen = useDict('task_priority')
      return () => h('div')
    }
  })

  beforeEach(() => {
    setActivePinia(createPinia())
    getDictApi.mockClear()
    payload = { builtin: false, items: [item(1, '低'), item(2, '中'), item(3, '高')] }
  })

  it('loads the dict once on first use', async () => {
    mount(Host)
    await flush()

    expect(getDictApi).toHaveBeenCalledTimes(1)
    expect(seen.options.value.map((o) => o.value)).toEqual([1, 2, 3])
  })

  it('a retired option disappears from an ALREADY-MOUNTED consumer', async () => {
    mount(Host)
    await flush()
    expect(seen.options.value.map((o) => o.value)).toEqual([1, 2, 3])

    // Ops retires "高" (status=0 → enabled:false) on the Dict console.
    payload = { builtin: false, items: [item(1, '低'), item(2, '中'), item(3, '高', false)] }
    await useDictStore().invalidate('task_priority')
    await flush()

    expect(getDictApi).toHaveBeenCalledTimes(2)
    // The retired option must be gone from what the user can still CHOOSE.
    expect(seen.options.value.map((o) => o.value)).toEqual([1, 2])
    // ...but it must still RESOLVE a label, so historical rows render.
    expect(seen.label(3)).toBe('高')
  })

  it('a newly added option appears in an ALREADY-MOUNTED consumer', async () => {
    mount(Host)
    await flush()

    payload = { builtin: false, items: [item(1, '低'), item(2, '中'), item(3, '高'), item(4, '緊急')] }
    await useDictStore().invalidate('task_priority')
    await flush()

    expect(seen.options.value.map((o) => o.value)).toEqual([1, 2, 3, 4])
    expect(seen.label(4)).toBe('緊急')
  })

  it('invalidating a code nobody loaded is a no-op, not a fetch', async () => {
    await useDictStore().invalidate('never_used')
    expect(getDictApi).not.toHaveBeenCalled()
  })

  it('a failed refetch keeps the previous options rather than blanking the dropdown', async () => {
    mount(Host)
    await flush()

    getDictApi.mockRejectedValueOnce(new Error('network down'))
    await useDictStore().invalidate('task_priority')
    await flush()

    expect(seen.options.value.map((o) => o.value)).toEqual([1, 2, 3])
  })
})
