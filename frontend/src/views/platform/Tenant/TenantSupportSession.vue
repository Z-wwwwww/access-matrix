<script setup>
import { ref, watch } from 'vue'
import { useI18n } from 'vue-i18n'
import Drawer from '@/components/ui/Drawer.vue'
import { LifeBuoy, AlertTriangle } from 'lucide-vue-next'

/**
 * Reason-capture dialog before entering a support session. The reason is
 * mandatory — it flows into core_oplog.request_body via the @OpLog aspect
 * on the impersonate-start endpoint, which is the primary audit
 * justification (the JWT's act claim records WHO; this records WHY).
 *
 * Stays scary-coloured on purpose. Even with audit, "I'm about to act
 * as a paying customer's super-admin" is a high-trust action; the UX
 * should reflect that.
 */
const { t } = useI18n()

const props = defineProps({
  row: { type: Object, default: null }
})
const emit = defineEmits(['close', 'start'])

const reason = ref('')
const isOpen = ref(false)
const submitting = ref(false)

watch(() => props.row, (row) => {
  if (row) {
    reason.value = ''
    submitting.value = false
    isOpen.value = true
  } else {
    isOpen.value = false
  }
}, { immediate: true })

function onClose(v) {
  if (!v) emit('close')
}

function submit() {
  const trimmed = reason.value.trim()
  if (!trimmed || trimmed.length < 5) {
    // Length floor stops drive-by "test" reasons from polluting audit.
    return
  }
  submitting.value = true
  emit('start', { row: props.row, reason: trimmed })
  // Parent owns success/fail messaging + closing the drawer; we just
  // disable re-clicks while it runs.
}
</script>

<template>
  <Drawer
    :open="isOpen"
    :title="t('platform.tenant.support.dialog.title')"
    width="max-w-md"
    @update:open="onClose"
  >
    <div v-if="row" class="space-y-4">
      <!-- Warning callout: high-trust action acknowledgement -->
      <div class="p-3 rounded-lg bg-destructive/10 border border-destructive/30 text-xs leading-relaxed">
        <div class="flex items-start gap-2">
          <AlertTriangle class="size-4 text-destructive shrink-0 mt-0.5" />
          <div class="text-foreground">
            <p class="font-medium mb-1">{{ t('platform.tenant.support.dialog.warning.title') }}</p>
            <p class="text-muted-foreground">
              {{ t('platform.tenant.support.dialog.warning.body', { displayName: row.displayName, tenantCode: row.tenantCode }) }}
            </p>
          </div>
        </div>
      </div>

      <div>
        <label class="text-xs text-muted-foreground block mb-1">
          {{ t('platform.tenant.support.dialog.reasonLabel') }} <span class="text-destructive">*</span>
        </label>
        <textarea v-model="reason" rows="3"
                  class="w-full px-3 py-2 text-sm rounded-md border border-border bg-background focus:outline-none focus:ring-2 focus:ring-primary/40 resize-none"
                  :placeholder="t('platform.tenant.support.dialog.reasonPlaceholder')"
                  :disabled="submitting" />
        <p class="text-[11px] text-muted-foreground mt-1">
          {{ t('platform.tenant.support.dialog.reasonHint') }}
        </p>
      </div>

      <div class="text-xs text-muted-foreground space-y-1.5 pl-1">
        <p>· {{ t('platform.tenant.support.dialog.ttlNote') }}</p>
        <p>· {{ t('platform.tenant.support.dialog.auditNote') }}</p>
        <p>· {{ t('platform.tenant.support.dialog.writeNote') }}</p>
      </div>
    </div>

    <template #footer>
      <div class="flex justify-end gap-2">
        <button class="h-9 px-3 rounded border border-border text-sm"
                @click="emit('close')">{{ t('common.button.cancel') }}</button>
        <button class="h-9 px-3 rounded bg-destructive text-destructive-foreground text-sm inline-flex items-center gap-1 disabled:opacity-50 disabled:cursor-not-allowed"
                :disabled="submitting || reason.trim().length < 5"
                @click="submit">
          <LifeBuoy class="size-4" />
          {{ submitting ? t('platform.tenant.support.dialog.starting') : t('platform.tenant.support.dialog.confirm') }}
        </button>
      </div>
    </template>
  </Drawer>
</template>
