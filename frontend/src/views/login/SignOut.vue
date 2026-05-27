<script setup>
/**
 * Sign-out transition page. Mirrors the SsoCallback pattern: the user
 * clicks "Sign out" in AppHeader, we route them HERE instead of doing
 * the work inline, and this page handles the actual flow with visible
 * progress.
 *
 * <p>Why a dedicated page instead of inline:
 * <ul>
 *   <li><b>Visible work</b> — the OIDC logout path makes a backend call
 *       (revoke refresh token), a probe to Keycloak (3 s worst case),
 *       and a final window.location.assign. Doing this from a dropdown
 *       menu means the user sees the dropdown close and then... silence.
 *       Routing to a spinner page makes the wait legible.</li>
 *   <li><b>Failure surfacing</b> — if the logout call throws unexpectedly
 *       (network, JS error), AppHeader has nowhere to put the error.
 *       Here we can render it with a "go to login" escape.</li>
 *   <li><b>Cancellation barrier</b> — once we route here, the dropdown
 *       is gone and the user can't accidentally re-trigger logout
 *       mid-flight.</li>
 * </ul>
 *
 * <p>Possible outcomes:
 * <ol>
 *   <li>OIDC + KC healthy → authStore.logout() returns true, window
 *       navigates to KC's end_session_endpoint; this page is unmounted
 *       by the browser before the next paint.</li>
 *   <li>OIDC + KC unreachable → authStore.logout() returns false with
 *       lastLogoutLocalOnly=true; we push /login?logout=local-only so
 *       the login page surfaces the unreachable banner.</li>
 *   <li>Password mode → authStore.logout() returns false; we push /login.</li>
 *   <li>Exception thrown → we render an error state with a manual link
 *       to /login (user-driven escape; no automatic redirect, on the
 *       theory that they should see what broke).</li>
 * </ol>
 */
import { ref, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { useI18n } from 'vue-i18n'
import { useAuthStore } from '@/stores/auth'
import { LogOut, AlertTriangle } from 'lucide-vue-next'

const router = useRouter()
const { t } = useI18n()
const authStore = useAuthStore()

const status = ref('signing-out')   // signing-out | failed
const errorMsg = ref('')

onMounted(async () => {
  try {
    const navigatedAway = await authStore.logout()
    if (navigatedAway) {
      // window.location.assign already fired — the browser is on its
      // way to Keycloak. Don't do anything else; the page will unmount
      // when navigation commits.
      return
    }
    // Local logout only (password mode OR OIDC mode with unreachable
    // KC). Route to /login; the localOnly hint, when present, makes
    // /login show the "logged out locally" prefix banner above the
    // standard unreachable banner.
    const localOnly = authStore.wasLastLogoutLocalOnly()
    router.replace(localOnly ? '/login?logout=local-only' : '/login')
  } catch (e) {
    // Unexpected — e.g. authStore.logout() bug, or some downstream
    // throw we didn't anticipate. Don't auto-redirect; let the user
    // read the error and click out.
    status.value = 'failed'
    errorMsg.value = e?.message || 'Sign-out failed'
  }
})

function goLogin() {
  router.replace('/login')
}
</script>

<template>
  <div class="min-h-screen flex items-center justify-center bg-background p-4">
    <div class="w-full max-w-[420px] rounded-xl border border-border bg-card shadow-lg p-8 space-y-5">
      <!-- Brand -->
      <div class="text-center">
        <h1 class="text-2xl font-bold text-foreground font-serif tracking-wide">Access Matrix</h1>
      </div>

      <!-- Signing-out state -->
      <template v-if="status === 'signing-out'">
        <div class="flex flex-col items-center justify-center py-4 space-y-3">
          <div class="w-7 h-7 border-2 border-primary border-t-transparent rounded-full animate-spin"></div>
          <p class="text-sm font-medium text-foreground inline-flex items-center gap-2">
            <LogOut :size="14" class="text-muted-foreground" />
            {{ t('signOut.title') }}
          </p>
          <p class="text-xs text-muted-foreground text-center max-w-[280px]">
            {{ t('signOut.body') }}
          </p>
        </div>
      </template>

      <!-- Failed state -->
      <template v-else>
        <div class="flex flex-col items-center justify-center py-4 space-y-3">
          <AlertTriangle :size="32" class="text-destructive" />
          <p class="text-sm font-medium text-foreground">{{ t('signOut.failed.title') }}</p>
          <p class="text-xs text-destructive text-center break-all max-w-[320px]">{{ errorMsg }}</p>
          <button
            type="button"
            class="mt-2 h-9 px-4 rounded-lg bg-primary text-primary-foreground text-sm font-medium hover:bg-primary/90 transition-colors"
            @click="goLogin"
          >
            {{ t('signOut.failed.goLogin') }}
          </button>
        </div>
      </template>
    </div>
  </div>
</template>
