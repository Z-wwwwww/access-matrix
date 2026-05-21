<script setup>
import { computed } from 'vue'
import { cn } from '@/lib/utils'

const props = defineProps({
  modelValue: {
    type: [String, Number, Boolean],
    default: ''
  },
  value: {
    type: [String, Number, Boolean],
    required: true
  },
  label: {
    type: String,
    default: ''
  },
  name: {
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

const isChecked = computed(() => String(props.modelValue) === String(props.value))

function select() {
  if (props.disabled || isChecked.value) return
  emit('update:modelValue', props.value)
  emit('change', props.value)
}
</script>

<template>
  <label
    :class="cn(
      'inline-flex items-center gap-1.5 text-sm leading-none text-foreground select-none align-middle',
      disabled ? 'cursor-not-allowed opacity-50' : 'cursor-pointer',
      props.class
    )"
  >
    <span
      :class="cn(
        'inline-flex items-center justify-center w-4 h-4 rounded-full border transition-colors shrink-0',
        isChecked
          ? 'border-primary border-[5px] bg-card'
          : 'bg-card border-input hover:border-primary/50',
        error && 'border-destructive',
        disabled && 'pointer-events-none'
      )"
    />
    <input
      type="radio"
      class="sr-only"
      :name="name"
      :checked="isChecked"
      :disabled="disabled"
      @change="select"
    />
    <slot>{{ label }}</slot>
  </label>
</template>
