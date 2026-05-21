<script setup>
import { computed, ref, onMounted, onBeforeUnmount } from 'vue'
import { useI18n } from 'vue-i18n'
import { cn } from '@/lib/utils'
import Select from '@/components/ui/Select.vue'
import Checkbox from '@/components/ui/Checkbox.vue'
import ColumnFilter from './ColumnFilter.vue'
import {
  ChevronLeft, ChevronRight, ChevronsLeft, ChevronsRight, ChevronDown, Loader2
} from 'lucide-vue-next'

const { t } = useI18n()

const props = defineProps({
  /**
   * 列配置
   * @type {{ key: string, title: string, minWidth?: string, sticky?: 'left'|'right',
   *          align?: 'left'|'center'|'right', filter?: { modelValue: string[], options: {value,label}[] },
   *          class?: string, headerClass?: string }[]}
   */
  columns: {
    type: Array,
    default: () => []
  },
  /** 表格数据 */
  data: {
    type: Array,
    default: () => []
  },
  /** 行唯一标识字段 */
  rowKey: {
    type: String,
    default: 'id'
  },
  /** 加载中 */
  loading: {
    type: Boolean,
    default: false
  },
  /** 当前页码（v-model:page） */
  page: {
    type: Number,
    default: 1
  },
  /** 每页条数（v-model:pageSize） */
  pageSize: {
    type: Number,
    default: 10
  },
  /** 总条数 */
  total: {
    type: Number,
    default: 0
  },
  /** 可选的每页条数列表 */
  pageSizeOptions: {
    type: Array,
    default: () => [10, 20, 50, 100]
  },
  /** 空数据提示文字。空文字列の場合は dataTable.emptyState の翻訳を使用 */
  emptyText: {
    type: String,
    default: ''
  },
  /** 加载提示文字。空文字列の場合は dataTable.loading の翻訳を使用 */
  loadingText: {
    type: String,
    default: ''
  },
  /** 是否显示分页 */
  showPagination: {
    type: Boolean,
    default: true
  },
  /** 行選択を有効化（先頭にチェックボックス列を追加） */
  selectable: {
    type: Boolean,
    default: false
  },
  /** 選択中の行配列（v-model:selection、行オブジェクトそのものを保持） */
  selection: {
    type: Array,
    default: () => []
  },
  /** 行が選択可能か判定する関数（省略時は常に選択可能） */
  rowSelectable: {
    type: Function,
    default: null
  },
  /** 行展開を有効化（先頭に展開トグル列を追加） */
  expandable: {
    type: Boolean,
    default: false
  },
  /** 展開中の行キー配列（v-model:expandedRowKeys） */
  expandedRowKeys: {
    type: Array,
    default: () => []
  },
  /** 行が展開可能か判定する関数（省略時は常に展開可能） */
  rowExpandable: {
    type: Function,
    default: null
  },
  class: {
    type: String,
    default: ''
  }
})

const emit = defineEmits(['update:page', 'update:pageSize', 'update:filter', 'update:selection', 'update:expandedRowKeys'])

// ── Row Selection ──
function isRowSelectable(row) {
  if (props.rowSelectable) return props.rowSelectable(row)
  return true
}

function isRowSelected(row) {
  const key = row[props.rowKey]
  return props.selection.some((r) => r[props.rowKey] === key)
}

// 選択可能な行のみ対象
const selectableRows = computed(() => {
  if (!props.selectable) return []
  return props.data.filter((row) => isRowSelectable(row))
})

// 当前页全选状態
const allSelected = computed(() => {
  if (selectableRows.value.length === 0) return false
  return selectableRows.value.every((row) => isRowSelected(row))
})
// 部分選択（インディタミネート用）
const someSelected = computed(() => {
  if (selectableRows.value.length === 0) return false
  return selectableRows.value.some((row) => isRowSelected(row)) && !allSelected.value
})

function toggleRow(row) {
  if (!isRowSelectable(row)) return
  const key = row[props.rowKey]
  const next = isRowSelected(row)
    ? props.selection.filter((r) => r[props.rowKey] !== key)
    : [...props.selection, row]
  emit('update:selection', next)
}

function toggleAll() {
  if (allSelected.value) {
    // 取消当前页可選択行の選中（保留其他页已选中的）
    const currentKeys = new Set(selectableRows.value.map((r) => r[props.rowKey]))
    emit('update:selection', props.selection.filter((r) => !currentKeys.has(r[props.rowKey])))
  } else {
    // 把当前页未选中的可選択行并入
    const merged = [...props.selection]
    selectableRows.value.forEach((row) => {
      if (!isRowSelected(row)) merged.push(row)
    })
    emit('update:selection', merged)
  }
}

// ── Row Expand ──
function isRowExpanded(row) {
  return props.expandedRowKeys.includes(row[props.rowKey])
}

function isExpandable(row) {
  if (props.rowExpandable) return props.rowExpandable(row)
  return true
}

function toggleExpand(row) {
  const key = row[props.rowKey]
  const next = isRowExpanded(row)
    ? props.expandedRowKeys.filter((k) => k !== key)
    : [...props.expandedRowKeys, key]
  emit('update:expandedRowKeys', next)
}

// ── Total column count (for colspan) ──
const totalColSpan = computed(() => {
  let count = props.columns.length
  if (props.selectable) count++
  if (props.expandable) count++
  return count
})

const totalPages = computed(() => Math.max(1, Math.ceil(props.total / props.pageSize)))

const pageSizeSelectOptions = computed(() =>
  props.pageSizeOptions.map((v) => ({ label: t('dataTable.pagination.perPage', { n: v }), value: v }))
)

const emptyDisplay = computed(() => props.emptyText || t('dataTable.emptyState'))
const loadingDisplay = computed(() => props.loadingText || t('dataTable.loading'))

function goToPage(p) {
  if (p < 1 || p > totalPages.value) return
  emit('update:page', p)
}

// ページ番号リスト (省略記号付き):
//   total <= 7        → 全件表示
//   current <= 4      → [1,2,3,4,5,right,last]
//   current >= last-3 → [1,left,last-4,last-3,last-2,last-1,last]
//   それ以外          → [1,left,cur-1,cur,cur+1,right,last]
// 'left'/'right' は省略記号、hover で -5/+5 ジャンプボタンに変わる。
const pageNumbers = computed(() => {
  const total = totalPages.value
  const cur = props.page
  if (total <= 7) return Array.from({ length: total }, (_, i) => i + 1)
  if (cur <= 4) return [1, 2, 3, 4, 5, 'right', total]
  if (cur >= total - 3) return [1, 'left', total - 4, total - 3, total - 2, total - 1, total]
  return [1, 'left', cur - 1, cur, cur + 1, 'right', total]
})

function jumpBy(delta) {
  const target = Math.min(Math.max(props.page + delta, 1), totalPages.value)
  if (target !== props.page) emit('update:page', target)
}

function changePageSize(val) {
  emit('update:pageSize', Number(val))
}

function onFilterUpdate(colKey, val) {
  emit('update:filter', { [colKey]: val })
}

// 連続する sticky:right 列のため、右端から累積幅をオフセットとして計算。
// 例: [...non-sticky, status(sticky 100px), actions(sticky 160px)]
//   → actions: right=0px / status: right=160px
// shadow (dt-sticky-right ::before) は最も左端の sticky:right 列だけに付与する
// (sticky と非 sticky の境界線上にだけ shadow を出す)。
const stickyRightInfo = computed(() => {
  const offsets = {}
  let cumulative = 0
  let leftmostKey = null
  for (let i = props.columns.length - 1; i >= 0; i--) {
    const col = props.columns[i]
    if (col.sticky === 'right') {
      offsets[col.key] = cumulative
      cumulative += parseInt(col.minWidth) || parseInt(col.width) || 0
      leftmostKey = col.key
    } else if (cumulative > 0) {
      // 連続性が途切れたらストップ (中間が非 sticky なら設定ミス扱い)
      break
    }
  }
  return { offsets, leftmostKey }
})

function stickyClass(col) {
  if (col.sticky === 'right') {
    const isLeftmost = stickyRightInfo.value.leftmostKey === col.key
    return isLeftmost ? 'sticky bg-card dt-sticky-right' : 'sticky bg-card'
  }
  if (col.sticky === 'left') return 'sticky left-0 bg-card dt-sticky-left'
  return ''
}

function stickyStyle(col) {
  if (col.sticky === 'right') {
    return { right: (stickyRightInfo.value.offsets[col.key] || 0) + 'px' }
  }
  return null
}

function alignClass(col) {
  if (col.align === 'center') return 'text-center'
  if (col.align === 'right') return 'text-right'
  return 'text-left'
}

// ── Scroll shadow: 滚到最右时隐藏右侧冻结列阴影 ──
const scrollRef = ref(null)
const scrolledToEnd = ref(false)

function onScroll() {
  const el = scrollRef.value
  if (!el) return
  // 容差 1px 防止亚像素误差
  scrolledToEnd.value = el.scrollLeft + el.clientWidth >= el.scrollWidth - 1
}

onMounted(() => {
  const el = scrollRef.value
  if (el) {
    el.addEventListener('scroll', onScroll, { passive: true })
    onScroll()
  }
})

onBeforeUnmount(() => {
  scrollRef.value?.removeEventListener('scroll', onScroll)
})
</script>

<template>
  <div :class="cn('flex flex-col', props.class)">
    <!-- Table area (relative for loading overlay) -->
    <div class="relative">
      <div
        ref="scrollRef"
        class="overflow-x-auto scrollbar-thin"
        :class="[{ 'dt-scrolled-end': scrolledToEnd }, loading && 'pointer-events-none select-none']"
      >
      <table class="w-full text-sm">
        <thead>
          <tr class="border-b border-border">
            <!-- Expand 列ヘッダー -->
            <th v-if="expandable" class="h-11 w-10 pl-3 pr-1" />
            <!-- Selection 列ヘッダー（全選択チェックボックス） -->
            <th
              v-if="selectable"
              class="h-10 w-10 pl-4 pr-2"
              scope="col"
            >
              <div class="flex items-center justify-start">
                <Checkbox
                  :model-value="allSelected"
                  :indeterminate="someSelected"
                  :disabled="selectableRows.length === 0"
                  @change="toggleAll"
                />
              </div>
            </th>
            <th
              v-for="(col, colIdx) in columns"
              :key="col.key"
              class="h-11 px-3 text-[13px] font-semibold text-foreground whitespace-nowrap"
              :class="cn(alignClass(col), stickyClass(col), col.headerClass, !selectable && !expandable && colIdx === 0 && 'pl-4', !col.sticky && colIdx === columns.length - 1 && 'pr-4')"
              :style="[col.minWidth && { minWidth: col.minWidth }, col.width && { width: col.width }, stickyStyle(col)]"
            >
              <div
                class="flex items-center gap-1"
                :class="{ 'justify-center': col.align === 'center', 'justify-end': col.align === 'right' }"
              >
                <slot :name="'header-' + col.key" :column="col">
                  {{ col.title }}
                </slot>
                <ColumnFilter
                  v-if="col.filter"
                  :options="col.filter.options"
                  :model-value="col.filter.modelValue"
                  @update:model-value="onFilterUpdate(col.key, $event)"
                />
              </div>
            </th>
          </tr>
        </thead>
        <tbody>
          <!-- Empty (loading 中は overlay が前面に出るので非表示) -->
          <tr v-if="!loading && data.length === 0">
            <td :colspan="totalColSpan" class="p-8 text-center text-muted-foreground">
              <slot name="empty">{{ emptyDisplay }}</slot>
            </td>
          </tr>
          <!-- Loading 中で初回データ無し: overlay 用のスペースを確保 -->
          <tr v-else-if="loading && data.length === 0">
            <td :colspan="totalColSpan" class="h-40" />
          </tr>
          <!-- Rows -->
          <template
            v-else
            v-for="(row, rowIndex) in data"
            :key="row[rowKey] ?? rowIndex"
          >
            <tr class="border-b border-border/40 hover:bg-muted/40 transition-colors">
              <!-- Expand 列セル -->
              <td v-if="expandable" class="w-10 pl-3 pr-1 py-2">
                <button
                  v-if="isExpandable(row)"
                  class="inline-flex items-center justify-center h-6 w-6 rounded hover:bg-muted transition-colors"
                  @click="toggleExpand(row)"
                >
                  <ChevronDown
                    :size="14"
                    class="transition-transform"
                    :class="{ 'rotate-180': isRowExpanded(row) }"
                  />
                </button>
              </td>
              <!-- Selection 列セル -->
              <td v-if="selectable" class="w-10 pl-4 pr-2 py-2">
                <div class="flex items-center justify-start">
                  <Checkbox
                    :model-value="isRowSelected(row)"
                    :disabled="!isRowSelectable(row)"
                    @change="toggleRow(row)"
                  />
                </div>
              </td>
              <td
                v-for="(col, colIdx) in columns"
                :key="col.key"
                class="px-3 py-2"
                :class="cn(alignClass(col), stickyClass(col), col.class, !selectable && !expandable && colIdx === 0 && 'pl-4', !col.sticky && colIdx === columns.length - 1 && 'pr-4')"
                :style="stickyStyle(col)"
              >
                <slot :name="'cell-' + col.key" :row="row" :value="row[col.key]" :index="rowIndex">
                  {{ row[col.key] }}
                </slot>
              </td>
            </tr>
            <!-- Expanded content row -->
            <tr v-if="expandable && isRowExpanded(row)">
              <td :colspan="totalColSpan" class="p-0">
                <slot name="expand" :row="row" :index="rowIndex" />
              </td>
            </tr>
          </template>
        </tbody>
      </table>
      </div>

      <!-- Loading overlay -->
      <div
        v-if="loading"
        class="absolute inset-0 z-10 flex items-center justify-center bg-background/55 backdrop-blur-[1px] transition-opacity"
      >
        <div class="flex items-center gap-2 px-4 py-2.5 rounded-lg bg-card shadow-lg border border-border">
          <slot name="loading">
            <Loader2 :size="20" class="animate-spin text-brand-orange" />
            <span class="text-sm text-foreground font-medium">{{ loadingDisplay }}</span>
          </slot>
        </div>
      </div>
    </div>

    <!-- Pagination -->
    <div v-if="showPagination" class="flex items-center justify-between px-4 py-2.5 border-t border-border gap-4">
      <span class="text-sm text-foreground font-medium whitespace-nowrap shrink-0">
        {{ t('dataTable.pagination.total', { n: total }) }}
      </span>
      <div class="flex items-center gap-1 shrink-0">
        <Select
          :model-value="pageSize"
          :options="pageSizeSelectOptions"
          :clearable="false"
          class="w-[130px] shrink-0"
          @update:model-value="changePageSize"
        />
        <button
          class="h-8 w-8 shrink-0 inline-flex items-center justify-center rounded-lg hover:bg-muted transition-colors disabled:opacity-40 disabled:cursor-not-allowed"
          :disabled="page <= 1"
          @click="goToPage(1)"
        >
          <ChevronsLeft :size="14" />
        </button>
        <button
          class="h-8 w-8 shrink-0 inline-flex items-center justify-center rounded-lg hover:bg-muted transition-colors disabled:opacity-40 disabled:cursor-not-allowed"
          :disabled="page <= 1"
          @click="goToPage(page - 1)"
        >
          <ChevronLeft :size="14" />
        </button>
        <div class="flex items-center gap-0.5 px-0.5">
          <template v-for="(p, i) in pageNumbers" :key="`${p}-${i}`">
            <button
              v-if="p === 'left' || p === 'right'"
              type="button"
              :title="p === 'left' ? t('common.tooltip.pagePrevious5') : t('common.tooltip.pageNext5')"
              class="group h-8 w-8 inline-flex items-center justify-center rounded-md text-sm tabular-nums text-muted-foreground hover:bg-muted hover:text-primary transition-colors cursor-pointer"
              @click="jumpBy(p === 'left' ? -5 : 5)"
            >
              <span class="group-hover:hidden">…</span>
              <span class="hidden group-hover:inline">{{ p === 'left' ? '-5' : '+5' }}</span>
            </button>
            <button
              v-else
              type="button"
              :class="[
                'h-8 min-w-8 px-2 inline-flex items-center justify-center rounded-md text-sm tabular-nums transition-colors cursor-pointer',
                p === page
                  ? 'bg-primary text-primary-foreground font-medium hover:bg-primary'
                  : 'hover:bg-muted text-foreground'
              ]"
              @click="goToPage(p)"
            >{{ p }}</button>
          </template>
        </div>
        <button
          class="h-8 w-8 shrink-0 inline-flex items-center justify-center rounded-lg hover:bg-muted transition-colors disabled:opacity-40 disabled:cursor-not-allowed"
          :disabled="page >= totalPages"
          @click="goToPage(page + 1)"
        >
          <ChevronRight :size="14" />
        </button>
        <button
          class="h-8 w-8 shrink-0 inline-flex items-center justify-center rounded-lg hover:bg-muted transition-colors disabled:opacity-40 disabled:cursor-not-allowed"
          :disabled="page >= totalPages"
          @click="goToPage(totalPages)"
        >
          <ChevronsRight :size="14" />
        </button>
      </div>
    </div>
  </div>
</template>

<style scoped>
.dt-sticky-right::before {
  content: '';
  position: absolute;
  top: 0;
  bottom: 0;
  left: -6px;
  width: 6px;
  pointer-events: none;
  background: linear-gradient(to right, transparent, rgba(0, 0, 0, 0.06));
  transition: opacity 0.2s;
}
.dt-scrolled-end .dt-sticky-right::before {
  opacity: 0;
}
.dt-sticky-left::after {
  content: '';
  position: absolute;
  top: 0;
  bottom: 0;
  right: -6px;
  width: 6px;
  pointer-events: none;
  background: linear-gradient(to left, transparent, rgba(0, 0, 0, 0.06));
}
</style>
