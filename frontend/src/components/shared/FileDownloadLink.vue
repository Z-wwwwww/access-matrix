<script setup>
import { computed } from 'vue'
import { FileArchive, Download } from 'lucide-vue-next'

defineOptions({ name: 'FileDownloadLink' })

const props = defineProps({
  fileName: { type: String, default: '' },
  valid: { type: Boolean, default: true }
})

const emit = defineEmits(['download'])

const tooltip = computed(() => props.fileName || '')

function handleClick() {
  if (!props.valid || !props.fileName) return
  emit('download')
}
</script>

<template>
  <button
    v-if="valid && fileName"
    type="button"
    class="group inline-flex items-start gap-2 max-w-full px-2.5 py-1.5 rounded-md border border-border bg-card hover:bg-primary/5 hover:border-primary/40 transition-colors text-left"
    :title="tooltip"
    @click="handleClick"
  >
    <span class="flex h-7 w-7 shrink-0 items-center justify-center rounded-md bg-primary/10 text-primary">
      <FileArchive :size="14" />
    </span>
    <span class="flex-1 min-w-0 text-sm text-primary break-all leading-snug">{{ fileName }}</span>
    <Download :size="14" class="shrink-0 mt-1 text-muted-foreground group-hover:text-primary transition-colors" />
  </button>
  <span
    v-else-if="fileName"
    class="inline-flex items-start gap-2 max-w-full px-2.5 py-1.5 rounded-md border border-border bg-muted/30 text-left"
    :title="tooltip"
  >
    <span class="flex h-7 w-7 shrink-0 items-center justify-center rounded-md bg-muted text-muted-foreground">
      <FileArchive :size="14" />
    </span>
    <span class="flex-1 min-w-0 text-sm text-muted-foreground break-all leading-snug">{{ fileName }}</span>
  </span>
</template>
