<script setup>
import { ref, computed, watch, nextTick, onBeforeUnmount, inject } from 'vue'
import { useI18n } from 'vue-i18n'
import { cn } from '@/lib/utils'
import { toJSTDateTimeStr, toBackendDate, todayJSTStr, nowJST } from '@/lib/date'
import { Calendar, Clock, ChevronLeft, ChevronRight, X } from 'lucide-vue-next'
import { usePopupFollowTrigger, applyAbsolutePopupPosition } from '@/composables/usePopupFollowTrigger'

const { t, tm } = useI18n()

const props = defineProps({
  /** v-model value: 受け付けフォーマット YYYY-MM-DD HH:mm / YYYY-MM-DDTHH:mm / YYYY-MM-DD HH:mm:ssZZ 等 */
  modelValue: {
    type: String,
    default: ''
  },
  placeholder: {
    type: String,
    default: ''
  },
  /** 秒を表示するか */
  showSeconds: {
    type: Boolean,
    default: false
  },
  /**
   * 出力フォーマット:
   *   ''（デフォルト）                   — showSeconds に応じて 'YYYY-MM-DD HH:mm' or 'YYYY-MM-DD HH:mm:ss'
   *   'YYYY-MM-DD HH:mm:ssZZ'            — 後端要求のバックエンド形式（JST）
   *   その他任意文字列                   — 従来互換のため、その他は showSeconds ベース
   */
  valueFormat: {
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
  },
  class: {
    type: String,
    default: ''
  }
})

const emit = defineEmits(['update:modelValue', 'change'])

const open = ref(false)
const triggerRef = ref(null)
const panelRef = ref(null)

// ── Parse model value ──
// 先用 toJSTDateTimeStr 将任意输入转为 JST 的 'YYYY-MM-DD HH:mm'，再解析
function parseValue(val) {
  if (!val) return null
  // 含时区的字符串先转为 JST 字符串
  const normalized = /[+\-]\d{4}$|[+\-]\d{2}:\d{2}$|Z$/.test(val)
    ? toJSTDateTimeStr(val)
    : val.replace('T', ' ')
  if (!normalized) return null
  const m = normalized.match(/^(\d{4})-(\d{2})-(\d{2})(?:[ ](\d{2}):(\d{2})(?::(\d{2}))?)?/)
  if (!m) return null
  return {
    year: parseInt(m[1]),
    month: parseInt(m[2]) - 1,
    day: parseInt(m[3]),
    hour: m[4] ? parseInt(m[4]) : 0,
    minute: m[5] ? parseInt(m[5]) : 0,
    second: m[6] ? parseInt(m[6]) : 0
  }
}

function formatValue(parts) {
  if (!parts) return ''
  const y = parts.year
  const mo = String(parts.month + 1).padStart(2, '0')
  const d = String(parts.day).padStart(2, '0')
  const h = String(parts.hour).padStart(2, '0')
  const mi = String(parts.minute).padStart(2, '0')
  if (props.showSeconds) {
    const s = String(parts.second).padStart(2, '0')
    return `${y}-${mo}-${d} ${h}:${mi}:${s}`
  }
  return `${y}-${mo}-${d} ${h}:${mi}`
}

const parsed = computed(() => parseValue(props.modelValue))

const displayValue = computed(() => {
  const p = parsed.value
  if (!p) return ''
  const y = p.year
  const mo = String(p.month + 1).padStart(2, '0')
  const d = String(p.day).padStart(2, '0')
  const h = String(p.hour).padStart(2, '0')
  const mi = String(p.minute).padStart(2, '0')
  if (props.showSeconds) {
    const s = String(p.second).padStart(2, '0')
    return `${y}/${mo}/${d} ${h}:${mi}:${s}`
  }
  return `${y}/${mo}/${d} ${h}:${mi}`
})

// ── Calendar state ── (JST 基準で初期化、ブラウザ TZ 非依存)
const viewYear = ref(nowJST().year())
const viewMonth = ref(nowJST().month())
// Time picker state（独立 ref，编辑期间不立即 commit）
const pickHour = ref(0)
const pickMinute = ref(0)
const pickSecond = ref(0)
// Selected date during picking
const pickDate = ref('')

const WEEKDAYS = computed(() => tm('common.datePicker.weekdays'))
const MONTH_LABELS = computed(() => tm('common.datePicker.months'))
const placeholderText = computed(() => props.placeholder || t('common.datePicker.dateTimePlaceholder'))
/** 'date' | 'month' | 'year' */
const view = ref('date')

const yearRangeStart = computed(() => Math.floor(viewYear.value / 12) * 12)
const yearList = computed(() => Array.from({ length: 12 }, (_, i) => yearRangeStart.value + i))

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

const calendarDays = computed(() => {
  const y = viewYear.value
  const m = viewMonth.value
  const firstDay = new Date(y, m, 1)
  const lastDay = new Date(y, m + 1, 0)

  let startDow = firstDay.getDay() - 1
  if (startDow < 0) startDow = 6

  const days = []
  const prevLastDay = new Date(y, m, 0).getDate()
  for (let i = startDow - 1; i >= 0; i--) {
    days.push({ day: prevLastDay - i, current: false, date: null })
  }
  for (let d = 1; d <= lastDay.getDate(); d++) {
    const mm = String(m + 1).padStart(2, '0')
    const dd = String(d).padStart(2, '0')
    days.push({ day: d, current: true, date: `${y}-${mm}-${dd}` })
  }
  const remaining = 42 - days.length
  for (let i = 1; i <= remaining; i++) {
    days.push({ day: i, current: false, date: null })
  }
  return days
})

function isSelected(dateStr) {
  return dateStr && dateStr === pickDate.value
}

function isToday(dateStr) {
  return dateStr === todayJSTStr()
}

function selectDay(day) {
  if (!day.current || !day.date) return
  pickDate.value = day.date
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

// ── Time helpers ──
function clampNumber(val, min, max) {
  let n = parseInt(val) || 0
  if (n < min) n = min
  if (n > max) n = max
  return n
}

function onHourInput(e) {
  pickHour.value = clampNumber(e.target.value, 0, 23)
}
function onMinuteInput(e) {
  pickMinute.value = clampNumber(e.target.value, 0, 59)
}
function onSecondInput(e) {
  pickSecond.value = clampNumber(e.target.value, 0, 59)
}

function pad(n) {
  return String(n).padStart(2, '0')
}

// ── Time dropdown ──
const HOURS = Array.from({ length: 24 }, (_, i) => i)
const MINUTES = Array.from({ length: 60 }, (_, i) => i)
const SECONDS = Array.from({ length: 60 }, (_, i) => i)
const TIME_DROPDOWN_H = 160
/** 'hour' | 'minute' | 'second' | null */
const timeDropdown = ref(null)
/** 'top' | 'bottom' — 下拉位置 */
const timeDropdownPlacement = ref('bottom')
const hourListRef = ref(null)
const minuteListRef = ref(null)
const secondListRef = ref(null)

function toggleTimeDropdown(type, event) {
  timeDropdown.value = timeDropdown.value === type ? null : type
  if (timeDropdown.value) {
    // 检测可用空间，决定向上或向下展开
    const inputEl = event?.currentTarget
    if (inputEl) {
      const rect = inputEl.getBoundingClientRect()
      const spaceBelow = window.innerHeight - rect.bottom
      const spaceAbove = rect.top
      // 下方空间不够且上方空间更多 → 向上
      timeDropdownPlacement.value =
        spaceBelow < TIME_DROPDOWN_H + 8 && spaceAbove > spaceBelow ? 'top' : 'bottom'
    }
    nextTick(() => {
      const refMap = { hour: hourListRef, minute: minuteListRef, second: secondListRef }
      const valMap = { hour: pickHour.value, minute: pickMinute.value, second: pickSecond.value }
      const el = refMap[type].value
      if (el) {
        const item = el.querySelector(`[data-val="${valMap[type]}"]`)
        if (item) item.scrollIntoView({ block: 'center' })
      }
    })
  }
}

function pickTime(type, val) {
  if (type === 'hour') pickHour.value = val
  else if (type === 'minute') pickMinute.value = val
  else if (type === 'second') pickSecond.value = val
  timeDropdown.value = null
}

// ── Confirm / Cancel / Clear ──
function confirm() {
  if (!pickDate.value) return
  const [y, mo, d] = pickDate.value.split('-').map(Number)
  // 1. 先生成「分 or 秒」精度の従来フォーマット（showSeconds ベース）
  const defaultVal = formatValue({
    year: y,
    month: mo - 1,
    day: d,
    hour: pickHour.value,
    minute: pickMinute.value,
    second: pickSecond.value
  })
  // 2. valueFormat に応じて最終出力を決定
  let newVal = defaultVal
  if (props.valueFormat === 'YYYY-MM-DD HH:mm:ssZZ') {
    // バックエンド要求形式（JST + 秒 + オフセット付）
    newVal = toBackendDate(defaultVal)
  }
  emit('update:modelValue', newVal)
  emit('change', newVal)
  open.value = false
}

function clearValue() {
  emit('update:modelValue', '')
  emit('change', '')
  open.value = false
}

function setNow() {
  const j = nowJST()
  viewYear.value = j.year()
  viewMonth.value = j.month()
  pickDate.value = j.format('YYYY-MM-DD')
  pickHour.value = j.hour()
  pickMinute.value = j.minute()
  pickSecond.value = j.second()
}

const injectedPopupZ = inject('popupZIndex', null)

// ── Open/Close ──
function updatePosition() {
  if (!triggerRef.value || !panelRef.value) return
  applyAbsolutePopupPosition(panelRef.value, triggerRef.value.getBoundingClientRect(), {
    assumedHeight: 380,
    zIndex: injectedPopupZ?.value,
    triggerEl: triggerRef.value
  })
}

async function toggleOpen() {
  if (props.disabled) return
  open.value = !open.value
  if (open.value) {
    view.value = 'date'
    // Initialize picker state from current value
    const p = parsed.value
    if (p) {
      viewYear.value = p.year
      viewMonth.value = p.month
      pickDate.value = `${p.year}-${pad(p.month + 1)}-${pad(p.day)}`
      pickHour.value = p.hour
      pickMinute.value = p.minute
      pickSecond.value = p.second
    } else {
      const j = nowJST()
      viewYear.value = j.year()
      viewMonth.value = j.month()
      pickDate.value = ''
      pickHour.value = 0
      pickMinute.value = 0
      pickSecond.value = 0
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

watch(open, (val) => {
  if (!val) {
    document.removeEventListener('mousedown', onDocClick, true)
    timeDropdown.value = null
  }
})

// Popup を開いている間は trigger の位置に追従 (scroll / resize 時に再配置)
usePopupFollowTrigger(open, triggerRef, updatePosition)

onBeforeUnmount(() => {
  document.removeEventListener('mousedown', onDocClick, true)
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
    <!-- Clear -->
    <span
      v-if="modelValue && !disabled"
      class="absolute right-2 top-1/2 -translate-y-1/2 opacity-0 group-hover:opacity-100 transition-opacity cursor-pointer p-0.5 rounded-full hover:bg-muted z-10"
      @click.stop="clearValue"
    >
      <X :size="14" class="text-muted-foreground" />
    </span>

    <!-- Panel -->
    <Teleport to="body">
      <div
        v-if="open"
        ref="panelRef"
        class="fixed -top-[9999px] -left-[9999px] z-[60] w-[300px] rounded-xl border border-border bg-card shadow-xl p-3"
      >
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

        <!-- Time picker -->
        <div class="flex items-center gap-2 mt-3 pt-3 border-t border-border">
          <Clock :size="14" class="shrink-0 text-muted-foreground" />
          <div class="flex items-center gap-1 text-sm">
            <!-- Hour -->
            <div class="relative">
              <input
                type="text"
                :value="pad(pickHour)"
                class="w-12 h-8 px-1 text-center border border-input rounded-md bg-card text-foreground focus:outline-none focus:ring-2 focus:ring-ring cursor-pointer"
                @input="onHourInput"
                @click.stop="toggleTimeDropdown('hour', $event)"
              />
              <div
                v-if="timeDropdown === 'hour'"
                ref="hourListRef"
                class="absolute left-0 w-12 max-h-[160px] overflow-y-auto rounded-md border border-border bg-card shadow-lg py-1 z-20"
                :class="timeDropdownPlacement === 'top' ? 'bottom-full mb-1' : 'top-full mt-1'"
              >
                <button
                  v-for="h in HOURS"
                  :key="h"
                  :data-val="h"
                  type="button"
                  class="block w-full px-1 py-1 text-center text-sm transition-colors"
                  :class="pickHour === h ? 'bg-primary/10 text-primary font-medium' : 'text-foreground hover:bg-muted'"
                  @click.stop="pickTime('hour', h)"
                >
                  {{ pad(h) }}
                </button>
              </div>
            </div>
            <span class="text-muted-foreground">:</span>
            <!-- Minute -->
            <div class="relative">
              <input
                type="text"
                :value="pad(pickMinute)"
                class="w-12 h-8 px-1 text-center border border-input rounded-md bg-card text-foreground focus:outline-none focus:ring-2 focus:ring-ring cursor-pointer"
                @input="onMinuteInput"
                @click.stop="toggleTimeDropdown('minute', $event)"
              />
              <div
                v-if="timeDropdown === 'minute'"
                ref="minuteListRef"
                class="absolute left-0 w-12 max-h-[160px] overflow-y-auto rounded-md border border-border bg-card shadow-lg py-1 z-20"
                :class="timeDropdownPlacement === 'top' ? 'bottom-full mb-1' : 'top-full mt-1'"
              >
                <button
                  v-for="m in MINUTES"
                  :key="m"
                  :data-val="m"
                  type="button"
                  class="block w-full px-1 py-1 text-center text-sm transition-colors"
                  :class="pickMinute === m ? 'bg-primary/10 text-primary font-medium' : 'text-foreground hover:bg-muted'"
                  @click.stop="pickTime('minute', m)"
                >
                  {{ pad(m) }}
                </button>
              </div>
            </div>
            <!-- Second -->
            <template v-if="showSeconds">
              <span class="text-muted-foreground">:</span>
              <div class="relative">
                <input
                  type="text"
                  :value="pad(pickSecond)"
                  class="w-12 h-8 px-1 text-center border border-input rounded-md bg-card text-foreground focus:outline-none focus:ring-2 focus:ring-ring cursor-pointer"
                  @input="onSecondInput"
                  @click.stop="toggleTimeDropdown('second', $event)"
                />
                <div
                  v-if="timeDropdown === 'second'"
                  ref="secondListRef"
                  class="absolute left-0 w-12 max-h-[160px] overflow-y-auto rounded-md border border-border bg-card shadow-lg py-1 z-20"
                  :class="timeDropdownPlacement === 'top' ? 'bottom-full mb-1' : 'top-full mt-1'"
                >
                  <button
                    v-for="s in SECONDS"
                    :key="s"
                    :data-val="s"
                    type="button"
                    class="block w-full px-1 py-1 text-center text-sm transition-colors"
                    :class="pickSecond === s ? 'bg-primary/10 text-primary font-medium' : 'text-foreground hover:bg-muted'"
                    @click.stop="pickTime('second', s)"
                  >
                    {{ pad(s) }}
                  </button>
                </div>
              </div>
            </template>
          </div>
          <button
            type="button"
            class="ml-auto text-xs text-primary hover:text-primary/80 transition-colors px-2 py-1 rounded-md hover:bg-primary/10 font-medium"
            @click.stop="setNow"
          >
            {{ t('common.datePicker.now') }}
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
            :disabled="!pickDate"
            class="text-xs font-medium px-3 py-1 rounded-md bg-primary text-primary-foreground hover:bg-primary/90 transition-colors disabled:opacity-40 disabled:cursor-not-allowed"
            @click.stop="confirm"
          >
            {{ t('common.datePicker.confirm') }}
          </button>
        </div>
      </div>
    </Teleport>
  </div>
</template>
