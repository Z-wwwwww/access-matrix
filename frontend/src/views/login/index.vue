<script setup>
import { ref, onMounted } from 'vue'
import { useRouter, useRoute } from 'vue-router'
import { useI18n } from 'vue-i18n'
import { useAuthStore } from '@/stores/auth'
import { useMenuStore } from '@/stores/menu'
import { useTabsStore } from '@/stores/tabs'
import { User, Lock, Building } from 'lucide-vue-next'

const router = useRouter()
const route = useRoute()
const { t } = useI18n()
const authStore = useAuthStore()
const menuStore = useMenuStore()
const tabsStore = useTabsStore()

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
  if (e.key === 'Enter') handleLogin()
}

onMounted(() => {
  if (authStore.isAuthenticated) router.replace('/')
})
</script>

<template>
  <div
    class="relative min-h-screen flex items-center justify-center bg-background p-4"
    @keydown="handleKeydown"
  >
    <!-- Login card -->
    <div class="w-full max-w-[420px] rounded-xl border border-border bg-card shadow-lg shadow-black/[0.06] p-8">
      <!-- Brand -->
      <div class="text-center mb-8">
        <h1 class="text-2xl font-bold text-foreground font-serif tracking-wide">Access Matrix</h1>
      </div>

      <!-- Error message -->
      <div v-if="errorMsg" class="mb-4 p-3 rounded-lg bg-destructive/10 text-destructive text-sm border border-destructive/20">
        {{ errorMsg }}
      </div>

      <form @submit.prevent="handleLogin" class="space-y-4">
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

        <!-- advanced toggle (tenant input) -->
        <div class="text-center">
          <button
            type="button"
            class="text-xs text-muted-foreground hover:text-foreground transition-colors"
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
