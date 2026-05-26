<script setup>
import { ref, computed, onMounted, onBeforeUnmount } from 'vue'
import { useRouter, useRoute } from 'vue-router'
import { useI18n } from 'vue-i18n'
import { useAuthStore } from '@/stores/auth'
import { useMenuStore } from '@/stores/menu'
import { useTabsStore } from '@/stores/tabs'
import { User, Lock, Building, KeyRound } from 'lucide-vue-next'
import { beginLogin as beginSsoLogin, oidcConfig, stashReturnTo, keycloakForgotPasswordUrl } from '@/utils/oidc'

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
const hotClicks = ref(0)
let hotResetTimer = null
function onHotZoneClick() {
  if (!ssoEnabled || passwordUnlocked.value) return  // nothing to unlock
  hotClicks.value += 1
  clearTimeout(hotResetTimer)
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
  stashReturnTo(route.query.from || '/')
  try {
    await beginSsoLogin()   // navigates away; never resolves
  } catch (e) {
    errorMsg.value = e.message || 'SSO not available'
  }
}

function handleForgotPassword() {
  const url = keycloakForgotPasswordUrl()
  if (url) {
    stashReturnTo(route.query.from || '/')
    window.location.assign(url)
  }
}

onMounted(() => {
  if (authStore.isAuthenticated) router.replace('/')
  // Surface a callback failure (set by SsoCallback.vue) so the user
  // sees why they were bounced back from the IdP. detail param carries
  // the actual error message for fast debugging.
  if (route.query.sso_error) {
    const detail = route.query.detail ? decodeURIComponent(route.query.detail) : ''
    ssoErrorFromQuery.value = detail
        ? `${t('login.message.ssoFailed')} — ${detail}`
        : t('login.message.ssoFailed')
  }
})

onBeforeUnmount(() => {
  clearTimeout(hotResetTimer)
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
