<script setup>
import { HelpCircle } from 'lucide-vue-next'
import { cn } from '@/lib/utils'
import Switch from '@/components/ui/Switch.vue'

const props = defineProps({
  modelValue: { type: [Boolean, Number, String], default: false },
  checkedValue: { type: [Boolean, Number, String], default: true },
  uncheckedValue: { type: [Boolean, Number, String], default: false },
  label: { type: String, default: '' },
  description: { type: String, default: '' },
  helpText: { type: String, default: '' },
  disabledBadge: { type: String, default: '' },
  inline: { type: Boolean, default: false },
  disabled: { type: Boolean, default: false },
  class: { type: String, default: '' }
})

const emit = defineEmits(['update:modelValue', 'change'])

function onUpdate(v) { emit('update:modelValue', v) }
function onChange(v) { emit('change', v) }
</script>

<template>
  <label
    v-if="inline"
    :class="cn(
      'inline-flex items-center gap-1.5 text-[10px] font-medium shrink-0',
      disabled ? 'text-muted-foreground/40 cursor-not-allowed' : 'text-muted-foreground cursor-pointer',
      props.class
    )"
    :title="description || undefined"
  >
    <span v-if="label">{{ label }}</span>
    <span
      v-if="disabled && disabledBadge"
      class="text-[9px] px-1 py-px rounded bg-amber-100 text-amber-700 dark:bg-amber-500/15 dark:text-amber-400 leading-none shrink-0"
    >{{ disabledBadge }}</span>
    <span
      v-if="helpText"
      class="inline-flex shrink-0 cursor-help"
      :title="helpText"
      @click.prevent.stop
    >
      <HelpCircle :size="11" class="text-muted-foreground/60 hover:text-muted-foreground pointer-events-none" />
    </span>
    <Switch
      :model-value="modelValue"
      :checked-value="checkedValue"
      :unchecked-value="uncheckedValue"
      :disabled="disabled"
      @update:model-value="onUpdate"
      @change="onChange"
    />
  </label>
  <div v-else :class="cn('', props.class)">
    <label
      v-if="label"
      class="block text-sm font-medium text-foreground mb-1.5"
      :title="description || undefined"
    >
      {{ label }}
      <span
        v-if="helpText"
        class="ml-1 inline-flex align-middle cursor-help"
        :title="helpText"
      >
        <HelpCircle :size="14" class="text-muted-foreground/60 hover:text-muted-foreground" />
      </span>
    </label>
    <Switch
      :model-value="modelValue"
      :checked-value="checkedValue"
      :unchecked-value="uncheckedValue"
      :disabled="disabled"
      @update:model-value="onUpdate"
      @change="onChange"
    />
  </div>
</template>
