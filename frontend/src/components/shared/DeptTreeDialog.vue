<script setup>
import { ref, computed, watch } from 'vue'
import { useI18n } from 'vue-i18n'
import Dialog from '@/components/ui/Dialog.vue'
import { Search, ChevronDown, X, Building2 } from 'lucide-vue-next'
import OrgNode from './OrgNode.vue'
import { getDeptTreeApi } from '../../../services/dept'

defineOptions({ name: 'DeptTreeDialog' })

const { t } = useI18n()

/**
 * 部署選択用のモーダル型ピッカー（縦向き org-chart 表示）。
 *
 * - trigger は breadcrumb で現在値を表示
 * - クリックすると Dialog が開き、組織図形式（根が上、子が下）でツリーを表示
 * - 連結線は CSS pseudo-element で 1px 線として描画（OrgNode 側）
 * - 検索: マッチしたノード + その祖先のみ表示（チャートが自動的にスリムになる）
 * - 単クリックで決定 + 閉じる
 */
const props = defineProps({
  modelValue: { type: [String, Number], default: '' },
  /** 隠す部署 ID 一覧（その配下も自動的に消える） */
  exclude: { type: Array, default: () => [] },
  placeholder: { type: String, default: '' },
  disabled: { type: Boolean, default: false },
  error: { type: Boolean, default: false },
  clearable: { type: Boolean, default: true }
})

const emit = defineEmits(['update:modelValue', 'change'])

const rawTree = ref([])
const open = ref(false)
const searchKeyword = ref('')
/** 複数会社（複数ルート）時、左サイドの会社タブの選択 ID */
const activeRootId = ref(null)

const tree = computed(() => transform(rawTree.value))

function transform(nodes) {
  return (nodes || []).map((n) => ({
    id: n.id,
    name: n.name,
    code: n.code,
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

const excludeSet = computed(() => new Set((props.exclude || []).map(String)))

const filteredTree = computed(() => filterExcluded(tree.value, excludeSet.value))

function filterExcluded(nodes, excl) {
  if (!excl.size) return nodes
  const out = []
  for (const n of nodes) {
    if (excl.has(String(n.id))) continue
    out.push({ ...n, children: filterExcluded(n.children, excl) })
  }
  return out
}

/** 検索後のツリー（matched + 祖先のみ残る） */
const visibleTree = computed(() => {
  const q = searchKeyword.value.trim().toLowerCase()
  if (!q) return filteredTree.value
  const matched = collectMatchesAndAncestors(filteredTree.value, q)
  return pruneTree(filteredTree.value, matched)
})

function pruneTree(nodes, includedIds) {
  const out = []
  for (const n of nodes) {
    if (!includedIds.has(n.id)) continue
    out.push({
      ...n,
      children: pruneTree(n.children || [], includedIds)
    })
  }
  return out
}

function collectMatchesAndAncestors(nodes, q) {
  const ids = new Set()
  function walk(nodes, ancestorStack) {
    for (const n of nodes) {
      const matches = String(n.label).toLowerCase().includes(q)
      if (matches) {
        ids.add(n.id)
        for (const a of ancestorStack) ids.add(a)
      }
      if (n.children?.length) walk(n.children, [...ancestorStack, n.id])
    }
  }
  walk(nodes, [])
  return ids
}

/** id → ノード参照 */
const nodeMap = computed(() => {
  const m = new Map()
  function walk(nodes) {
    for (const n of nodes) {
      m.set(String(n.id), n)
      if (n.children?.length) walk(n.children)
    }
  }
  walk(filteredTree.value)
  return m
})

/** id → parentId */
const parentMap = computed(() => {
  const m = new Map()
  function walk(nodes, parentId) {
    for (const n of nodes) {
      m.set(String(n.id), parentId)
      if (n.children?.length) walk(n.children, String(n.id))
    }
  }
  walk(filteredTree.value, null)
  return m
})

const hasValue = computed(() => props.modelValue !== '' && props.modelValue != null)

const selectedBreadcrumb = computed(() => {
  if (!hasValue.value) return []
  const parts = []
  let cur = String(props.modelValue)
  for (let safety = 0; safety < 100 && cur; safety++) {
    const node = nodeMap.value.get(cur)
    if (!node) break
    parts.unshift(node.name)
    const p = parentMap.value.get(cur)
    cur = p ? String(p) : null
  }
  return parts
})

function openDialog() {
  if (props.disabled) return
  searchKeyword.value = ''
  // 現在値がある場合は、その値を含むルート（会社）を初期アクティブタブにする
  let initial = null
  if (hasValue.value) {
    let cur = String(props.modelValue)
    let last = cur
    for (let s = 0; s < 100 && cur; s++) {
      last = cur
      const p = parentMap.value.get(cur)
      cur = p ? String(p) : null
    }
    initial = nodeMap.value.get(last)?.id ?? null
  }
  if (initial == null && filteredTree.value.length) initial = filteredTree.value[0].id
  activeRootId.value = initial
  open.value = true
}

/** 検索などで visibleTree が変わったとき、active タブを補正 */
watch(visibleTree, (roots) => {
  if (!roots.length) return
  if (!roots.find((r) => r.id === activeRootId.value)) {
    activeRootId.value = roots[0].id
  }
})

/** 右ペインに表示する現在のルート（= 選択中の会社） */
const activeRoot = computed(
  () => visibleTree.value.find((r) => r.id === activeRootId.value) || visibleTree.value[0] || null
)

function selectNode(node) {
  emit('update:modelValue', node.id)
  emit('change', node)
  open.value = false
}

function clearValue(e) {
  e?.stopPropagation()
  emit('update:modelValue', '')
  emit('change', null)
}
</script>

<template>
  <!-- Trigger -->
  <div class="group relative inline-flex w-full">
    <button
      type="button"
      :disabled="disabled"
      :class="[
        'flex items-center justify-between w-full h-9 px-3 pr-8 border border-input rounded-lg bg-card text-sm transition cursor-pointer text-left',
        'focus:outline-none focus:ring-2 focus:ring-ring',
        'hover:border-primary/50',
        'disabled:bg-muted/60 disabled:text-muted-foreground disabled:cursor-not-allowed disabled:border-border/60',
        error ? 'border-destructive focus:ring-destructive/30' : ''
      ]"
      @click="openDialog"
    >
      <span v-if="hasValue && selectedBreadcrumb.length" class="text-foreground truncate">
        <template v-for="(part, idx) in selectedBreadcrumb" :key="idx">
          <span v-if="idx > 0" class="text-muted-foreground/70 mx-1">/</span>
          <span :class="idx === selectedBreadcrumb.length - 1 ? 'font-medium' : 'text-muted-foreground'">{{ part }}</span>
        </template>
      </span>
      <span v-else class="text-muted-foreground truncate">{{ placeholder || t('common.placeholder.pleaseSelect') }}</span>
      <ChevronDown
        v-if="!clearable || !hasValue"
        :size="14"
        class="absolute right-2.5 top-1/2 -translate-y-1/2 text-muted-foreground"
      />
    </button>
    <template v-if="clearable && hasValue && !disabled">
      <span
        class="absolute right-2 top-1/2 -translate-y-1/2 opacity-0 group-hover:opacity-100 transition-opacity cursor-pointer p-0.5 rounded-full hover:bg-muted z-10"
        @click.stop="clearValue($event)"
      >
        <X :size="14" class="text-muted-foreground" />
      </span>
      <ChevronDown
        :size="14"
        class="absolute right-2.5 top-1/2 -translate-y-1/2 text-muted-foreground group-hover:opacity-0 pointer-events-none"
      />
    </template>

    <!-- Modal: 縦向き org-chart -->
    <Dialog
      v-model:open="open"
      :title="t('common.placeholder.pleaseSelect')"
      width="max-w-3xl"
      z-index="z-[100]"
    >
      <div class="space-y-3">
        <!-- 検索 -->
        <div class="relative">
          <Search :size="14" class="absolute left-2.5 top-1/2 -translate-y-1/2 text-muted-foreground" />
          <input
            v-model="searchKeyword"
            type="text"
            :placeholder="t('common.placeholder.search')"
            class="w-full h-9 pl-8 pr-2 rounded-md border border-input bg-card text-sm focus:outline-none focus:ring-2 focus:ring-ring"
          />
        </div>

        <!-- 組織図ビューポート: 複数会社時は左に縦タブ、右に対象会社の org-chart -->
        <div class="flex border border-border rounded-lg bg-muted/20 overflow-hidden">
          <!-- 左: 会社タブ（ルートが 2 件以上のときのみ表示） -->
          <div
            v-if="filteredTree.length > 1"
            class="flex-shrink-0 w-44 border-r border-border bg-card/60 overflow-y-auto max-h-[60vh]"
          >
            <button
              v-for="root in visibleTree"
              :key="root.id"
              type="button"
              :class="[
                'w-full text-left px-3 py-2 text-sm border-l-2 transition flex items-center gap-2',
                root.id === activeRootId
                  ? 'border-primary bg-primary/10 text-primary font-medium'
                  : 'border-transparent hover:bg-muted text-foreground'
              ]"
              @click="activeRootId = root.id"
            >
              <Building2 :size="14" class="shrink-0" />
              <span class="truncate">{{ root.name }}</span>
            </button>
            <div v-if="!visibleTree.length" class="text-xs text-muted-foreground p-3">該当なし</div>
          </div>
          <!-- 右: 対象会社の組織図 -->
          <div class="flex-1 overflow-auto max-h-[60vh]">
            <div class="min-w-full inline-flex justify-center py-6 px-4">
              <div v-if="activeRoot" class="inline-flex gap-4 items-start">
                <OrgNode
                  :node="activeRoot"
                  :model-value="modelValue"
                  @select="selectNode"
                />
              </div>
              <div v-else class="text-sm text-muted-foreground py-8">該当なし</div>
            </div>
          </div>
        </div>
      </div>
    </Dialog>
  </div>
</template>
