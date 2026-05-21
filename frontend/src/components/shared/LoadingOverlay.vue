<script setup>
import { useI18n } from 'vue-i18n'
import { Loader2 } from 'lucide-vue-next'

defineProps({
  visible: { type: Boolean, default: false },
  message: { type: String, default: '' },
})

const { t } = useI18n()
</script>

<template>
  <Transition
    enter-active-class="transition-opacity duration-200"
    leave-active-class="transition-opacity duration-150"
    enter-from-class="opacity-0"
    leave-to-class="opacity-0"
  >
    <div
      v-if="visible"
      class="fixed inset-0 z-50 flex items-center justify-center bg-foreground/20 backdrop-blur-md"
    >
      <div class="flex flex-col items-center gap-3 px-8 py-6 rounded-2xl bg-card border border-border shadow-2xl min-w-[180px]">
        <div class="relative flex items-center justify-center w-12 h-12">
          <div class="absolute inset-0 rounded-full border-2 border-primary/15"></div>
          <Loader2 :size="32" class="animate-spin text-primary" />
        </div>
        <span class="text-sm font-medium text-foreground">{{ message || t('common.message.loading') }}</span>
      </div>
    </div>
  </Transition>
</template>
