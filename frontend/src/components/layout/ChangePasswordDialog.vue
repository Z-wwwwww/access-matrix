<script setup>
import { computed } from 'vue'
import { useI18n } from 'vue-i18n'
import { useRoute } from 'vue-router'
import Dialog from '@/components/ui/Dialog.vue'
import { KeyRound, ExternalLink } from 'lucide-vue-next'
import { beginPasswordUpdate, stashReturnTo, oidcConfig } from '@/utils/oidc'

/**
 * Self-service password change is owned by the IdP. Instead of dropping the
 * user at the Keycloak account-console ROOT (where the password form hides
 * two levels deep under Account security → Signing in), we fire Keycloak's
 * UPDATE_PASSWORD Application-Initiated Action: a full-page redirect lands
 * DIRECTLY on the "update password" screen, and on save/cancel Keycloak
 * returns through /sso/callback — back to the page the user came from.
 * The backend never sees the new password.
 */
const { t } = useI18n()
const route = useRoute()

defineProps({
  open: { type: Boolean, default: false }
})
const emit = defineEmits(['update:open'])

const ssoEnabled = computed(() => oidcConfig().enabled)

function goUpdatePassword() {
  // Come back to the page the user was on once KC redirects through
  // /sso/callback (SsoCallback routes to popReturnTo()).
  stashReturnTo(route.fullPath)
  beginPasswordUpdate()
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
      <p>{{ t('password.updateHint') }}</p>

      <button
        v-if="ssoEnabled"
        class="inline-flex items-center gap-2 h-10 px-4 rounded-lg bg-primary text-primary-foreground font-medium hover:bg-primary/90 transition-colors"
        @click="goUpdatePassword"
      >
        <KeyRound :size="16" />
        {{ t('password.updateButton') }}
        <ExternalLink :size="14" class="opacity-80" />
      </button>

      <div v-else class="text-xs text-muted-foreground p-3 rounded bg-muted/40 border border-border">
        {{ t('password.unavailable') }}
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
