<script setup>
import { ref, computed } from 'vue'
import TreePicker from './TreePicker.vue'
import { getMenuIndexApi } from '../../../services/menu'

defineOptions({ name: 'MenuPicker' })

/**
 * メニューツリーをツリー dropdown 形式で選択する共通ピッカー。
 *
 * - データ源は /admin/menu/list（フラット配列）→ parentId / sortOrder で組み立て直す
 * - exclude: 自己＋子孫を渡せば「親選択の循環防止」用途で使える（配下サブツリーごと隠す）
 * - excludeTypes: 例 [3] でボタン (menuType=3) を親候補から外す（ツリーから削除）
 */
const props = defineProps({
  modelValue: { type: [String, Number], default: '' },
  exclude: { type: Array, default: () => [] },
  excludeTypes: { type: Array, default: () => [] },
  placeholder: { type: String, default: '' },
  disabled: { type: Boolean, default: false },
  error: { type: Boolean, default: false },
  clearable: { type: Boolean, default: true }
})

const emit = defineEmits(['update:modelValue', 'change'])

const flatList = ref([])

const tree = computed(() => {
  // 1. excludeTypes でフラットレベルで先に除外
  const excludeTypeSet = new Set(props.excludeTypes || [])
  const filtered = flatList.value.filter((m) => !excludeTypeSet.has(m.menuType))

  // 2. parentId でツリー化、各レベル sortOrder 順
  const byParent = new Map()
  for (const m of filtered) {
    const k = m.parentId || ''
    if (!byParent.has(k)) byParent.set(k, [])
    byParent.get(k).push(m)
  }
  function build(parentKey) {
    return (byParent.get(parentKey) || [])
      .slice()
      .sort((a, b) => (a.sortOrder ?? 0) - (b.sortOrder ?? 0))
      .map((m) => ({
        id: m.id,
        label: `${m.title}${m.code ? ` (${m.code})` : ''}`,
        children: build(m.id)
      }))
  }
  return build('')
})

async function load() {
  try {
    const res = await getMenuIndexApi()
    if (res.data.code === 0) flatList.value = res.data.data || []
  } catch (e) {
    console.error('メニュー一覧取得失敗:', e)
  }
}

load()
</script>

<template>
  <TreePicker
    :model-value="modelValue"
    :tree="tree"
    :exclude-ids="exclude"
    :placeholder="placeholder"
    :disabled="disabled"
    :error="error"
    :clearable="clearable"
    @update:model-value="(v) => emit('update:modelValue', v)"
    @change="(n) => emit('change', n)"
  />
</template>
