<script setup>
import { computed } from 'vue'
import Select from '@/components/ui/Select.vue'
import { useDict } from '@/composables/useDict'

defineOptions({ name: 'DictPicker' })

/**
 * Dictionary-driven dropdown. Resolves options from the project's static
 * dictionary store (`@/dict/storage`) via the {@link useDict} composable —
 * dict data is bundled at build time rather than fetched over HTTP, so this
 * picker is HTTP-free. {@code exclusion} hides specific values from the list.
 */
const props = defineProps({
  modelValue: {
    type: [String, Number],
    default: ''
  },
  /** Dict code as defined in src/dict/storage.js (e.g. "STATUS", "GENDER"). */
  dictCode: {
    type: String,
    required: true
  },
  /** Hide these values from the dropdown (string-compared). */
  exclusion: {
    type: Array,
    default: null
  },
  placeholder: {
    type: String,
    default: ''
  },
  disabled: {
    type: Boolean,
    default: false
  },
  allowClear: {
    type: Boolean,
    default: true
  },
  error: {
    type: Boolean,
    default: false
  }
})

const emit = defineEmits(['update:modelValue'])

// useDict is keyed at the call-site; dynamic dictCode swap is rare for a
// picker (its semantics would change underneath the user) so we resolve once.
const { options: rawOptions } = useDict(props.dictCode)

const options = computed(() => {
  const list = rawOptions.value || []
  if (!props.exclusion || props.exclusion.length === 0) return list
  const excSet = new Set(props.exclusion.map(String))
  return list.filter((o) => !excSet.has(String(o.value)))
})

function onChange(val) {
  emit('update:modelValue', val)
}
</script>

<template>
  <Select
    :model-value="modelValue"
    :options="options"
    :placeholder="placeholder"
    :disabled="disabled"
    :clearable="allowClear"
    :error="error"
    @update:model-value="onChange"
  />
</template>
