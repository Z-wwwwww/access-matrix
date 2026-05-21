<script setup>
import { computed } from 'vue'
import { cn } from '@/lib/utils'

const props = defineProps({
  modelValue: {
    type: [Boolean, Number, String],
    default: false
  },
  checkedValue: {
    type: [Boolean, Number, String],
    default: true
  },
  uncheckedValue: {
    type: [Boolean, Number, String],
    default: false
  },
  disabled: {
    type: Boolean,
    default: false
  },
  class: {
    type: String,
    default: ''
  }
})

const emit = defineEmits(['update:modelValue', 'change'])

const isChecked = computed(() => String(props.modelValue) === String(props.checkedValue))

function toggle() {
  if (props.disabled) return
  const newVal = isChecked.value ? props.uncheckedValue : props.checkedValue
  emit('update:modelValue', newVal)
  emit('change', newVal)
}
</script>

<template>
  <button
    type="button"
    role="switch"
    :aria-checked="isChecked"
    :disabled="disabled"
    :class="cn(
      'relative inline-flex h-5 w-9 shrink-0 cursor-pointer items-center rounded-full border-2 border-transparent transition-colors focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-ring focus-visible:ring-offset-2 focus-visible:ring-offset-background',
      isChecked ? 'bg-primary' : 'bg-input',
      disabled && 'opacity-50 cursor-not-allowed',
      props.class
    )"
    @click="toggle"
  >
    <span
      :class="cn(
        'pointer-events-none block h-4 w-4 rounded-full bg-background shadow-lg ring-0 transition-transform',
        isChecked ? 'translate-x-4' : 'translate-x-0'
      )"
    />
  </button>
</template>
