<script setup>
import { ref, computed } from 'vue'
import { Building, Building2 } from 'lucide-vue-next'
import TreePicker from './TreePicker.vue'
import { getDeptTreeApi } from '../../../services/dept'

defineOptions({ name: 'DeptPicker' })

/**
 * 部署ツリーをツリー形式の dropdown で選択させる共通ピッカー。
 *
 * - データ源は /dept/tree
 * - 親部署選択など、自己と子孫を選ばせたくないケースは `exclude` で除外可能
 *   （exclude されたノードは配下のサブツリーごと TreePicker から消える）
 * - 単一選択のみ
 * - `rich=true` で連結線・アイコン・パンくず表示を opt-in（ユーザー編集など、
 *   業務担当者が部署を直感的に選びたい画面で有効化）
 */
const props = defineProps({
  modelValue: { type: [String, Number], default: '' },
  /** 隠す部署 ID 一覧（その配下も自動的に消える） */
  exclude: { type: Array, default: () => [] },
  placeholder: { type: String, default: '' },
  disabled: { type: Boolean, default: false },
  error: { type: Boolean, default: false },
  clearable: { type: Boolean, default: true },
  /** 連結線・アイコン・trigger のパンくず表示をまとめて on にする */
  rich: { type: Boolean, default: false }
})

const emit = defineEmits(['update:modelValue', 'change'])

const rawTree = ref([])

const tree = computed(() => transform(rawTree.value))

function transform(nodes) {
  return (nodes || []).map((n) => ({
    id: n.id,
    // breadcrumb 用に純粋な部署名
    name: n.name,
    // ツリー行は code も併記して検索性を確保
    label: `${n.name}${n.code ? ` (${n.code})` : ''}`,
    children: transform(n.children)
  }))
}

async function load() {
  try {
    const res = await getDeptTreeApi()
    if (res.data.code === 0) rawTree.value = res.data.data || []
  } catch (e) {
    console.error('部署ツリー取得失敗:', e)
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
    :show-connectors="rich"
    :show-breadcrumb="rich"
    @update:model-value="(v) => emit('update:modelValue', v)"
    @change="(n) => emit('change', n)"
  >
    <!-- rich モードのときだけアイコンを描画。
         子を持つノード = 上位組織として Building2（複数フロア風）、
         葉ノード = 拠点として Building（単独建物）。 -->
    <template v-if="rich" #node-icon="{ isLeaf }">
      <component
        :is="isLeaf ? Building : Building2"
        :size="14"
        class="shrink-0 text-muted-foreground"
      />
    </template>
  </TreePicker>
</template>
