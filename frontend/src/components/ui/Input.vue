<script setup>
import { computed, ref } from 'vue'
import { cn } from '@/lib/utils'
import { X, Eye, EyeOff } from 'lucide-vue-next'

const props = defineProps({
  modelValue: {
    type: [String, Number],
    default: ''
  },
  placeholder: {
    type: String,
    default: ''
  },
  type: {
    type: String,
    default: 'text'
  },
  /**
   * 表示フォーマット:
   *   ''       (デフォルト) — そのまま表示
   *   'money'  — 千分位カンマ表示。フォーカス時は素数字で編集、ブラー時に整形。
   *              v-model は常に Number（または空文字列）を保持するため、保存時は変換不要。
   */
  format: {
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

const emit = defineEmits(['update:modelValue', 'keyup', 'blur'])

const hasValue = computed(() => props.modelValue !== '' && props.modelValue != null)

const focused = ref(false)
const showPassword = ref(false)
const isPassword = computed(() => props.type === 'password')

// money モード: blur 中は千分位整形、focus 中は素数字。それ以外は modelValue そのまま。
const displayValue = computed(() => {
  if (props.format !== 'money') return props.modelValue ?? ''
  if (focused.value) return props.modelValue ?? ''
  const v = props.modelValue
  if (v === '' || v === null || v === undefined) return ''
  const num = Number(v)
  if (Number.isNaN(num)) return v
  return num.toLocaleString('ja-JP')
})

// money 入力時のタイプ属性: ブラウザの type="number" は 1,000 を弾くので
// money モード時は text にして自前パース、password モード時は表示切替対応、それ以外は props.type を素通し
const inputType = computed(() => {
  if (props.format === 'money') return 'text'
  if (isPassword.value) return showPassword.value ? 'text' : 'password'
  return props.type
})

function onInput(e) {
  if (props.format === 'money') {
    // 数字・小数点・マイナス以外を除去（カンマや全角混入も自動で剥がす）
    const raw = e.target.value.replace(/[^\d.-]/g, '')
    if (raw === '' || raw === '-' || raw === '.') {
      emit('update:modelValue', '')
      return
    }
    const n = Number(raw)
    emit('update:modelValue', Number.isNaN(n) ? '' : n)
  } else {
    emit('update:modelValue', e.target.value)
  }
}

function onFocus() {
  focused.value = true
}

function onBlur(e) {
  focused.value = false
  emit('blur', e)
}

function clear() {
  emit('update:modelValue', '')
}
</script>

<template>
  <div class="group relative inline-flex w-full">
    <input
      :type="inputType"
      :value="displayValue"
      :placeholder="placeholder"
      :disabled="disabled"
      :class="cn(
        'w-full h-9 px-3 pr-8 border border-input rounded-lg bg-card text-foreground text-sm transition',
        'placeholder:text-muted-foreground',
        'focus:outline-none focus:ring-2 focus:ring-ring focus:border-primary/50',
        'hover:border-primary/50',
        'disabled:bg-muted/60 disabled:text-muted-foreground disabled:cursor-not-allowed disabled:border-border/60',
        format === 'money' && 'text-right',
        error && 'border-destructive focus:ring-destructive/30 focus:border-destructive',
        props.class
      )"
      @input="onInput"
      @focus="onFocus"
      @blur="onBlur"
      @keyup="$emit('keyup', $event)"
    />
    <span
      v-if="isPassword && !disabled"
      class="absolute right-2 top-1/2 -translate-y-1/2 cursor-pointer p-0.5 rounded-full hover:bg-muted text-muted-foreground hover:text-foreground transition-colors"
      :title="showPassword ? 'Hide' : 'Show'"
      @click.stop="showPassword = !showPassword"
    >
      <EyeOff v-if="showPassword" :size="14" />
      <Eye v-else :size="14" />
    </span>
    <span
      v-else-if="hasValue && !disabled"
      class="absolute right-2 top-1/2 -translate-y-1/2 opacity-0 group-hover:opacity-100 transition-opacity cursor-pointer p-0.5 rounded-full hover:bg-muted"
      @click.stop="clear"
    >
      <X :size="14" class="text-muted-foreground" />
    </span>
  </div>
</template>
