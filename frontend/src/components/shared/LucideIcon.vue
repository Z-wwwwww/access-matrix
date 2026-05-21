<script setup>
import { computed } from 'vue'
import { resolveIcon } from '@/utils/icon-registry'

const props = defineProps({
  name: {
    type: String,
    default: ''
  },
  size: {
    type: Number,
    default: 16
  }
})

const iconComponent = computed(() => resolveIcon(props.name))
</script>

<template>
  <component v-if="iconComponent" :is="iconComponent" :size="size" />
</template>
