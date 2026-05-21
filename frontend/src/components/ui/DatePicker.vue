<script setup>
import { ref, computed, watch, nextTick, onBeforeUnmount, inject } from 'vue'
import { useI18n } from 'vue-i18n'
import { cn } from '@/lib/utils'
import { toJSTDateStr, toBackendDate, todayJSTStr, nowJST } from '@/lib/date'
import { Calendar, ChevronLeft, ChevronRight, X } from 'lucide-vue-next'
import { usePopupFollowTrigger, applyAbsolutePopupPosition } from '@/composables/usePopupFollowTrigger'

const { t, tm } = useI18n()

const props = defineProps({
  modelValue: {
    type: String,
    default: ''
  },
  placeholder: {
    type: String,
    default: ''
  },
  /**
   * 出力フォーマット:
   *   'YYYY-MM-DD' (デフォルト)          — 純日付、従来互換
   *   'YYYY-MM-DD HH:mm:ssZZ'            — 後端要求のバックエンド形式（JST 0 時に拡張）
   *   その他任意の dayjs フォーマット    — 汎用ケース、ローカルタイムで format
   * 注意: 入力 (modelValue) は引き続きどちらのフォーマットも受け付ける。
   */
  valueFormat: {
    type: String,
    default: 'YYYY-MM-DD'
  },
  /**
   * Picker mode: 'date' (default, full calendar) | 'month' (year-month only, emits 'YYYY-MM') | 'year' (year only, emits 'YYYY')
   */
  picker: {
    type: String,
    default: 'date'
  },
  class: {
    type: String,
    default: ''
  },
  disabled: {
    type: Boolean,
    default: false
  },
  error: {
    type: Boolean,
    default: false
  }
})

const emit = defineEmits(['update:modelValue'])

/**
 * カレンダーから選ばれた 'YYYY-MM-DD' を v-model に emit する形式に変換
 */
function formatForEmit(dateStr) {
  if (!dateStr) return ''
  const fmt = props.valueFormat
  if (!fmt || fmt === 'YYYY-MM-DD') return dateStr
  // JST 強制バックエンド形式
  if (fmt === 'YYYY-MM-DD HH:mm:ssZZ') return toBackendDate(dateStr)
  // 汎用: dayjs でフォーマット（ローカルタイム、純日付 → 0 時基準）
  // 追加フォーマットが必要ならここに case を足す
  return dateStr
}

const open = ref(false)
const triggerRef = ref(null)
const panelRef = ref(null)

// Calendar state — 初期表示は JST 基準（ブラウザ TZ に左右されない）
const viewYear = ref(nowJST().year())
const viewMonth = ref(nowJST().month())
/** 'date' | 'month' | 'year' */
const view = ref('date')

const WEEKDAYS = computed(() => tm('common.datePicker.weekdays'))
const MONTH_LABELS = computed(() => tm('common.datePicker.months'))
const placeholderText = computed(() => props.placeholder || t('common.datePicker.placeholder'))

const yearRangeStart = computed(() => Math.floor(viewYear.value / 12) * 12)
const yearList = computed(() => Array.from({ length: 12 }, (_, i) => yearRangeStart.value + i))
const headerLabel = computed(() => {
  if (view.value === 'year') return `${yearRangeStart.value} - ${yearRangeStart.value + 11}`
  if (view.value === 'month') return t('common.datePicker.year', { year: viewYear.value })
  return monthLabel.value
})

/** 当前 modelValue 转换为 JST 的 YYYY-MM-DD 字符串（用於和日历比较） */
const selectedDateStr = computed(() => toJSTDateStr(props.modelValue))

const displayValue = computed(() => {
  if (props.picker === 'year') {
    if (!props.modelValue) return ''
    const m = String(props.modelValue).match(/^(\d{4})/)
    return m ? m[1] : ''
  }
  if (props.picker === 'month') {
    if (!props.modelValue) return ''
    // Accept 'YYYY-MM' or 'YYYY-MM-DD...' — show 'YYYY/MM'
    const m = String(props.modelValue).match(/^(\d{4})-(\d{2})/)
    return m ? `${m[1]}/${m[2]}` : ''
  }
  if (!selectedDateStr.value) return ''
  return selectedDateStr.value.replace(/-/g, '/')
})

const selectedDateObj = computed(() => {
  if (!selectedDateStr.value) return null
  const [y, m, d] = selectedDateStr.value.split('-').map(Number)
  return new Date(y, m - 1, d)
})

const monthLabel = computed(() => t('common.datePicker.yearMonth', {
  year: viewYear.value,
  month: viewMonth.value + 1,
  monthName: MONTH_LABELS.value[viewMonth.value]
}))

const calendarDays = computed(() => {
  const y = viewYear.value
  const m = viewMonth.value
  const firstDay = new Date(y, m, 1)
  const lastDay = new Date(y, m + 1, 0)

  // Monday = 0
  let startDow = firstDay.getDay() - 1
  if (startDow < 0) startDow = 6

  const days = []

  // Previous month padding
  const prevLastDay = new Date(y, m, 0).getDate()
  for (let i = startDow - 1; i >= 0; i--) {
    days.push({ day: prevLastDay - i, current: false, date: null })
  }

  // Current month
  for (let d = 1; d <= lastDay.getDate(); d++) {
    const mm = String(m + 1).padStart(2, '0')
    const dd = String(d).padStart(2, '0')
    days.push({ day: d, current: true, date: `${y}-${mm}-${dd}` })
  }

  // Next month padding
  const remaining = 42 - days.length
  for (let i = 1; i <= remaining; i++) {
    days.push({ day: i, current: false, date: null })
  }

  return days
})

function isSelected(dateStr) {
  return dateStr && dateStr === selectedDateStr.value
}

function isToday(dateStr) {
  return !!dateStr && dateStr === todayJSTStr()
}

function selectDay(day) {
  if (!day.date) return
  emit('update:modelValue', formatForEmit(day.date))
  open.value = false
}

function prevMonth() {
  if (viewMonth.value === 0) {
    viewMonth.value = 11
    viewYear.value--
  } else {
    viewMonth.value--
  }
}

function nextMonth() {
  if (viewMonth.value === 11) {
    viewMonth.value = 0
    viewYear.value++
  } else {
    viewMonth.value++
  }
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
  if (props.picker === 'month') {
    const mm = String(m + 1).padStart(2, '0')
    emit('update:modelValue', `${viewYear.value}-${mm}`)
    open.value = false
    return
  }
  view.value = 'date'
}

function selectYearView(y) {
  viewYear.value = y
  if (props.picker === 'year') {
    emit('update:modelValue', String(y))
    open.value = false
    return
  }
  view.value = 'month'
}

function goToday() {
  const j = nowJST()
  viewYear.value = j.year()
  viewMonth.value = j.month()
  view.value = 'date'
}

function clearValue() {
  emit('update:modelValue', '')
  open.value = false
}

// Drawer/Dialog 内なら自分より高い z-index、それ以外は undefined (class z-[60] が活きる)
const injectedPopupZ = inject('popupZIndex', null)

function updatePosition() {
  if (!triggerRef.value || !panelRef.value) return
  const rect = triggerRef.value.getBoundingClientRect()
  applyAbsolutePopupPosition(panelRef.value, rect, {
    assumedHeight: 320,
    zIndex: injectedPopupZ?.value,
    triggerEl: triggerRef.value
  })
}

async function toggleOpen() {
  if (props.disabled) return
  open.value = !open.value
  if (open.value) {
    view.value = props.picker === 'year' ? 'year' : props.picker === 'month' ? 'month' : 'date'
    // Sync view to selected date
    if (props.picker === 'year' && props.modelValue) {
      const m = String(props.modelValue).match(/^(\d{4})/)
      if (m) viewYear.value = Number(m[1])
    } else if (props.picker === 'month' && props.modelValue) {
      const m = String(props.modelValue).match(/^(\d{4})-(\d{2})/)
      if (m) {
        viewYear.value = Number(m[1])
        viewMonth.value = Number(m[2]) - 1
      }
    } else if (selectedDateObj.value) {
      viewYear.value = selectedDateObj.value.getFullYear()
      viewMonth.value = selectedDateObj.value.getMonth()
    }
    await nextTick()
    updatePosition()
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

// Popup を開いている間は trigger の位置に追従 (scroll / resize 時に再配置)
usePopupFollowTrigger(open, triggerRef, updatePosition)

onBeforeUnmount(() => {
  document.removeEventListener('mousedown', onDocClick, true)
})

// Watch for external open state to close listener
watch(open, (val) => {
  if (!val) {
    document.removeEventListener('mousedown', onDocClick, true)
  }
})
</script>

<template>
  <div class="group relative inline-flex w-full">
    <!-- Trigger -->
    <button
      ref="triggerRef"
      type="button"
      :disabled="disabled"
      :class="cn(
        'flex items-center gap-2 w-full h-9 px-3 pr-8 border border-input rounded-lg bg-card text-sm transition cursor-pointer',
        'focus:outline-none focus:ring-2 focus:ring-ring',
        'hover:border-primary/50',
        'disabled:bg-muted/60 disabled:text-muted-foreground disabled:cursor-not-allowed disabled:border-border/60',
        open ? 'ring-2 ring-ring border-primary/50' : '',
        error && 'border-destructive focus:ring-destructive/30',
        props.class
      )"
      @click="toggleOpen"
    >
      <Calendar :size="14" class="shrink-0 text-muted-foreground" />
      <span v-if="displayValue" class="text-foreground truncate">{{ displayValue }}</span>
      <span v-else class="text-muted-foreground truncate">{{ placeholderText }}</span>
    </button>
    <!-- Clear button -->
    <span
      v-if="modelValue && !disabled"
      class="absolute right-2 top-1/2 -translate-y-1/2 opacity-0 group-hover:opacity-100 transition-opacity cursor-pointer p-0.5 rounded-full hover:bg-muted"
      @click.stop="clearValue"
    >
      <X :size="14" class="text-muted-foreground" />
    </span>

    <!-- Panel -->
    <Teleport to="body">
      <div
        v-if="open"
        ref="panelRef"
        class="fixed -top-[9999px] -left-[9999px] z-[60] w-[280px] rounded-xl border border-border bg-card shadow-xl p-3"
      >
        <!-- Header -->
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
            <div
              v-for="wd in WEEKDAYS"
              :key="wd"
              class="h-8 flex items-center justify-center text-xs font-medium text-muted-foreground"
            >
              {{ wd }}
            </div>
          </div>

          <!-- Days grid -->
          <div class="grid grid-cols-7">
            <button
              v-for="(day, idx) in calendarDays"
              :key="idx"
              type="button"
              :disabled="!day.current"
              class="h-8 w-full inline-flex items-center justify-center text-sm rounded-lg transition-colors"
              :class="[
                !day.current
                  ? 'text-muted-foreground/30 cursor-default'
                  : isSelected(day.date)
                    ? 'bg-primary text-primary-foreground font-medium'
                    : isToday(day.date)
                      ? 'bg-accent/15 text-accent font-medium hover:bg-accent/25'
                      : 'text-foreground hover:bg-muted'
              ]"
              @click.stop="day.current && selectDay(day)"
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
            @click.stop="clearValue"
          >
            {{ t('common.button.clear') }}
          </button>
          <button
            type="button"
            class="text-xs text-primary hover:text-primary/80 transition-colors px-2 py-1 rounded-md hover:bg-primary/10 font-medium"
            @click.stop="picker === 'month' ? (goToday(), selectMonthView(nowJST().month())) : (goToday(), selectDay({ date: todayJSTStr(), current: true }))"
          >
            {{ t('common.datePicker.today') }}
          </button>
        </div>
      </div>
    </Teleport>
  </div>
</template>
