<script setup>
import { ref, computed, watch, nextTick, onBeforeUnmount, inject } from 'vue'
import { useI18n } from 'vue-i18n'
import { cn } from '@/lib/utils'
import { toBackendDate, toJSTDateStr, todayJSTStr, nowJST } from '@/lib/date'
import { Calendar, ChevronLeft, ChevronRight, X } from 'lucide-vue-next'
import { usePopupFollowTrigger, applyAbsolutePopupPosition } from '@/composables/usePopupFollowTrigger'

const { t, tm } = useI18n()

const props = defineProps({
  /** 開始日（YYYY-MM-DD / YYYY-MM-DD HH:mm:ssZZ 等） */
  startDate: {
    type: String,
    default: ''
  },
  /** 終了日（YYYY-MM-DD / YYYY-MM-DD HH:mm:ssZZ 等） */
  endDate: {
    type: String,
    default: ''
  },
  startPlaceholder: {
    type: String,
    default: ''
  },
  endPlaceholder: {
    type: String,
    default: ''
  },
  /**
   * 出力フォーマット（DatePicker と同仕様）:
   *   'YYYY-MM-DD' (デフォルト)          — 純日付、従来互換
   *   'YYYY-MM-DD HH:mm:ssZZ'            — 後端要求のバックエンド形式（JST 0 時に拡張）
   */
  valueFormat: {
    type: String,
    default: 'YYYY-MM-DD'
  },
  disabled: {
    type: Boolean,
    default: false
  },
  error: {
    type: Boolean,
    default: false
  },
  /**
   * 選択モード
   *   false (デフォルト) — 拆两半：開始日・終了日を独立に選択、片側のみ指定可
   *   true              — 連続区間：開始日を選ぶと自動で終了日へ、両方必須
   */
  rangeMode: {
    type: Boolean,
    default: false
  },
  class: {
    type: String,
    default: ''
  }
})

const emit = defineEmits(['update:startDate', 'update:endDate'])

/**
 * カレンダーから選ばれた 'YYYY-MM-DD' を v-model に emit する形式に変換
 */
function formatForEmit(dateStr) {
  if (!dateStr) return ''
  const fmt = props.valueFormat
  if (!fmt || fmt === 'YYYY-MM-DD') return dateStr
  if (fmt === 'YYYY-MM-DD HH:mm:ssZZ') return toBackendDate(dateStr)
  return dateStr
}

const open = ref(false)
/** 'start' | 'end' — which side is being picked */
const picking = ref('start')
const triggerRef = ref(null)
const panelRef = ref(null)

// JST 基準で初期化、ブラウザ TZ 非依存
const viewYear = ref(nowJST().year())
const viewMonth = ref(nowJST().month())
/** 'date' | 'month' | 'year' */
const view = ref('date')

const WEEKDAYS = computed(() => tm('common.datePicker.weekdays'))
const MONTH_LABELS = computed(() => tm('common.datePicker.months'))
const startPlaceholderText = computed(() => props.startPlaceholder || t('common.datePicker.startPlaceholder'))
const endPlaceholderText = computed(() => props.endPlaceholder || t('common.datePicker.endPlaceholder'))

const yearRangeStart = computed(() => Math.floor(viewYear.value / 12) * 12)
const yearList = computed(() => Array.from({ length: 12 }, (_, i) => yearRangeStart.value + i))

// ── Display ──
// 入力は 'YYYY-MM-DD' 或 'YYYY-MM-DD HH:mm:ssZZ' どちらも受け付け、
// toJSTDateStr で一律純日付に正規化してから Date オブジェクトを作る
function formatDate(str, short = false) {
  const pure = toJSTDateStr(str)
  if (!pure) return ''
  const d = new Date(pure + 'T00:00:00')
  if (isNaN(d.getTime())) return ''
  if (short) {
    return `${String(d.getMonth() + 1)}/${String(d.getDate()).padStart(2, '0')}`
  }
  return `${d.getFullYear()}/${String(d.getMonth() + 1).padStart(2, '0')}/${String(d.getDate()).padStart(2, '0')}`
}

/** 触发器上用短格式（同年省略年份） */
function triggerFormat(str) {
  const pure = toJSTDateStr(str)
  if (!pure) return ''
  const d = new Date(pure + 'T00:00:00')
  if (isNaN(d.getTime())) return ''
  const m = String(d.getMonth() + 1).padStart(2, '0')
  const day = String(d.getDate()).padStart(2, '0')
  // 同年判定は JST 基準（ブラウザ TZ で「同年」が変わるのを防ぐ）
  if (d.getFullYear() === nowJST().year()) return `${m}/${day}`
  return `${d.getFullYear()}/${m}/${day}`
}

const startDisplay = computed(() => formatDate(props.startDate))
const endDisplay = computed(() => formatDate(props.endDate))
const startTrigger = computed(() => triggerFormat(props.startDate))
const endTrigger = computed(() => triggerFormat(props.endDate))
const hasValue = computed(() => !!props.startDate || !!props.endDate)

const monthLabel = computed(() => t('common.datePicker.yearMonth', {
  year: viewYear.value,
  month: viewMonth.value + 1,
  monthName: MONTH_LABELS.value[viewMonth.value]
}))
const headerLabel = computed(() => {
  if (view.value === 'year') return `${yearRangeStart.value} - ${yearRangeStart.value + 11}`
  if (view.value === 'month') return t('common.datePicker.year', { year: viewYear.value })
  return monthLabel.value
})

// ── Calendar days ──
// 表示月以外の日付 (前月末尾・翌月先頭) にも実日付を割り当てて選択可能にする
const calendarDays = computed(() => {
  const y = viewYear.value
  const m = viewMonth.value
  const firstDay = new Date(y, m, 1)
  const lastDay = new Date(y, m + 1, 0)

  let startDow = firstDay.getDay() - 1
  if (startDow < 0) startDow = 6

  const pad = n => String(n).padStart(2, '0')
  const days = []
  // 前月末尾 (灰色表示 / click 可)
  const prevMonth = m === 0 ? 11 : m - 1
  const prevYear = m === 0 ? y - 1 : y
  const prevLastDay = new Date(y, m, 0).getDate()
  for (let i = startDow - 1; i >= 0; i--) {
    const dayNum = prevLastDay - i
    days.push({ day: dayNum, current: false, date: `${prevYear}-${pad(prevMonth + 1)}-${pad(dayNum)}` })
  }
  // 当月
  for (let d = 1; d <= lastDay.getDate(); d++) {
    days.push({ day: d, current: true, date: `${y}-${pad(m + 1)}-${pad(d)}` })
  }
  // 翌月先頭 (灰色表示 / click 可)
  const nextMonth = m === 11 ? 0 : m + 1
  const nextYear = m === 11 ? y + 1 : y
  const remaining = 42 - days.length
  for (let i = 1; i <= remaining; i++) {
    days.push({ day: i, current: false, date: `${nextYear}-${pad(nextMonth + 1)}-${pad(i)}` })
  }
  return days
})

function isToday(dateStr) {
  return dateStr === todayJSTStr()
}

// startDate/endDate は backend 形式（YYYY-MM-DD HH:mm:ssZZ）の可能性もあるので、
// 比較する前に一律 YYYY-MM-DD に正規化
const normalizedStart = computed(() => toJSTDateStr(props.startDate))
const normalizedEnd = computed(() => toJSTDateStr(props.endDate))

function isStart(dateStr) {
  return dateStr && dateStr === normalizedStart.value
}

function isEnd(dateStr) {
  return dateStr && dateStr === normalizedEnd.value
}

function isInRange(dateStr) {
  if (!dateStr || !normalizedStart.value || !normalizedEnd.value) return false
  return dateStr > normalizedStart.value && dateStr < normalizedEnd.value
}

function dayClass(day) {
  const d = day.date
  if (isStart(d) || isEnd(d)) return 'bg-primary text-primary-foreground font-medium'
  if (isInRange(d)) return 'bg-primary/10 text-primary'
  if (isDisabledDay(d)) return 'text-muted-foreground/30 cursor-not-allowed'
  if (isToday(d)) return 'bg-accent/15 text-accent font-medium hover:bg-accent/25'
  // 表示月以外: 灰色系で可クリック
  if (!day.current) return 'text-muted-foreground/50 hover:bg-muted'
  return 'text-foreground hover:bg-muted'
}

/** 終了日选择时，早于開始日的日期禁用 */
function isDisabledDay(dateStr) {
  if (!dateStr) return false
  if (picking.value === 'end' && normalizedStart.value && dateStr < normalizedStart.value) return true
  return false
}

// ── Actions ──
function selectDay(day) {
  if (!day.date) return
  if (isDisabledDay(day.date)) return
  // 表示月以外を選んだらカレンダー視点もその月へ移動
  if (!day.current) {
    const d = new Date(day.date + 'T00:00:00')
    if (!isNaN(d.getTime())) {
      viewYear.value = d.getFullYear()
      viewMonth.value = d.getMonth()
    }
  }

  if (picking.value === 'start') {
    emit('update:startDate', formatForEmit(day.date))
    // If end date is before new start, clear it
    if (normalizedEnd.value && day.date > normalizedEnd.value) {
      emit('update:endDate', '')
    }
    if (props.rangeMode) {
      // 連続区間モード：自動で終了日選択へ
      picking.value = 'end'
    } else {
      // 拆两半モード：開始日のみ確定して閉じる
      open.value = false
    }
  } else {
    emit('update:endDate', formatForEmit(day.date))
    open.value = false
  }
}

function clearStart() {
  emit('update:startDate', '')
}

function clearEnd() {
  emit('update:endDate', '')
}

function prevMonth() {
  if (viewMonth.value === 0) { viewMonth.value = 11; viewYear.value-- }
  else viewMonth.value--
}

function nextMonth() {
  if (viewMonth.value === 11) { viewMonth.value = 0; viewYear.value++ }
  else viewMonth.value++
}

function goToday() {
  const j = nowJST()
  viewYear.value = j.year()
  viewMonth.value = j.month()
  view.value = 'date'
}

function headerPrev() {
  if (view.value === 'year') viewYear.value -= 12
  else if (view.value === 'month') viewYear.value--
  else prevMonth()
}

function headerNext() {
  if (view.value === 'year') viewYear.value += 12
  else if (view.value === 'month') viewYear.value++
  else nextMonth()
}

function onHeaderClick() {
  if (view.value === 'date') view.value = 'month'
  else if (view.value === 'month') view.value = 'year'
}

function selectMonthView(m) {
  viewMonth.value = m
  view.value = 'date'
}

function selectYearView(y) {
  viewYear.value = y
  view.value = 'month'
}

function clearAll() {
  emit('update:startDate', '')
  emit('update:endDate', '')
  open.value = false
}

// ── Open/Close ──
const injectedPopupZ = inject('popupZIndex', null)

function updatePosition() {
  if (!triggerRef.value || !panelRef.value) return
  applyAbsolutePopupPosition(panelRef.value, triggerRef.value.getBoundingClientRect(), {
    assumedHeight: 340,
    zIndex: injectedPopupZ?.value,
    triggerEl: triggerRef.value
  })
}

async function openPicker(side) {
  if (props.disabled) return
  picking.value = side
  view.value = 'date'
  // Sync calendar view to the relevant date
  const target = toJSTDateStr(side === 'start' ? props.startDate : (props.endDate || props.startDate))
  if (target) {
    const d = new Date(target + 'T00:00:00')
    if (!isNaN(d.getTime())) {
      viewYear.value = d.getFullYear()
      viewMonth.value = d.getMonth()
    }
  }
  open.value = true
  await nextTick()
  updatePosition()
  setTimeout(() => {
    document.addEventListener('mousedown', onDocClick, true)
  }, 0)
}

function onDocClick(e) {
  if (panelRef.value && panelRef.value.contains(e.target)) return
  if (triggerRef.value && triggerRef.value.contains(e.target)) return
  open.value = false
  document.removeEventListener('mousedown', onDocClick, true)
}

watch(open, (val) => {
  if (!val) document.removeEventListener('mousedown', onDocClick, true)
})

// Popup を開いている間は trigger の位置に追従 (scroll / resize 時に再配置)
usePopupFollowTrigger(open, triggerRef, updatePosition)

onBeforeUnmount(() => {
  document.removeEventListener('mousedown', onDocClick, true)
})
</script>

<template>
  <div class="group relative inline-flex w-full" ref="triggerRef">
    <!-- Range mode trigger (連続区間) -->
    <button
      v-if="rangeMode"
      type="button"
      :disabled="disabled"
      :class="cn(
        'flex items-center gap-1 w-full h-9 px-2.5 pr-7 border border-input rounded-lg bg-card text-sm transition cursor-pointer',
        'focus:outline-none focus:ring-2 focus:ring-ring',
        'hover:border-primary/50',
        'disabled:bg-muted/60 disabled:text-muted-foreground disabled:cursor-not-allowed disabled:border-border/60',
        open ? 'ring-2 ring-ring border-primary/50' : '',
        error && 'border-destructive focus:ring-destructive/30',
        props.class
      )"
      @click="openPicker(startDate && !endDate ? 'end' : 'start')"
    >
      <Calendar :size="14" class="shrink-0 text-muted-foreground" />
      <template v-if="startDisplay || endDisplay">
        <span class="flex-1 text-center text-foreground whitespace-nowrap">{{ startDisplay || '...' }}</span>
        <span class="text-muted-foreground shrink-0">~</span>
        <span class="flex-1 text-center text-foreground whitespace-nowrap">{{ endDisplay || '...' }}</span>
      </template>
      <template v-else>
        <span class="flex-1 text-center text-muted-foreground">{{ startPlaceholderText }}</span>
        <span class="text-muted-foreground shrink-0">~</span>
        <span class="flex-1 text-center text-muted-foreground">{{ endPlaceholderText }}</span>
      </template>
    </button>

    <!-- Split mode trigger (拆两半) -->
    <div
      v-else
      :class="cn(
        'flex items-center gap-1 w-full h-9 px-2.5 border border-input rounded-lg bg-card text-sm transition',
        'focus-within:outline-none focus-within:ring-2 focus-within:ring-ring focus-within:border-primary/50',
        !disabled && 'hover:border-primary/50',
        disabled && 'bg-muted/60 text-muted-foreground cursor-not-allowed border-border/60',
        open ? 'ring-2 ring-ring border-primary/50' : '',
        error && 'border-destructive focus-within:ring-destructive/30',
        props.class
      )"
    >
      <Calendar :size="14" class="shrink-0 text-muted-foreground" />
      <button
        type="button"
        :disabled="disabled"
        class="flex-1 min-w-0 h-full text-center whitespace-nowrap focus:outline-none cursor-pointer disabled:cursor-not-allowed"
        :class="startDisplay ? 'text-foreground' : 'text-muted-foreground'"
        @click.stop="openPicker('start')"
      >
        {{ startDisplay || startPlaceholderText }}
      </button>
      <span
        v-if="startDate && !disabled"
        class="opacity-0 group-hover:opacity-100 transition-opacity cursor-pointer p-0.5 rounded-full hover:bg-muted shrink-0"
        @click.stop="clearStart"
      >
        <X :size="12" class="text-muted-foreground" />
      </span>
      <span class="text-muted-foreground shrink-0">~</span>
      <button
        type="button"
        :disabled="disabled"
        class="flex-1 min-w-0 h-full text-center whitespace-nowrap focus:outline-none cursor-pointer disabled:cursor-not-allowed"
        :class="endDisplay ? 'text-foreground' : 'text-muted-foreground'"
        @click.stop="openPicker('end')"
      >
        {{ endDisplay || endPlaceholderText }}
      </button>
      <span
        v-if="endDate && !disabled"
        class="opacity-0 group-hover:opacity-100 transition-opacity cursor-pointer p-0.5 rounded-full hover:bg-muted shrink-0"
        @click.stop="clearEnd"
      >
        <X :size="12" class="text-muted-foreground" />
      </span>
    </div>

    <!-- Clear all (range mode only) -->
    <span
      v-if="rangeMode && hasValue && !disabled"
      class="absolute right-2 top-1/2 -translate-y-1/2 opacity-0 group-hover:opacity-100 transition-opacity cursor-pointer p-0.5 rounded-full hover:bg-muted z-10"
      @click.stop="clearAll"
    >
      <X :size="14" class="text-muted-foreground" />
    </span>

    <!-- Calendar Panel -->
    <Teleport to="body">
      <div
        v-if="open"
        ref="panelRef"
        class="fixed -top-[9999px] -left-[9999px] z-[60] w-[280px] rounded-xl border border-border bg-card shadow-xl p-3"
      >
        <!-- Picking indicator -->
        <div class="flex items-center gap-2 mb-2">
          <button
            type="button"
            class="flex-1 text-center text-xs py-1 rounded-md transition-colors"
            :class="picking === 'start' ? 'bg-primary/10 text-primary font-medium' : 'text-muted-foreground hover:bg-muted'"
            @click.stop="picking = 'start'"
          >
            {{ startDisplay || startPlaceholderText }}
          </button>
          <span class="text-muted-foreground text-xs">~</span>
          <button
            type="button"
            class="flex-1 text-center text-xs py-1 rounded-md transition-colors"
            :class="picking === 'end' ? 'bg-primary/10 text-primary font-medium' : 'text-muted-foreground hover:bg-muted'"
            @click.stop="picking = 'end'"
          >
            {{ endDisplay || endPlaceholderText }}
          </button>
        </div>

        <!-- Month nav -->
        <div class="flex items-center justify-between mb-2">
          <button
            type="button"
            class="h-7 w-7 inline-flex items-center justify-center rounded-lg hover:bg-muted transition-colors text-muted-foreground hover:text-foreground"
            @click.stop="headerPrev"
          >
            <ChevronLeft :size="16" />
          </button>
          <button
            type="button"
            class="text-sm font-medium text-foreground hover:text-primary transition-colors px-2 py-0.5 rounded-md hover:bg-muted"
            @click.stop="onHeaderClick"
          >
            {{ headerLabel }}
          </button>
          <button
            type="button"
            class="h-7 w-7 inline-flex items-center justify-center rounded-lg hover:bg-muted transition-colors text-muted-foreground hover:text-foreground"
            @click.stop="headerNext"
          >
            <ChevronRight :size="16" />
          </button>
        </div>

        <!-- Date view -->
        <template v-if="view === 'date'">
          <!-- Weekday headers -->
          <div class="grid grid-cols-7 mb-1">
            <div v-for="wd in WEEKDAYS" :key="wd" class="h-8 flex items-center justify-center text-xs font-medium text-muted-foreground">
              {{ wd }}
            </div>
          </div>

          <!-- Days -->
          <div class="grid grid-cols-7">
            <button
              v-for="(day, idx) in calendarDays"
              :key="idx"
              type="button"
              :disabled="isDisabledDay(day.date)"
              class="h-8 w-full inline-flex items-center justify-center text-sm rounded-lg transition-colors"
              :class="dayClass(day)"
              @click.stop="selectDay(day)"
            >
              {{ day.day }}
            </button>
          </div>
        </template>

        <!-- Month view -->
        <div v-else-if="view === 'month'" class="grid grid-cols-3 gap-1">
          <button
            v-for="(ml, mi) in MONTH_LABELS"
            :key="mi"
            type="button"
            class="h-14 inline-flex items-center justify-center text-sm rounded-lg transition-colors"
            :class="viewMonth === mi
              ? 'bg-primary text-primary-foreground font-medium'
              : 'text-foreground hover:bg-muted'"
            @click.stop="selectMonthView(mi)"
          >
            {{ ml }}
          </button>
        </div>

        <!-- Year view -->
        <div v-else class="grid grid-cols-3 gap-1">
          <button
            v-for="y in yearList"
            :key="y"
            type="button"
            class="h-14 inline-flex items-center justify-center text-sm rounded-lg transition-colors"
            :class="viewYear === y
              ? 'bg-primary text-primary-foreground font-medium'
              : 'text-foreground hover:bg-muted'"
            @click.stop="selectYearView(y)"
          >
            {{ y }}
          </button>
        </div>

        <!-- Footer -->
        <div class="flex items-center justify-between mt-2 pt-2 border-t border-border">
          <button
            type="button"
            class="text-xs text-muted-foreground hover:text-foreground transition-colors px-2 py-1 rounded-md hover:bg-muted"
            @click.stop="clearAll"
          >
            {{ t('common.button.clear') }}
          </button>
          <button
            type="button"
            class="text-xs text-primary hover:text-primary/80 transition-colors px-2 py-1 rounded-md hover:bg-primary/10 font-medium"
            @click.stop="goToday"
          >
            {{ t('common.datePicker.today') }}
          </button>
        </div>
      </div>
    </Teleport>
  </div>
</template>
