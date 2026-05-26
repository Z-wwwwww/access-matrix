<script setup>
import { computed } from 'vue'
import { useI18n } from 'vue-i18n'
import Dialog from '@/components/ui/Dialog.vue'
import { KeyRound, ExternalLink } from 'lucide-vue-next'
import { keycloakAccountUrl, oidcConfig } from '@/utils/oidc'

/**
 * Self-service password change is now owned by the IdP — the user goes to
 * Keycloak's built-in account console (one URL per realm) and changes their
 * password there. The backend doesn't even see the new password.
 *
 * <p>This dialog therefore renders no form; it shows a single "open account
 * console" link and explains what to expect.
 */
const { t } = useI18n()

defineProps({
  open: { type: Boolean, default: false }
})
const emit = defineEmits(['update:open'])

const accountUrl = computed(() => keycloakAccountUrl())
const ssoEnabled = computed(() => oidcConfig().enabled)

function openConsole() {
  if (accountUrl.value) window.open(accountUrl.value, '_blank', 'noopener')
}
</script>

<template>
  <Dialog
    :open="open"
    :title="t('layout.header.password')"
    width="max-w-md"
    @update:open="(v) => emit('update:open', v)"
  >
    <div class="space-y-4 text-sm text-foreground">
      <p>{{ t('password.openConsoleHint') }}</p>

      <button
        v-if="ssoEnabled && accountUrl"
        class="inline-flex items-center gap-2 h-10 px-4 rounded-lg bg-primary text-primary-foreground font-medium hover:bg-primary/90 transition-colors"
        @click="openConsole"
      >
        <KeyRound :size="16" />
        {{ t('password.openConsoleButton') }}
        <ExternalLink :size="14" class="opacity-80" />
      </button>

      <div v-else class="text-xs text-muted-foreground p-3 rounded bg-muted/40 border border-border">
        {{ t('password.consoleUnavailable') }}
      </div>
    </div>

    <template #footer>
      <button
        class="inline-flex items-center gap-1.5 h-9 px-4 rounded-lg text-sm font-medium bg-muted text-foreground hover:bg-muted/80 transition-colors"
        @click="emit('update:open', false)"
      >
        {{ t('common.button.close') }}
      </button>
    </template>
  </Dialog>
</template>
