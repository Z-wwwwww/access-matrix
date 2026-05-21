<script setup>
import { computed } from 'vue'
import { cva } from 'class-variance-authority'
import { cn } from '@/lib/utils'

const badgeVariants = cva(
  'inline-flex items-center rounded-full border px-2.5 py-0.5 text-xs font-semibold transition-colors',
  {
    variants: {
      variant: {
        default: 'border-transparent bg-primary text-primary-foreground',
        secondary: 'border-transparent bg-slate-100 text-slate-600 dark:bg-slate-500/20 dark:text-slate-300',
        success: 'border-transparent bg-emerald-100 text-emerald-700 dark:bg-emerald-500/20 dark:text-emerald-300',
        warning: 'border-transparent bg-amber-100 text-amber-700 dark:bg-amber-500/20 dark:text-amber-300',
        danger: 'border-transparent bg-red-100 text-red-700 dark:bg-red-500/20 dark:text-red-300',
        info: 'border-transparent bg-blue-100 text-blue-700 dark:bg-blue-500/20 dark:text-blue-300',
        violet: 'border-transparent bg-violet-100 text-violet-700 dark:bg-violet-500/20 dark:text-violet-300',
        outline: 'text-foreground'
      }
    },
    defaultVariants: {
      variant: 'default'
    }
  }
)

const props = defineProps({
  variant: {
    type: String,
    default: 'default'
  },
  class: {
    type: String,
    default: ''
  }
})

const classes = computed(() =>
  cn(badgeVariants({ variant: props.variant }), props.class)
)
</script>

<template>
  <span :class="classes">
    <slot />
  </span>
</template>
