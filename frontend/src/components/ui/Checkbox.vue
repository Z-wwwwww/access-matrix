<script setup>
import { computed } from 'vue'
import { cn } from '@/lib/utils'
import { Check, Minus } from 'lucide-vue-next'

const props = defineProps({
  modelValue: {
    type: [Boolean, Array, Number, String],
    default: false
  },
  value: {
    type: [String, Number, Boolean],
    default: undefined
  },
  label: {
    type: String,
    default: ''
  },
  disabled: {
    type: Boolean,
    default: false
  },
  indeterminate: {
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

const isArrayMode = computed(() => Array.isArray(props.modelValue))

const isChecked = computed(() => {
  if (isArrayMode.value) {
    return props.modelValue.some((v) => String(v) === String(props.value))
  }
  return Boolean(props.modelValue)
})

function toggle() {
  if (props.disabled) return
  if (isArrayMode.value) {
    const next = isChecked.value
      ? props.modelValue.filter((v) => String(v) !== String(props.value))
      : [...props.modelValue, props.value]
    emit('update:modelValue', next)
    emit('change', next)
  } else {
    const next = !isChecked.value
    emit('update:modelValue', next)
    emit('change', next)
  }
}
</script>

<template>
  <span
    role="checkbox"
    :aria-checked="indeterminate ? 'mixed' : isChecked"
    :aria-disabled="disabled"
    :tabindex="disabled ? -1 : 0"
    :class="cn(
      'inline-flex items-center gap-1.5 text-sm leading-none text-foreground select-none align-middle',
      disabled ? 'cursor-not-allowed opacity-50' : 'cursor-pointer',
      props.class
    )"
    @click.stop="toggle"
    @keydown.space.prevent="toggle"
  >
    <span
      :class="cn(
        'inline-flex items-center justify-center w-[18px] h-[18px] rounded-[3px] border-[1.5px] transition-colors shrink-0',
        isChecked || indeterminate
          ? 'bg-primary border-primary text-primary-foreground'
          : 'bg-card border-input hover:border-primary/50',
        error && 'border-destructive',
        disabled && 'pointer-events-none'
      )"
    >
      <Minus v-if="indeterminate" :size="14" :stroke-width="3" />
      <Check v-else-if="isChecked" :size="14" :stroke-width="3" />
    </span>
    <slot>{{ label }}</slot>
  </span>
</template>
