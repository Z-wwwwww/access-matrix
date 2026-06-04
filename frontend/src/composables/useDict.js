import { computed } from 'vue'
import { useI18n } from 'vue-i18n'
import { useDictStore } from '@/stores/dict'

/** Pick a label from an i18n map for the current locale, with ja_JP fallback. */
function pickI18n(map, loc) {
  if (!map) return ''
  return map[loc] ?? map.ja_JP ?? Object.values(map)[0] ?? ''
}

/**
 * Read a dictionary (built-in enum OR managed table — same contract) and expose
 * it in the shapes a page needs. Source-agnostic; labels resolve reactively to
 * the current locale (built-in via `t(labelKey)`, managed via labelI18n).
 *
 *   const status = useDict('task_status')
 *   <Select :options="status.options" v-model="form.status" />
 *   <Badge :variant="status.cssClass(row.status) || 'outline'">{{ status.label(row.status) }}</Badge>
 *
 * Returns:
 *   items     computed [{ value, label, cssClass, enabled }]  (all, incl. disabled)
 *   options   computed [{ label, value }]                     (enabled only — for <Select>)
 *   label(v)  resolves a value's label (disabled items too; unknown → raw value)
 *   cssClass(v) Badge variant for a value, or undefined
 *   loading   ref
 */
export function useDict(code) {
  const store = useDictStore()
  const { t, locale } = useI18n()
  const entry = store.entryOf(code)
  store.ensure(code) // fire-and-forget; reactive entry updates when it resolves

  const items = computed(() => (entry.items || []).map((it) => ({
    value: it.value,
    label: it.labelKey ? t(it.labelKey) : pickI18n(it.labelI18n, locale.value),
    cssClass: it.cssClass || undefined,
    enabled: it.enabled !== false,
  })))

  const options = computed(() => items.value
    .filter((i) => i.enabled)
    .map((i) => ({ label: i.label, value: i.value })))

  // keyed by String(value) so numeric (built-in) and string (managed) both match
  const byValue = computed(() => {
    const m = new Map()
    for (const i of items.value) m.set(String(i.value), i)
    return m
  })

  function label(v) {
    if (v === null || v === undefined || v === '') return ''
    return byValue.value.get(String(v))?.label ?? String(v)
  }

  function cssClass(v) {
    return byValue.value.get(String(v))?.cssClass
  }

  return { items, options, label, cssClass, loading: computed(() => entry.loading) }
}

/** Convenience for several codes at once: returns an object keyed by code. */
export function useDicts(codes) {
  const out = {}
  for (const c of codes) out[c] = useDict(c)
  return out
}

/**
 * Resolve a single dict label OUTSIDE a component (stores / utils / interceptors),
 * where `useDict` (needs setup context) can't be called. Async — ensures the dict
 * is loaded first. Falls back to the raw value when unknown.
 *
 *   import { resolveDictLabel } from '@/composables/useDict'
 *   const txt = await resolveDictLabel('task_status', 3)   // → "完了" / "Done" ...
 */
export async function resolveDictLabel(code, value) {
  const { default: i18n } = await import('@/lang')
  const { useDictStore } = await import('@/stores/dict')
  const entry = await useDictStore().ensure(code)
  const it = (entry.items || []).find((i) => String(i.value) === String(value))
  if (!it) return value === null || value === undefined ? '' : String(value)
  return it.labelKey ? i18n.global.t(it.labelKey) : pickI18n(it.labelI18n, i18n.global.locale.value)
}
