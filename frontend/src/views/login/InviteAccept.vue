<script setup>
import { ref, onMounted } from 'vue'
import { useRouter, useRoute } from 'vue-router'
import { useI18n } from 'vue-i18n'
import { Lock, CheckCircle2, XCircle } from 'lucide-vue-next'
import { probeInviteApi, acceptInviteApi } from '../../../services/invite'

/**
 * Invite-acceptance landing page. The user got here from the email link;
 * the token is in the path. Flow:
 *
 *   1. onMounted → probeInviteApi to confirm the token is still valid.
 *      Bad / expired / used → show "invite no longer valid" state.
 *   2. User sets a password (twice, must match), client-side checks
 *      length, then POST → acceptInviteApi to consume + sync to Keycloak.
 *   3. Success → show "all set" state with a link to /login.
 *
 * We intentionally do NOT auto-redirect to /login on success — the user
 * just set a password they need to remember; giving them a brief confirmation
 * screen makes the flow feel less hostile.
 */
const router = useRouter()
const route  = useRoute()
const { t }  = useI18n()

const token = route.params.token

const state = ref('checking')       // 'checking' | 'form' | 'submitting' | 'done' | 'invalid'
const errorMsg = ref('')
const tenantId = ref('')

const password        = ref('')
const passwordConfirm = ref('')

onMounted(async () => {
  if (!token) {
    state.value = 'invalid'
    errorMsg.value = t('invite.message.invalidLink')
    return
  }
  try {
    const res = await probeInviteApi(token)
    if (res.data.code === 0) {
      tenantId.value = res.data.data?.tenantId || ''
      state.value = 'form'
    } else {
      state.value = 'invalid'
      errorMsg.value = res.data.msg || t('invite.message.notValid')
    }
  } catch (e) {
    state.value = 'invalid'
    errorMsg.value = e.message || t('invite.message.notValid')
  }
})

async function submit() {
  errorMsg.value = ''
  if (!password.value || password.value.length < 8) {
    errorMsg.value = t('invite.message.passwordTooShort')
    return
  }
  if (password.value !== passwordConfirm.value) {
    errorMsg.value = t('invite.message.passwordMismatch')
    return
  }
  state.value = 'submitting'
  try {
    const res = await acceptInviteApi(token, password.value)
    if (res.data.code === 0) {
      state.value = 'done'
    } else {
      state.value = 'form'
      errorMsg.value = res.data.msg || t('invite.message.acceptFailed')
    }
  } catch (e) {
    state.value = 'form'
    errorMsg.value = e.message || t('invite.message.acceptFailed')
  }
}

function goLogin() {
  router.push('/login')
}
</script>

<template>
  <div class="min-h-screen flex items-center justify-center bg-background p-4">
    <div class="w-full max-w-[420px] rounded-xl border border-border bg-card shadow-lg p-8 space-y-5">
      <!-- Header -->
      <div class="text-center">
        <h1 class="text-xl font-semibold text-foreground">{{ t('invite.title') }}</h1>
        <p v-if="tenantId" class="text-xs text-muted-foreground mt-1">{{ t('invite.tenantPrefix') }} {{ tenantId }}</p>
      </div>

      <!-- States -->
      <template v-if="state === 'checking'">
        <p class="text-sm text-muted-foreground text-center">{{ t('invite.message.checking') }}</p>
      </template>

      <template v-else-if="state === 'invalid'">
        <div class="text-center text-destructive">
          <XCircle :size="40" class="mx-auto mb-2" />
          <p class="text-sm">{{ errorMsg }}</p>
          <button class="mt-4 text-xs underline text-muted-foreground hover:text-foreground" @click="goLogin">
            {{ t('invite.button.goLogin') }}
          </button>
        </div>
      </template>

      <template v-else-if="state === 'done'">
        <div class="text-center">
          <CheckCircle2 :size="40" class="mx-auto mb-2 text-emerald-600" />
          <p class="text-sm text-foreground">{{ t('invite.message.done') }}</p>
          <button class="mt-5 w-full h-10 bg-primary text-primary-foreground font-medium rounded-lg text-sm hover:bg-primary/90"
                  @click="goLogin">
            {{ t('invite.button.goLogin') }}
          </button>
        </div>
      </template>

      <template v-else>
        <div v-if="errorMsg" class="p-3 rounded-lg bg-destructive/10 text-destructive text-xs border border-destructive/20">
          {{ errorMsg }}
        </div>

        <form @submit.prevent="submit" class="space-y-4">
          <div>
            <label class="block text-sm font-medium text-foreground mb-1.5">{{ t('invite.passwordLabel') }}</label>
            <div class="relative">
              <div class="absolute left-3 top-1/2 -translate-y-1/2 text-muted-foreground">
                <Lock :size="16" />
              </div>
              <input v-model="password" type="password" minlength="8" maxlength="128" required
                     :placeholder="t('invite.passwordPlaceholder')"
                     class="w-full h-10 pl-10 pr-4 border border-input rounded-lg bg-background text-foreground text-sm placeholder:text-muted-foreground focus:outline-none focus:ring-2 focus:ring-ring transition" />
            </div>
          </div>

          <div>
            <label class="block text-sm font-medium text-foreground mb-1.5">{{ t('invite.passwordConfirmLabel') }}</label>
            <div class="relative">
              <div class="absolute left-3 top-1/2 -translate-y-1/2 text-muted-foreground">
                <Lock :size="16" />
              </div>
              <input v-model="passwordConfirm" type="password" minlength="8" maxlength="128" required
                     :placeholder="t('invite.passwordConfirmPlaceholder')"
                     class="w-full h-10 pl-10 pr-4 border border-input rounded-lg bg-background text-foreground text-sm placeholder:text-muted-foreground focus:outline-none focus:ring-2 focus:ring-ring transition" />
            </div>
          </div>

          <button type="submit" :disabled="state === 'submitting'"
                  class="w-full h-10 bg-primary text-primary-foreground font-medium rounded-lg text-sm hover:bg-primary/90 active:bg-primary/80 transition-colors duration-200 disabled:opacity-50 disabled:cursor-not-allowed">
            {{ state === 'submitting' ? t('invite.button.submitting') : t('invite.button.submit') }}
          </button>
        </form>
      </template>
    </div>
  </div>
</template>
