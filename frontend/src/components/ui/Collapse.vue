<script setup>
import { ref, computed, watch } from 'vue'
import { ChevronDown } from 'lucide-vue-next'
import { cn } from '@/lib/utils'

defineOptions({ name: 'Collapse' })

const props = defineProps({
  modelValue: {
    type: Boolean,
    default: null
  },
  defaultOpen: {
    type: Boolean,
    default: true
  },
  title: {
    type: String,
    default: ''
  },
  variant: {
    type: String,
    default: 'card'
  },
  headerClass: {
    type: String,
    default: ''
  },
  bodyClass: {
    type: String,
    default: ''
  },
  disabled: {
    type: Boolean,
    default: false
  }
})

const emit = defineEmits(['update:modelValue'])

const controlled = computed(() => props.modelValue !== null)
const innerOpen = ref(props.defaultOpen)
watch(() => props.modelValue, (v) => { if (v !== null) innerOpen.value = v })

const isOpen = computed(() => controlled.value ? props.modelValue : innerOpen.value)

function toggle() {
  if (props.disabled) return
  const next = !isOpen.value
  if (!controlled.value) innerOpen.value = next
  emit('update:modelValue', next)
}

const rootCls = computed(() => {
  return {
    card: 'rounded-lg border border-border bg-card shadow-sm',
    bordered: 'rounded-lg border border-border',
    plain: ''
  }[props.variant] || 'rounded-lg border border-border bg-card shadow-sm'
})
</script>

<template>
  <div :class="cn(rootCls)">
    <button
      type="button"
      :disabled="disabled"
      :aria-expanded="isOpen"
      :class="
        cn(
          'flex items-center justify-between w-full px-4 py-3 text-left text-sm font-semibold text-foreground hover:bg-muted/40 transition-colors',
          isOpen && 'border-b border-border',
          disabled && 'cursor-not-allowed opacity-60',
          headerClass
        )
      "
      @click="toggle"
    >
      <span class="flex items-center gap-2 min-w-0">
        <slot name="title">{{ title }}</slot>
      </span>
      <span class="flex items-center gap-2 shrink-0">
        <slot name="extra" />
        <ChevronDown
          :size="16"
          class="text-muted-foreground transition-transform duration-200"
          :class="{ 'rotate-180': isOpen }"
        />
      </span>
    </button>
    <div v-show="isOpen" :class="cn('px-4 py-4', bodyClass)">
      <slot />
    </div>
  </div>
</template>
