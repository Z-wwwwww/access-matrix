<script setup>
import { ref, computed, watch, nextTick, onBeforeUnmount, inject } from 'vue'
import { useI18n } from 'vue-i18n'
import { cn } from '@/lib/utils'
import { Clock, X } from 'lucide-vue-next'
import { nowJST } from '@/lib/date'
import { usePopupFollowTrigger, applyAbsolutePopupPosition } from '@/composables/usePopupFollowTrigger'

const { t } = useI18n()

const props = defineProps({
  /** v-model value: "HH:mm" or "HH:mm:ss" */
  modelValue: {
    type: String,
    default: ''
  },
  placeholder: {
    type: String,
    default: ''
  },
  showSeconds: {
    type: Boolean,
    default: false
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

// ── Parse / Format ──
function parseValue(val) {
  if (!val) return null
  const m = val.match(/^(\d{1,2}):(\d{1,2})(?::(\d{1,2}))?/)
  if (!m) return null
  return {
    hour: parseInt(m[1]),
    minute: parseInt(m[2]),
    second: m[3] ? parseInt(m[3]) : 0
  }
}

function pad(n) {
  return String(n).padStart(2, '0')
}

const parsed = computed(() => parseValue(props.modelValue))
const placeholderText = computed(() => props.placeholder || t('common.datePicker.timePlaceholder'))

const displayValue = computed(() => {
  const p = parsed.value
  if (!p) return ''
  if (props.showSeconds) {
    return `${pad(p.hour)}:${pad(p.minute)}:${pad(p.second)}`
  }
  return `${pad(p.hour)}:${pad(p.minute)}`
})

// ── Editing state ──
const pickHour = ref(0)
const pickMinute = ref(0)
const pickSecond = ref(0)

function syncFromValue() {
  const p = parsed.value
  if (p) {
    pickHour.value = p.hour
    pickMinute.value = p.minute
    pickSecond.value = p.second
  } else {
    pickHour.value = 0
    pickMinute.value = 0
    pickSecond.value = 0
  }
}

// 立即提交（点击下拉项后）
function commit() {
  const newVal = props.showSeconds
    ? `${pad(pickHour.value)}:${pad(pickMinute.value)}:${pad(pickSecond.value)}`
    : `${pad(pickHour.value)}:${pad(pickMinute.value)}`
  emit('update:modelValue', newVal)
  emit('change', newVal)
}

function clampNumber(val, min, max) {
  let n = parseInt(val) || 0
  if (n < min) n = min
  if (n > max) n = max
  return n
}

function onHourInput(e) {
  pickHour.value = clampNumber(e.target.value, 0, 23)
  commit()
}
function onMinuteInput(e) {
  pickMinute.value = clampNumber(e.target.value, 0, 59)
  commit()
}
function onSecondInput(e) {
  pickSecond.value = clampNumber(e.target.value, 0, 59)
  commit()
}

function clearValue() {
  emit('update:modelValue', '')
  emit('change', '')
  open.value = false
}

function setNow() {
  const j = nowJST()
  pickHour.value = j.hour()
  pickMinute.value = j.minute()
  pickSecond.value = j.second()
  commit()
  open.value = false
}

// ── Time dropdowns ──
const HOURS = Array.from({ length: 24 }, (_, i) => i)
const MINUTES = Array.from({ length: 60 }, (_, i) => i)
const SECONDS = Array.from({ length: 60 }, (_, i) => i)
const TIME_DROPDOWN_H = 160
const timeDropdown = ref(null)
const timeDropdownPlacement = ref('bottom')
const hourListRef = ref(null)
const minuteListRef = ref(null)
const secondListRef = ref(null)

function toggleTimeDropdown(type, event) {
  timeDropdown.value = timeDropdown.value === type ? null : type
  if (timeDropdown.value) {
    const inputEl = event?.currentTarget
    if (inputEl) {
      const rect = inputEl.getBoundingClientRect()
      const spaceBelow = window.innerHeight - rect.bottom
      const spaceAbove = rect.top
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
  commit()
  timeDropdown.value = null
}

const injectedPopupZ = inject('popupZIndex', null)

// ── Open/Close ──
function updatePosition() {
  if (!triggerRef.value || !panelRef.value) return
  // TimePicker 主面板估算高度（time inputs + footer ≈ 100px）
  applyAbsolutePopupPosition(panelRef.value, triggerRef.value.getBoundingClientRect(), {
    assumedHeight: 120,
    zIndex: injectedPopupZ?.value,
    triggerEl: triggerRef.value
  })
}

async function toggleOpen() {
  if (props.disabled) return
  open.value = !open.value
  if (open.value) {
    syncFromValue()
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
        'flex items-center gap-2 w-full h-9 px-3 pr-8 border border-input rounded-lg bg-background text-sm transition cursor-pointer',
        'focus:outline-none focus:ring-2 focus:ring-ring',
        'hover:border-primary/50',
        'disabled:opacity-50 disabled:cursor-not-allowed',
        open ? 'ring-2 ring-ring border-primary/50' : '',
        error && 'border-destructive focus:ring-destructive/30',
        props.class
      )"
      @click="toggleOpen"
    >
      <Clock :size="14" class="shrink-0 text-muted-foreground" />
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
        class="fixed -top-[9999px] -left-[9999px] z-[60] rounded-xl border border-border bg-card shadow-xl p-3"
      >
        <!-- Time inputs -->
        <div class="flex items-center gap-2">
          <Clock :size="14" class="shrink-0 text-muted-foreground" />
          <div class="flex items-center gap-1 text-sm">
            <!-- Hour -->
            <div class="relative">
              <input
                type="text"
                :value="pad(pickHour)"
                class="w-12 h-8 px-1 text-center border border-input rounded-md bg-background text-foreground focus:outline-none focus:ring-2 focus:ring-ring cursor-pointer"
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
                class="w-12 h-8 px-1 text-center border border-input rounded-md bg-background text-foreground focus:outline-none focus:ring-2 focus:ring-ring cursor-pointer"
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
                  class="w-12 h-8 px-1 text-center border border-input rounded-md bg-background text-foreground focus:outline-none focus:ring-2 focus:ring-ring cursor-pointer"
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
            @click.stop="setNow"
          >
            {{ t('common.datePicker.now') }}
          </button>
        </div>
      </div>
    </Teleport>
  </div>
</template>
