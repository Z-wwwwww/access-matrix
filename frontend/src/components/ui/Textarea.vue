<script setup>
import { cn } from '@/lib/utils'

const props = defineProps({
  modelValue: {
    type: [String, Number],
    default: ''
  },
  placeholder: {
    type: String,
    default: ''
  },
  rows: {
    type: [String, Number],
    default: 3
  },
  maxlength: {
    type: [String, Number],
    default: undefined
  },
  disabled: {
    type: Boolean,
    default: false
  },
  error: {
    type: Boolean,
    default: false
  },
  resize: {
    type: String,
    default: 'none',
    validator: (v) => ['none', 'y', 'x', 'both'].includes(v)
  },
  class: {
    type: String,
    default: ''
  }
})

const emit = defineEmits(['update:modelValue', 'blur'])

const resizeClass = {
  none: 'resize-none',
  y: 'resize-y',
  x: 'resize-x',
  both: 'resize'
}

function onInput(e) {
  emit('update:modelValue', e.target.value)
}

function onBlur(e) {
  emit('blur', e)
}
</script>

<template>
  <textarea
    :value="modelValue"
    :placeholder="placeholder"
    :rows="rows"
    :maxlength="maxlength"
    :disabled="disabled"
    :class="cn(
      'w-full px-3 py-2 border border-input rounded-lg bg-card text-foreground text-sm transition',
      'placeholder:text-muted-foreground',
      'focus:outline-none focus:ring-2 focus:ring-ring focus:border-primary/50',
      'hover:border-primary/50',
      'disabled:bg-muted/60 disabled:text-muted-foreground disabled:cursor-not-allowed disabled:border-border/60',
      error && 'border-destructive focus:ring-destructive/30 focus:border-destructive',
      resizeClass[resize],
      props.class
    )"
    @input="onInput"
    @blur="onBlur"
  />
</template>
