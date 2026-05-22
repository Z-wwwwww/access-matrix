<script setup>
import { ref, computed, nextTick, onBeforeUnmount, inject, watch } from 'vue'
import { useI18n } from 'vue-i18n'
import { ChevronDown, ChevronRight, Check, X, Search } from 'lucide-vue-next'
import { usePopupFollowTrigger, applyAbsolutePopupPosition } from '@/composables/usePopupFollowTrigger'

defineOptions({ name: 'TreePicker' })

const { t } = useI18n()

/**
 * 階層データを開閉できるツリー風ドロップダウンで選択するための汎用ピッカー。
 *
 * 入力データ:
 *   tree = [{ id, label, name?, children: [...] }, ...]
 *   - label : ツリー行に表示する完全な文字列（例: "京都支社 (KYOTO)"）
 *   - name  : breadcrumb 用の短い名前（省略時は label から ` (...)` を剥がす）
 *
 * オプション機能（呼び出し側が opt-in）:
 *   - show-connectors: 行頭に ├─ / └─ / │  の box-drawing でリネージを描画
 *   - show-breadcrumb: trigger に「親 / 子 / 孫」のパンくず表記で選択値を表示
 *   - スロット node-icon: 各行のラベル左にアイコン挿入（{ node, isLeaf } を受ける）
 */
const props = defineProps({
  modelValue: { type: [String, Number], default: '' },
  /** [{ id, label, name?, children: [...] }, ...] */
  tree: { type: Array, default: () => [] },
  /** 隠す ID 一覧。ノードが消えるとその配下も自動的に消える（親選択の循環防止に便利） */
  excludeIds: { type: Array, default: () => [] },
  /** 表示はするが選択不可にする ID 一覧 */
  disabledIds: { type: Array, default: () => [] },
  placeholder: { type: String, default: '' },
  disabled: { type: Boolean, default: false },
  error: { type: Boolean, default: false },
  clearable: { type: Boolean, default: true },
  /** ├─ / └─ の連結線でリネージを描画する */
  showConnectors: { type: Boolean, default: false },
  /** trigger に breadcrumb 形式 (本社 / 東京支社 / 京都支社) で選択値を表示 */
  showBreadcrumb: { type: Boolean, default: false }
})

const emit = defineEmits(['update:modelValue', 'change'])

const open = ref(false)
const triggerRef = ref(null)
const panelRef = ref(null)
const searchInputRef = ref(null)
const searchKeyword = ref('')
const expanded = ref(new Set())

const excludeSet = computed(() => new Set((props.excludeIds || []).map(String)))
const disabledSet = computed(() => new Set((props.disabledIds || []).map(String)))

/** exclude を適用したサブツリー（exclude されたノードはサブツリーごと消える）。 */
const filteredTree = computed(() => filterTree(props.tree, excludeSet.value))

function filterTree(nodes, excl) {
  if (!excl.size) return nodes || []
  const out = []
  for (const n of nodes || []) {
    if (excl.has(String(n.id))) continue
    out.push({ ...n, children: filterTree(n.children, excl) })
  }
  return out
}

/** id → ノード参照（trigger 表示・breadcrumb 構築用） */
const nodeMap = computed(() => {
  const m = new Map()
  function walk(nodes) {
    for (const n of nodes || []) {
      m.set(String(n.id), n)
      if (n.children?.length) walk(n.children)
    }
  }
  walk(filteredTree.value)
  return m
})

/** id → parentId（breadcrumb 構築用に祖先を辿る） */
const parentMap = computed(() => {
  const m = new Map()
  function walk(nodes, parentId) {
    for (const n of nodes || []) {
      m.set(String(n.id), parentId)
      if (n.children?.length) walk(n.children, String(n.id))
    }
  }
  walk(filteredTree.value, null)
  return m
})

const hasValue = computed(() => props.modelValue !== '' && props.modelValue != null)

function shortName(node) {
  if (!node) return ''
  if (node.name) return node.name
  // label に " (...)" が付いていれば剥がす
  const idx = String(node.label || '').indexOf(' (')
  return idx > 0 ? node.label.substring(0, idx) : (node.label || '')
}

const selectedLabel = computed(() => {
  if (!hasValue.value) return ''
  const n = nodeMap.value.get(String(props.modelValue))
  return n ? n.label : ''
})

/** ルート → 選択ノード までの短い名前リスト */
const selectedBreadcrumb = computed(() => {
  if (!hasValue.value) return []
  const parts = []
  let id = String(props.modelValue)
  // 無限ループ防止
  for (let safety = 0; safety < 100 && id; safety++) {
    const node = nodeMap.value.get(id)
    if (!node) break
    parts.unshift(shortName(node))
    const parentId = parentMap.value.get(id)
    id = parentId ? String(parentId) : null
  }
  return parts
})

/** 表示候補ノード（級別 level + 連結線用の祖先 stems / isLastSibling 付き） */
const visibleNodes = computed(() => {
  const q = searchKeyword.value.trim().toLowerCase()
  if (!q) return flattenWith(filteredTree.value, null, false)
  const matched = collectMatchesWithAncestors(filteredTree.value, q)
  return flattenWith(filteredTree.value, matched, true)
})

/**
 * 1 度の DFS で flatten + 各行の連結線情報も計算する。
 * @param nodes 走査対象
 * @param includedIds 検索時の matched + 祖先集合（無い場合は expanded ベース）
 * @param isSearchMode true なら includedIds でフィルタ、false なら expanded で展開判定
 */
function flattenWith(nodes, includedIds, isSearchMode, level = 0, pathStems = [], out = []) {
  // 実際にこのレベルで描画する兄弟集合
  const siblings = (nodes || []).filter((n) => {
    if (isSearchMode) return includedIds.has(n.id)
    return true
  })
  for (let i = 0; i < siblings.length; i++) {
    const n = siblings[i]
    const isLast = i === siblings.length - 1
    const isLeaf = !n.children?.length
    out.push({
      ...n,
      level,
      isLeaf,
      pathStems,
      isLastSibling: isLast
    })
    const shouldRecurse = isSearchMode || expanded.value.has(n.id)
    if (shouldRecurse && !isLeaf) {
      flattenWith(n.children, includedIds, isSearchMode, level + 1, [...pathStems, !isLast], out)
    }
  }
  return out
}

function collectMatchesWithAncestors(nodes, q) {
  const ids = new Set()
  function walk(nodes, ancestorStack) {
    for (const n of nodes || []) {
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

/**
 * 行頭の連結線プレフィックス。
 * 例: level=2, pathStems=[false,true], isLastSibling=false → "│ ├─"
 * level 0 は空文字を返す（root に連結線は不要）。
 */
function buildPrefix(node) {
  if (!props.showConnectors || node.level === 0) return ''
  let s = ''
  // pathStems[0] は root レベルの「兄弟が残っているか」だが、root 自体には prefix を描かないので
  // 描画では pathStems[1..L-1] のみ使う。
  for (let i = 1; i < node.level; i++) {
    s += node.pathStems[i] ? '│ ' : '  '
  }
  s += node.isLastSibling ? '└─' : '├─'
  return s
}

function isExpanded(id) { return expanded.value.has(id) }

function toggleExpand(id) {
  if (expanded.value.has(id)) expanded.value.delete(id)
  else expanded.value.add(id)
}

function expandAll() {
  const s = new Set()
  function walk(nodes) {
    for (const n of nodes || []) {
      if (n.children?.length) {
        s.add(n.id)
        walk(n.children)
      }
    }
  }
  walk(filteredTree.value)
  expanded.value = s
}

function selectNode(n) {
  if (disabledSet.value.has(String(n.id))) return
  emit('update:modelValue', n.id)
  emit('change', n)
  open.value = false
  searchKeyword.value = ''
}

function clearValue(e) {
  e?.stopPropagation()
  emit('update:modelValue', '')
  emit('change', null)
  open.value = false
}

// Drawer 内に置かれた時 用に高い z-index を貰う
const injectedPopupZ = inject('popupZIndex', null)

function updatePosition() {
  if (!triggerRef.value || !panelRef.value) return
  const rect = triggerRef.value.getBoundingClientRect()
  applyAbsolutePopupPosition(panelRef.value, rect, {
    extraStyle: { minWidth: rect.width + 'px', maxWidth: '380px', width: 'max-content' },
    zIndex: injectedPopupZ?.value,
    triggerEl: triggerRef.value,
    assumedHeight: 360
  })
}

async function toggleOpen() {
  if (props.disabled) return
  open.value = !open.value
  if (open.value) {
    expandAll()
    searchKeyword.value = ''
    await nextTick()
    updatePosition()
    if (searchInputRef.value) searchInputRef.value.focus({ preventScroll: true })
    setTimeout(() => {
      document.addEventListener('mousedown', onDocClick, true)
    }, 0)
  } else {
    document.removeEventListener('mousedown', onDocClick, true)
  }
}

function onDocClick(e) {
  if (panelRef.value && panelRef.value.contains(e.target)) return
  if (triggerRef.value && triggerRef.value.contains(e.target)) return
  open.value = false
  document.removeEventListener('mousedown', onDocClick, true)
}

// パネルを閉じる時に検索ワードはクリア（次に開いた時に状態を引きずらない）
watch(open, (v) => { if (!v) searchKeyword.value = '' })

usePopupFollowTrigger(open, triggerRef, updatePosition)

onBeforeUnmount(() => {
  document.removeEventListener('mousedown', onDocClick, true)
})

const isSearching = computed(() => !!searchKeyword.value.trim())
</script>

<template>
  <div class="group relative inline-flex w-full">
    <button
      ref="triggerRef"
      type="button"
      :disabled="disabled"
      :class="[
        'flex items-center justify-between w-full h-9 px-3 pr-8 border border-input rounded-lg bg-card text-sm transition cursor-pointer',
        'focus:outline-none focus:ring-2 focus:ring-ring',
        'hover:border-primary/50',
        'disabled:bg-muted/60 disabled:text-muted-foreground disabled:cursor-not-allowed disabled:border-border/60',
        open ? 'ring-2 ring-ring border-primary/50' : '',
        error ? 'border-destructive focus:ring-destructive/30' : ''
      ]"
      @click="toggleOpen"
    >
      <!-- パンくず表示 -->
      <span v-if="showBreadcrumb && selectedBreadcrumb.length" class="text-foreground truncate text-left">
        <template v-for="(part, idx) in selectedBreadcrumb" :key="idx">
          <span v-if="idx > 0" class="text-muted-foreground/70 mx-1">/</span>
          <span :class="idx === selectedBreadcrumb.length - 1 ? 'font-medium' : 'text-muted-foreground'">{{ part }}</span>
        </template>
      </span>
      <span v-else-if="selectedLabel" class="text-foreground truncate">{{ selectedLabel }}</span>
      <span v-else class="text-muted-foreground truncate">{{ placeholder || t('common.placeholder.pleaseSelect') }}</span>
      <ChevronDown
        v-if="!clearable || !hasValue"
        :size="14"
        class="absolute right-2.5 top-1/2 -translate-y-1/2 text-muted-foreground transition-transform duration-200"
        :class="{ 'rotate-180': open }"
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
        class="absolute right-2.5 top-1/2 -translate-y-1/2 text-muted-foreground transition-all duration-200 group-hover:opacity-0 pointer-events-none"
        :class="{ 'rotate-180': open }"
      />
    </template>

    <Teleport to="body">
      <div
        v-if="open"
        ref="panelRef"
        class="fixed -top-[9999px] -left-[9999px] z-[60] flex flex-col max-h-[360px] rounded-2xl border border-border bg-card shadow-xl overflow-hidden"
      >
        <!-- 検索ボックス -->
        <div class="shrink-0 border-b border-border p-1.5">
          <div class="relative">
            <Search :size="14" class="absolute left-2.5 top-1/2 -translate-y-1/2 text-muted-foreground" />
            <input
              ref="searchInputRef"
              v-model="searchKeyword"
              type="text"
              :placeholder="t('common.placeholder.search')"
              class="w-full h-8 pl-8 pr-2 rounded-md border border-input bg-card text-sm focus:outline-none focus:ring-2 focus:ring-ring"
              @click.stop
            />
          </div>
        </div>

        <!-- ツリー本体 -->
        <div class="flex-1 overflow-y-auto py-1">
          <div
            v-for="n in visibleNodes"
            :key="n.id"
            :class="[
              'flex items-center gap-1 py-1.5 pr-3 text-sm rounded-lg mx-1 transition-colors',
              disabledSet.has(String(n.id))
                ? 'text-muted-foreground cursor-not-allowed opacity-50'
                : 'cursor-pointer hover:bg-muted',
              String(n.id) === String(modelValue) ? 'bg-primary/10 text-primary font-medium' : 'text-foreground'
            ]"
            :style="{ paddingLeft: showConnectors ? '4px' : (n.level * 16 + 4) + 'px' }"
            @click="selectNode(n)"
          >
            <!-- 連結線プレフィックス（showConnectors=true のときのみ） -->
            <span
              v-if="showConnectors && n.level > 0"
              class="font-mono whitespace-pre text-muted-foreground/60 select-none shrink-0 leading-none"
            >{{ buildPrefix(n) }}</span>

            <!-- chevron。検索中は強制的に open 形にし、クリックは無効化 -->
            <button
              v-if="!n.isLeaf"
              type="button"
              :disabled="isSearching"
              class="size-4 shrink-0 inline-flex items-center justify-center text-muted-foreground hover:text-foreground disabled:hover:text-muted-foreground disabled:cursor-default"
              @click.stop="!isSearching && toggleExpand(n.id)"
            >
              <ChevronDown v-if="isSearching || isExpanded(n.id)" class="size-3.5" />
              <ChevronRight v-else class="size-3.5" />
            </button>
            <span v-else class="inline-block size-4 shrink-0"></span>

            <!-- アイコン（呼び出し側がスロットを埋めた時のみ描画） -->
            <slot name="node-icon" :node="n" :is-leaf="n.isLeaf" />

            <span class="truncate flex-1">{{ n.label }}</span>
            <Check v-if="String(n.id) === String(modelValue)" :size="14" class="shrink-0 text-primary" />
          </div>
          <div
            v-if="visibleNodes.length === 0"
            class="px-3 py-4 text-center text-sm text-muted-foreground"
          >
            該当なし
          </div>
        </div>
      </div>
    </Teleport>
  </div>
</template>
