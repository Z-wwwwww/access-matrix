<script setup>
import { ref, watch } from 'vue'
import { useI18n } from 'vue-i18n'
import Drawer from '@/components/ui/Drawer.vue'
import { Send } from 'lucide-vue-next'

/**
 * Resend the tenant admin's onboarding invite — for a missed email or a
 * wrong address.
 *
 * The email field is OPTIONAL. It prefills with the tenant's current contact
 * email; leaving it as-is re-sends to that address (the "didn't arrive" case),
 * while editing it corrects the admin's address everywhere (DB + Keycloak +
 * tenant contact) before re-sending (the "wrong address" case). Sending an
 * empty value means "re-send to the current address".
 */
const { t } = useI18n()

const props = defineProps({
  row: { type: Object, default: null }
})
const emit = defineEmits(['close', 'resend'])

const email = ref('')
const original = ref('')
const isOpen = ref(false)
const submitting = ref(false)

watch(() => props.row, (row) => {
  if (row) {
    original.value = row.contactEmail || ''
    email.value = original.value
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
  submitting.value = true
  // Only send `email` when the operator actually changed it — otherwise pass
  // null so the backend re-sends to the current address without a needless
  // "correct email" write path.
  const trimmed = email.value.trim()
  const corrected = (trimmed && trimmed !== original.value) ? trimmed : null
  emit('resend', { row: props.row, email: corrected })
  // Parent owns success/fail messaging + closing the drawer.
}
</script>

<template>
  <Drawer
    :open="isOpen"
    :title="t('platform.tenant.resendInvite.dialog.title')"
    width="max-w-md"
    @update:open="onClose"
  >
    <div v-if="row" class="space-y-4">
      <p class="text-sm text-muted-foreground leading-relaxed">
        {{ t('platform.tenant.resendInvite.dialog.body', { displayName: row.displayName, tenantCode: row.tenantCode }) }}
      </p>

      <div>
        <label class="text-xs text-muted-foreground block mb-1">
          {{ t('platform.tenant.resendInvite.dialog.emailLabel') }}
        </label>
        <input v-model="email" type="email"
               class="w-full px-3 py-2 text-sm rounded-md border border-border bg-background focus:outline-none focus:ring-2 focus:ring-primary/40"
               :placeholder="t('platform.tenant.resendInvite.dialog.emailPlaceholder')"
               :disabled="submitting" />
        <p class="text-[11px] text-muted-foreground mt-1">
          {{ t('platform.tenant.resendInvite.dialog.emailHint') }}
        </p>
      </div>

      <div class="text-xs text-muted-foreground space-y-1.5 pl-1">
        <p>· {{ t('platform.tenant.resendInvite.dialog.tokenNote') }}</p>
        <p>· {{ t('platform.tenant.resendInvite.dialog.activatedNote') }}</p>
      </div>
    </div>

    <template #footer>
      <div class="flex justify-end gap-2">
        <button class="h-9 px-3 rounded border border-border text-sm"
                @click="emit('close')">
{{ t('common.button.cancel') }}
</button>
        <button class="h-9 px-3 rounded bg-primary text-primary-foreground text-sm inline-flex items-center gap-1 disabled:opacity-50 disabled:cursor-not-allowed"
                :disabled="submitting"
                @click="submit">
          <Send class="size-4" />
          {{ submitting ? t('platform.tenant.resendInvite.dialog.sending') : t('platform.tenant.resendInvite.dialog.confirm') }}
        </button>
      </div>
    </template>
  </Drawer>
</template>
