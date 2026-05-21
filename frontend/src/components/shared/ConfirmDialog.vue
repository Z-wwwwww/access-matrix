<script setup>
import { computed } from 'vue'
import { useI18n } from 'vue-i18n'
import { AlertTriangle, HelpCircle } from 'lucide-vue-next'
import Dialog from '@/components/ui/Dialog.vue'
import { useConfirm } from '@/composables/useConfirm'

const { t } = useI18n()
const { state } = useConfirm()

const open = computed(() => !!state.value)
const isDestructive = computed(() => state.value?.variant === 'destructive')

function handleConfirm() {
  if (state.value?.resolve) state.value.resolve(true)
  state.value = null
}

function handleCancel() {
  if (state.value?.resolve) state.value.resolve(false)
  state.value = null
}

const confirmClass = computed(() => {
  const base = 'inline-flex items-center gap-1.5 h-9 px-4 rounded-lg text-sm font-medium transition-colors'
  const variant = isDestructive.value
    ? 'bg-destructive text-destructive-foreground hover:bg-destructive/90'
    : 'bg-primary text-primary-foreground hover:bg-primary/90'
  return `${base} ${variant}`
})
</script>

<template>
  <Dialog
    :open="open"
    :title="state?.title || t('common.button.confirm')"
    width="max-w-md"
    z-index="z-[60]"
    :close-on-overlay="false"
    @update:open="$event ? null : handleCancel()"
  >
    <template #header>
      <div class="flex items-start gap-3">
        <div
          :class="[
            'shrink-0 w-10 h-10 rounded-full flex items-center justify-center',
            isDestructive ? 'bg-destructive/10 text-destructive' : 'bg-primary/10 text-primary'
          ]"
        >
          <AlertTriangle v-if="isDestructive" :size="20" />
          <HelpCircle v-else :size="20" />
        </div>
        <div class="min-w-0 flex-1 pt-1">
          <h2 class="text-base font-semibold leading-6 text-foreground truncate">
            {{ state?.title || t('common.button.confirm') }}
          </h2>
        </div>
      </div>
    </template>

    <p class="text-sm text-foreground whitespace-pre-line leading-6">{{ state?.message }}</p>

    <template #footer>
      <button
        class="inline-flex items-center gap-1.5 h-9 px-4 rounded-lg text-sm font-medium bg-muted text-foreground hover:bg-muted/80 transition-colors"
        @click="handleCancel"
      >
        {{ state?.cancelText || t('common.button.cancel') }}
      </button>
      <button :class="confirmClass" @click="handleConfirm">
        {{ state?.confirmText || t('common.button.confirm') }}
      </button>
    </template>
  </Dialog>
</template>
