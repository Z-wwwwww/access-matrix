<script setup>
import { computed } from 'vue'
import { cn } from '@/lib/utils'
import Radio from './Radio.vue'

const props = defineProps({
  modelValue: {
    type: [String, Number, Boolean],
    default: ''
  },
  options: {
    type: Array,
    default: () => []
  },
  name: {
    type: String,
    default: ''
  },
  direction: {
    type: String,
    default: 'horizontal' // 'horizontal' | 'vertical'
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

const groupName = computed(() => props.name || `radio-${Math.random().toString(36).slice(2, 9)}`)

function onChange(v) {
  emit('update:modelValue', v)
  emit('change', v)
}
</script>

<template>
  <div
    :class="cn(
      'flex items-center',
      direction === 'vertical' ? 'flex-col items-start gap-2' : 'flex-wrap gap-4',
      props.class
    )"
  >
    <Radio
      v-for="opt in options"
      :key="opt.value"
      :model-value="modelValue"
      :value="opt.value"
      :label="opt.label"
      :name="groupName"
      :disabled="disabled || opt.disabled"
      :error="error"
      @update:model-value="onChange"
    />
  </div>
</template>
