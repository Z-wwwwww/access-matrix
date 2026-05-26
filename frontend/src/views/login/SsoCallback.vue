<script setup>
import { ref, onMounted } from 'vue'
import { useRouter, useRoute } from 'vue-router'
import { useAuthStore } from '@/stores/auth'
import { useMenuStore } from '@/stores/menu'
import { useTabsStore } from '@/stores/tabs'
import { handleCallback, popReturnTo } from '@/utils/oidc'

/**
 * OIDC callback page — the IdP redirects here with ?code=...&state=...
 * after a successful Keycloak login. We exchange the code for an access
 * token, seed the auth store, fetch the user profile, then jump to the
 * page the user originally wanted (preserved across the IdP round-trip
 * via sessionStorage in utils/oidc.js).
 *
 * Failure modes (CSRF mismatch / network / invalid_grant) bounce back to
 * /login with an error message.
 */
const router    = useRouter()
const route     = useRoute()
const authStore = useAuthStore()
const menuStore = useMenuStore()
const tabsStore = useTabsStore()

const errorMsg = ref('')

onMounted(async () => {
  try {
    const { accessToken, idToken } = await handleCallback(route.query)
    authStore.setAccessToken(accessToken)
    // id_token is required for RP-Initiated Logout — Keycloak uses it to
    // confirm which session to end. Stash it now even though nothing
    // reads it until the user signs out.
    authStore.setIdToken(idToken)

    // Same post-login cleanup as the password path — old menus / tabs /
    // dynamic routes can carry stale permissions across re-logins.
    menuStore.clearMenus()
    tabsStore.clearTabs()
    if (router.hasRoute('AppLayout')) router.removeRoute('AppLayout')

    // fetchUserInfo failure is FATAL — if /user/me can't resolve the
    // business row (e.g. backend not restarted after the JIT changes, or
    // the row was soft-deleted but the Keycloak account still exists),
    // continuing would land the user in an empty shell with no menu and
    // no perms. Better to clear the token and bounce back with a clear
    // error than to silently hide the cause.
    try {
      await authStore.fetchUserInfo()
    } catch (e) {
      console.error('[sso-callback] fetchUserInfo failed:', e)
      authStore.clearAuth()
      throw new Error(`Profile lookup failed: ${(e && e.message) || 'unknown'}`)
    }

    router.replace(popReturnTo())
  } catch (err) {
    console.error('[sso-callback] error:', err)
    errorMsg.value = err.message || 'SSO sign-in failed'
    // 3 s instead of 1.5 s so the user actually has time to read the
    // message; carry detail through query so login page can show it too.
    const detail = encodeURIComponent(err.message || 'unknown')
    setTimeout(() => router.replace({ path: '/login', query: { sso_error: '1', detail } }), 3000)
  }
})
</script>

<template>
  <div class="min-h-screen flex items-center justify-center bg-background p-4">
    <div class="w-full max-w-[420px] rounded-xl border border-border bg-card shadow-lg p-8 text-center space-y-4">
      <div v-if="!errorMsg">
        <h1 class="text-xl font-medium text-foreground">Signing you in…</h1>
        <p class="text-sm text-muted-foreground mt-2">Exchanging credentials with the identity provider.</p>
      </div>
      <div v-else>
        <h1 class="text-xl font-medium text-destructive">Sign-in failed</h1>
        <p class="text-sm text-muted-foreground mt-2 break-all">{{ errorMsg }}</p>
        <p class="text-xs text-muted-foreground mt-4">Redirecting to login…</p>
      </div>
    </div>
  </div>
</template>
