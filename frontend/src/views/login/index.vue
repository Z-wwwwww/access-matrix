<script setup>
import { ref, computed, onMounted, onBeforeUnmount } from 'vue'
import { useRouter, useRoute } from 'vue-router'
import { useI18n } from 'vue-i18n'
import { useAuthStore } from '@/stores/auth'
import { useMenuStore } from '@/stores/menu'
import { useTabsStore } from '@/stores/tabs'
import { User, Lock, Building, KeyRound, ShieldAlert, RefreshCw } from 'lucide-vue-next'
import { beginLogin as beginSsoLogin, oidcConfig, stashReturnTo, keycloakForgotPasswordUrl, isSsoReachable } from '@/utils/oidc'

const ssoConfig = oidcConfig()
const ssoEnabled = ssoConfig.enabled
const ssoErrorFromQuery = ref('')

const router = useRouter()
const route = useRoute()
const { t } = useI18n()
const authStore = useAuthStore()
const menuStore = useMenuStore()
const tabsStore = useTabsStore()

// ─── view mode ─────────────────────────────────────────────────────────────
// When SSO is enabled (VITE_OIDC_ENABLED=true), we hide the username/password
// form by default and show only the "Sign in with SSO" button — that's the
// primary path. The legacy password form stays reachable as a break-glass for
// devs / admins when Keycloak is unreachable; to unlock it, click the
// invisible hot-zone in the card's top-right corner 5 times within 2 seconds.
// Choice persists per tab (sessionStorage) so a refresh doesn't snap back.
const PASSWORD_UNLOCK_KEY = 'login_password_unlocked'
const passwordUnlocked = ref(sessionStorage.getItem(PASSWORD_UNLOCK_KEY) === '1')
const viewMode = computed(() => (ssoEnabled && !passwordUnlocked.value) ? 'sso' : 'password')

// hot-zone counter
const HOT_CLICKS_REQUIRED = 5
const HOT_RESET_MS = 2000
const AUTO_REDIRECT_DELAY_MS = 1500   // window for hot-zone before SSO fires
const hotClicks = ref(0)
let hotResetTimer = null
let autoRedirectTimer = null
const autoRedirecting = ref(false)
// SSO server pre-flight probe state. When the probe fails (KC down,
// network blocked, etc.) we abort the auto-redirect and surface a friendly
// in-app banner with a "use break-glass" CTA — much better UX than letting
// the browser navigate to a dead URL and render its native error page,
// where the user has no path back to the SPA.
const ssoUnreachable = ref(false)
const ssoRetrying = ref(false)

function onHotZoneClick() {
  if (!ssoEnabled || passwordUnlocked.value) return  // nothing to unlock
  hotClicks.value += 1
  clearTimeout(hotResetTimer)
  // First hot-zone click also cancels the pending auto-redirect — once
  // the user starts trying to break glass, don't yank the rug out from
  // under them by navigating to Keycloak mid-click.
  if (autoRedirectTimer) {
    clearTimeout(autoRedirectTimer)
    autoRedirectTimer = null
    autoRedirecting.value = false
  }
  if (hotClicks.value >= HOT_CLICKS_REQUIRED) {
    passwordUnlocked.value = true
    sessionStorage.setItem(PASSWORD_UNLOCK_KEY, '1')
    hotClicks.value = 0
  } else {
    hotResetTimer = setTimeout(() => { hotClicks.value = 0 }, HOT_RESET_MS)
  }
}
function relockPassword() {
  passwordUnlocked.value = false
  sessionStorage.removeItem(PASSWORD_UNLOCK_KEY)
  hotClicks.value = 0
}

// ─── form state (only used in password mode) ──────────────────────────────
const TENANT_KEY = 'tenant_id'
const form = ref({
  tenant: localStorage.getItem(TENANT_KEY) || 'default',
  username: '',
  password: ''
})
const showTenant = ref(form.value.tenant !== 'default')

const loading = ref(false)
const errorMsg = ref('')

async function handleLogin() {
  if (!form.value.username) {
    errorMsg.value = t('login.message.enterUsername')
    return
  }
  if (!form.value.password) {
    errorMsg.value = t('login.message.enterPassword')
    return
  }

  loading.value = true
  errorMsg.value = ''
  // Tenant は X-Tenant-Id ヘッダ経由（request.js の interceptor が localStorage から読む）。
  // ログイン直前に localStorage を更新し、その後のすべてのリクエストに反映させる。
  const tenant = (form.value.tenant || 'default').trim() || 'default'
  localStorage.setItem(TENANT_KEY, tenant)
  try {
    const res = await authStore.login({
      username: form.value.username,
      password: form.value.password
    })
    if (res.data.code === 0) {
      // Reset prior-session residue so the new user gets fresh menus + tabs + dynamic routes
      menuStore.clearMenus()
      tabsStore.clearTabs()
      if (router.hasRoute('AppLayout')) router.removeRoute('AppLayout')

      // Preload /user/me up front so router-guard menu fetch never races the JWT
      try {
        await authStore.fetchUserInfo()
      } catch (e) {
        console.error('[login] fetchUserInfo failed:', e)
      }
      const redirect = route.query.from || '/'
      router.replace(redirect)
    } else {
      errorMsg.value = res.data.msg || t('login.message.loginFailed')
    }
  } catch (err) {
    errorMsg.value = err.message || t('login.message.loginFailed')
  } finally {
    loading.value = false
  }
}

function handleKeydown(e) {
  if (e.key === 'Enter' && viewMode.value === 'password') handleLogin()
}

async function handleSsoLogin() {
  errorMsg.value = ''
  ssoErrorFromQuery.value = ''
  // Probe BEFORE handing control off to window.location.assign. If the
  // probe fails we keep the user in-app and surface the friendly banner;
  // the redirect would otherwise dead-end on the browser's native error
  // page with no way back.
  ssoRetrying.value = true
  const reachable = await isSsoReachable()
  ssoRetrying.value = false
  if (!reachable) {
    ssoUnreachable.value = true
    return
  }
  ssoUnreachable.value = false
  stashReturnTo(route.query.from || '/')
  try {
    await beginSsoLogin()   // navigates away; never resolves
  } catch (e) {
    errorMsg.value = e.message || 'SSO not available'
  }
}

// Banner CTA: "use break-glass login". Equivalent to the 5-click hot-zone
// reaching its threshold — unlock the password form, persist the choice
// to sessionStorage so a reload doesn't snap back to the SSO view.
function useBreakGlass() {
  passwordUnlocked.value = true
  sessionStorage.setItem(PASSWORD_UNLOCK_KEY, '1')
  ssoUnreachable.value = false
  if (autoRedirectTimer) {
    clearTimeout(autoRedirectTimer)
    autoRedirectTimer = null
  }
  autoRedirecting.value = false
}

async function handleForgotPassword() {
  // Same probe-before-redirect pattern as handleSsoLogin and the logout
  // flow. Forgot-password navigates to Keycloak's reset-credentials
  // endpoint; without the probe, a down KC would dead-end on the
  // browser's "refused to connect" page with no way back. Surface the
  // unreachable banner instead — the user wanted to recover their KC
  // password, but if KC is down that's not possible right now AND they
  // may want to break-glass into the system in the meantime.
  errorMsg.value = ''
  ssoErrorFromQuery.value = ''
  ssoRetrying.value = true
  const reachable = await isSsoReachable()
  ssoRetrying.value = false
  if (!reachable) {
    ssoUnreachable.value = true
    return
  }
  const url = keycloakForgotPasswordUrl()
  if (url) {
    stashReturnTo(route.query.from || '/')
    window.location.assign(url)
  }
}

// Auto-redirect to SSO when we're in SSO mode and there's no reason to
// stay on /login. Reasons to stay:
//   - already authenticated (caller redirects to '/')
//   - the user just bounced back from SSO with an error (showing it
//     immediately would cause a redirect loop)
//   - the user manually unlocked password mode (5-click hot-zone) and
//     the SPA needs to render the password form for them
//   - they came here via ?password=1 (e.g. an admin's bookmarked
//     break-glass URL)
function shouldAutoRedirectToSso() {
  if (!ssoEnabled) return false
  if (authStore.isAuthenticated) return false
  if (passwordUnlocked.value) return false
  if (route.query.sso_error || route.query.err) return false
  if (route.query.password === '1') return false
  // We just came from a logout that couldn't reach KC — auto-redirecting
  // would just hit the same brick wall. Show the unreachable banner
  // directly instead.
  if (route.query.logout === 'local-only') return false
  return true
}

onMounted(() => {
  if (authStore.isAuthenticated) {
    router.replace('/')
    return
  }
  // Surface a callback failure (set by SsoCallback.vue) so the user
  // sees why they were bounced back from the IdP. detail param carries
  // the actual error message for fast debugging.
  if (route.query.sso_error) {
    const detail = route.query.detail ? decodeURIComponent(route.query.detail) : ''
    ssoErrorFromQuery.value = detail
        ? `${t('login.message.ssoFailed')} — ${detail}`
        : t('login.message.ssoFailed')
  }
  // Local-only logout (KC was unreachable at sign-out): pre-arm the
  // unreachable banner so the user lands on the EXACT same UI they
  // would see if they'd tried to SSO fresh and hit a down KC. No
  // separate "you were logged out locally" prefix — visual consistency
  // with the login-time probe failure is the explicit design goal.
  if (route.query.logout === 'local-only') {
    ssoUnreachable.value = true
  }
  // err=menu means router.beforeEach couldn't load /menu/me after a
  // successful login (token granted, but downstream session unusable).
  // Surface the detail rather than silently 404-ing the user.
  if (route.query.err === 'menu') {
    const detail = route.query.detail ? decodeURIComponent(route.query.detail) : ''
    ssoErrorFromQuery.value = detail
        ? `Menu load failed — ${detail}`
        : 'Menu load failed'
  }
  // Fire-and-forget SSO redirect after a short window. The delay gives
  // the user time to engage the 5-click hot-zone (break-glass) if they
  // need it — without it, SSO would fire on the first paint and the
  // hot-zone would never be reachable. beginSsoLogin itself navigates
  // via window.location.assign and never resolves.
  if (shouldAutoRedirectToSso()) {
    autoRedirecting.value = true
    // Pre-flight the SSO endpoint BEFORE the 1.5s hot-zone window kicks
    // in. If KC is down, the user gets a friendly in-app banner with a
    // break-glass CTA instead of the browser's "refused to connect"
    // page — that page has no way back, so a confused operator would
    // have to hit Back, lose tab state, and rediscover the hot-zone.
    isSsoReachable().then((reachable) => {
      if (!reachable) {
        autoRedirecting.value = false
        ssoUnreachable.value = true
        return
      }
      // Healthy — proceed with the normal hot-zone-friendly delay.
      autoRedirectTimer = setTimeout(() => {
        beginSsoLogin().catch((e) => {
          autoRedirecting.value = false
          errorMsg.value = e.message || 'SSO not available'
        })
      }, AUTO_REDIRECT_DELAY_MS)
    })
  }
})

onBeforeUnmount(() => {
  clearTimeout(hotResetTimer)
  clearTimeout(autoRedirectTimer)
})
</script>

<template>
  <div
    class="relative min-h-screen flex items-center justify-center bg-background p-4"
    @keydown="handleKeydown"
  >
    <!-- Login card -->
    <div class="relative w-full max-w-[420px] rounded-xl border border-border bg-card shadow-lg shadow-black/[0.06] p-8">
      <!-- Hidden hot-zone: 5 clicks within 2 s in SSO mode unlocks the password
           form (break-glass for devs when Keycloak is down). Invisible by design
           — visible cue would defeat the "hidden door" intent. -->
      <button
        type="button"
        class="absolute top-0 right-0 w-12 h-12 opacity-0 cursor-default"
        :aria-label="t('login.passwordModeHotzone')"
        :title="hotClicks > 0 ? `${hotClicks}/${HOT_CLICKS_REQUIRED}` : ''"
        @click="onHotZoneClick"
      ></button>

      <!-- Brand -->
      <div class="text-center mb-8">
        <h1 class="text-2xl font-bold text-foreground font-serif tracking-wide">Access Matrix</h1>
      </div>

      <!-- Error message -->
      <div v-if="errorMsg || ssoErrorFromQuery" class="mb-4 p-3 rounded-lg bg-destructive/10 text-destructive text-sm border border-destructive/20">
        {{ errorMsg || ssoErrorFromQuery }}
      </div>

      <!-- ─── SSO MODE ────────────────────────────────────────────────────── -->
      <div v-if="viewMode === 'sso'" class="space-y-4">
        <!-- SSO server is unreachable (probe failed) — show the friendly
             banner with break-glass + retry CTAs. Takes precedence over
             the auto-redirect spinner because we KNOW the redirect would
             fail anyway. -->
        <template v-if="ssoUnreachable">
          <div class="p-4 rounded-lg bg-amber-500/10 border border-amber-500/30 space-y-3">
            <div class="flex items-start gap-3">
              <ShieldAlert :size="18" class="text-amber-600 dark:text-amber-400 shrink-0 mt-0.5" />
              <div class="space-y-1">
                <p class="text-sm font-medium text-foreground">{{ t('login.ssoUnreachable.title') }}</p>
                <p class="text-xs text-muted-foreground leading-relaxed">{{ t('login.ssoUnreachable.body') }}</p>
              </div>
            </div>
            <div class="flex gap-2">
              <button
                type="button"
                class="flex-1 h-10 inline-flex items-center justify-center gap-2 bg-primary text-primary-foreground font-medium rounded-lg text-sm hover:bg-primary/90 transition-colors"
                @click="useBreakGlass"
              >
                <KeyRound :size="14" />
                {{ t('login.ssoUnreachable.useBreakGlass') }}
              </button>
              <button
                type="button"
                :disabled="ssoRetrying"
                class="h-10 px-3 inline-flex items-center justify-center gap-1.5 border border-border rounded-lg text-xs text-foreground hover:bg-muted disabled:opacity-50 transition-colors"
                @click="handleSsoLogin"
              >
                <RefreshCw :size="14" :class="ssoRetrying && 'animate-spin'" />
                {{ ssoRetrying ? t('login.ssoUnreachable.retrying') : t('login.ssoUnreachable.retry') }}
              </button>
            </div>
          </div>
        </template>

        <!-- Auto-redirect in progress: show a low-key spinner. The 5-click
             hot-zone in the card's top-right corner is still alive during
             this window — clicking it 5 times cancels the redirect and
             unlocks the password form. -->
        <template v-else-if="autoRedirecting">
          <div class="flex flex-col items-center justify-center py-4 space-y-3">
            <div class="w-6 h-6 border-2 border-primary border-t-transparent rounded-full animate-spin"></div>
            <p class="text-sm text-muted-foreground">{{ t('login.ssoRedirecting') }}</p>
          </div>
        </template>

        <!-- Fallback / error state: show the manual button + forgot link
             (auto-redirect failed, or user landed here with ?sso_error). -->
        <template v-else>
          <p class="text-sm text-muted-foreground text-center">
            {{ t('login.ssoOnlyHint') }}
          </p>

          <button
            type="button"
            class="w-full h-11 inline-flex items-center justify-center gap-2 bg-primary text-primary-foreground font-medium rounded-lg text-sm hover:bg-primary/90 active:bg-primary/80 transition-colors"
            @click="handleSsoLogin"
          >
            <KeyRound :size="16" />
            {{ t('login.ssoButton') }}
          </button>

          <div class="text-center">
            <button
              type="button"
              class="text-xs text-muted-foreground hover:text-foreground transition-colors"
              @click="handleForgotPassword"
            >
              {{ t('login.forgotPassword') }}
            </button>
          </div>
        </template>
      </div>

      <!-- ─── PASSWORD MODE (default when SSO disabled, or hot-zone unlocked) ── -->
      <form v-else @submit.prevent="handleLogin" class="space-y-4">
        <!-- Break-glass indicator when SSO is enabled but user unlocked password mode -->
        <div v-if="ssoEnabled && passwordUnlocked"
             class="flex items-center justify-between p-2 rounded-lg bg-amber-100/60 border border-amber-300/60 text-xs text-amber-900">
          <span>⚠ {{ t('login.passwordBreakGlass') }}</span>
          <button type="button" class="underline hover:text-amber-700" @click="relockPassword">
            {{ t('login.backToSso') }}
          </button>
        </div>

        <!-- identifier (username / email / user_no) -->
        <div>
          <label class="block text-sm font-medium text-foreground mb-1.5">{{ t('login.identifierLabel') }}</label>
          <div class="relative">
            <div class="absolute left-3 top-1/2 -translate-y-1/2 text-muted-foreground">
              <User :size="16" />
            </div>
            <input
              v-model="form.username"
              type="text"
              :placeholder="t('login.identifierPlaceholder')"
              class="w-full h-10 pl-10 pr-4 border border-input rounded-lg bg-background text-foreground text-sm placeholder:text-muted-foreground focus:outline-none focus:ring-2 focus:ring-ring transition"
            />
          </div>
        </div>

        <!-- password -->
        <div>
          <label class="block text-sm font-medium text-foreground mb-1.5">{{ t('login.passwordLabel') }}</label>
          <div class="relative">
            <div class="absolute left-3 top-1/2 -translate-y-1/2 text-muted-foreground">
              <Lock :size="16" />
            </div>
            <input
              v-model="form.password"
              type="password"
              :placeholder="t('login.passwordPlaceholder')"
              class="w-full h-10 pl-10 pr-4 border border-input rounded-lg bg-background text-foreground text-sm placeholder:text-muted-foreground focus:outline-none focus:ring-2 focus:ring-ring transition"
            />
          </div>
        </div>

        <!-- tenant (optional, defaults to "default") -->
        <div v-if="showTenant">
          <label class="block text-sm font-medium text-foreground mb-1.5">{{ t('login.tenantLabel') }}</label>
          <div class="relative">
            <div class="absolute left-3 top-1/2 -translate-y-1/2 text-muted-foreground">
              <Building :size="16" />
            </div>
            <input
              v-model="form.tenant"
              type="text"
              :placeholder="t('login.tenantPlaceholder')"
              class="w-full h-10 pl-10 pr-4 border border-input rounded-lg bg-background text-foreground text-sm placeholder:text-muted-foreground focus:outline-none focus:ring-2 focus:ring-ring transition"
            />
          </div>
        </div>

        <!-- submit -->
        <button
          type="submit"
          :disabled="loading"
          class="w-full h-10 bg-primary text-primary-foreground font-medium rounded-lg text-sm hover:bg-primary/90 active:bg-primary/80 transition-colors duration-200 disabled:opacity-50 disabled:cursor-not-allowed"
        >
          {{ loading ? t('login.submitting') : t('login.submit') }}
        </button>

        <!-- footer links: tenant toggle only (forgot password only in SSO mode) -->
        <div class="flex items-center justify-between text-xs text-muted-foreground">
          <button
            type="button"
            class="hover:text-foreground transition-colors"
            @click="showTenant = !showTenant"
          >
            {{ showTenant ? t('login.hideAdvanced') : t('login.showAdvanced') }}
          </button>
        </div>
      </form>
    </div>
    <!-- Footer -->
    <footer class="absolute bottom-3 left-0 right-0 text-center text-[11px] text-muted-foreground">
      {{ t('layout.footer.copyright') }}
    </footer>
  </div>
</template>
