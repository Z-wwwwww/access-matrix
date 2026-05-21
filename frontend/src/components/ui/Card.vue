<script setup>
import { computed } from 'vue'
import { cn } from '@/lib/utils'

const props = defineProps({
  class: {
    type: String,
    default: ''
  }
})

const classes = computed(() =>
  cn('rounded-xl border border-border bg-card text-card-foreground shadow-lg shadow-black/[0.06]', props.class)
)
</script>

<template>
  <div :class="classes">
    <slot />
  </div>
</template>
