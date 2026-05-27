<script setup>
import { ref, onMounted } from 'vue'
import { useRouter, useRoute } from 'vue-router'
import { useI18n } from 'vue-i18n'
import { Lock, CheckCircle2, XCircle } from 'lucide-vue-next'
import { probeResetApi, acceptResetApi } from '../../../services/passwordReset'

/**
 * Password-reset landing page used by the SSO → password reverse migration.
 * Mirror of InviteAccept.vue: the user got here from the migration email,
 * the cleartext token is in the route path, the rest is a thin form over
 * the pre-auth /auth/password-reset/{token} endpoint.
 *
 * State machine:
 *   checking    — onMounted GET probe; transitions to form / invalid
 *   form        — user fills in new password twice
 *   submitting  — POST in flight
 *   done        — success screen with "go to login" CTA
 *   invalid     — token expired / used / never existed; offer link to /login
 *
 * On success we DON'T auto-redirect; the user just set a password they
 * need to remember and a brief confirmation screen makes the flow feel
 * less hostile.
 */
const router = useRouter()
const route  = useRoute()
const { t }  = useI18n()

const token = route.params.token

const state    = ref('checking')   // checking | form | submitting | done | invalid
const errorMsg = ref('')
const tenantId = ref('')

const password        = ref('')
const passwordConfirm = ref('')

onMounted(async () => {
  if (!token) {
    state.value = 'invalid'
    errorMsg.value = t('passwordReset.message.invalidLink')
    return
  }
  try {
    const res = await probeResetApi(token)
    if (res.data.code === 0) {
      tenantId.value = res.data.data?.tenantId || ''
      state.value = 'form'
    } else {
      state.value = 'invalid'
      errorMsg.value = res.data.msg || t('passwordReset.message.notValid')
    }
  } catch (e) {
    state.value = 'invalid'
    errorMsg.value = e.message || t('passwordReset.message.notValid')
  }
})

async function submit() {
  errorMsg.value = ''
  if (!password.value || password.value.length < 8) {
    errorMsg.value = t('passwordReset.message.passwordTooShort')
    return
  }
  if (password.value !== passwordConfirm.value) {
    errorMsg.value = t('passwordReset.message.passwordMismatch')
    return
  }
  state.value = 'submitting'
  try {
    const res = await acceptResetApi(token, password.value)
    if (res.data.code === 0) {
      state.value = 'done'
    } else {
      state.value = 'form'
      errorMsg.value = res.data.msg || t('passwordReset.message.acceptFailed')
    }
  } catch (e) {
    state.value = 'form'
    errorMsg.value = e.message || t('passwordReset.message.acceptFailed')
  }
}

function goLogin() {
  router.push('/login')
}
</script>

<template>
  <div class="min-h-screen flex items-center justify-center bg-background p-4">
    <div class="w-full max-w-[420px] rounded-xl border border-border bg-card shadow-lg p-8 space-y-5">
      <div class="text-center">
        <h1 class="text-xl font-semibold text-foreground">{{ t('passwordReset.title') }}</h1>
        <p v-if="tenantId" class="text-xs text-muted-foreground mt-1">{{ t('passwordReset.tenantPrefix') }} {{ tenantId }}</p>
      </div>

      <template v-if="state === 'checking'">
        <p class="text-sm text-muted-foreground text-center">{{ t('passwordReset.message.checking') }}</p>
      </template>

      <template v-else-if="state === 'invalid'">
        <div class="text-center text-destructive">
          <XCircle :size="40" class="mx-auto mb-2" />
          <p class="text-sm">{{ errorMsg }}</p>
          <button class="mt-4 text-xs underline text-muted-foreground hover:text-foreground" @click="goLogin">
            {{ t('passwordReset.button.goLogin') }}
          </button>
        </div>
      </template>

      <template v-else-if="state === 'done'">
        <div class="text-center">
          <CheckCircle2 :size="40" class="mx-auto mb-2 text-emerald-600" />
          <p class="text-sm text-foreground">{{ t('passwordReset.message.done') }}</p>
          <button class="mt-5 w-full h-10 bg-primary text-primary-foreground font-medium rounded-lg text-sm hover:bg-primary/90"
                  @click="goLogin">
            {{ t('passwordReset.button.goLogin') }}
          </button>
        </div>
      </template>

      <template v-else>
        <div v-if="errorMsg" class="p-3 rounded-lg bg-destructive/10 text-destructive text-xs border border-destructive/20">
          {{ errorMsg }}
        </div>

        <p class="text-xs text-muted-foreground">{{ t('passwordReset.intro') }}</p>

        <form @submit.prevent="submit" class="space-y-4">
          <div>
            <label class="block text-sm font-medium text-foreground mb-1.5">{{ t('passwordReset.passwordLabel') }}</label>
            <div class="relative">
              <div class="absolute left-3 top-1/2 -translate-y-1/2 text-muted-foreground">
                <Lock :size="16" />
              </div>
              <input v-model="password" type="password" minlength="8" maxlength="128" required
                     :placeholder="t('passwordReset.passwordPlaceholder')"
                     class="w-full h-10 pl-10 pr-4 border border-input rounded-lg bg-background text-foreground text-sm placeholder:text-muted-foreground focus:outline-none focus:ring-2 focus:ring-ring transition" />
            </div>
          </div>

          <div>
            <label class="block text-sm font-medium text-foreground mb-1.5">{{ t('passwordReset.passwordConfirmLabel') }}</label>
            <div class="relative">
              <div class="absolute left-3 top-1/2 -translate-y-1/2 text-muted-foreground">
                <Lock :size="16" />
              </div>
              <input v-model="passwordConfirm" type="password" minlength="8" maxlength="128" required
                     :placeholder="t('passwordReset.passwordConfirmPlaceholder')"
                     class="w-full h-10 pl-10 pr-4 border border-input rounded-lg bg-background text-foreground text-sm placeholder:text-muted-foreground focus:outline-none focus:ring-2 focus:ring-ring transition" />
            </div>
          </div>

          <button type="submit" :disabled="state === 'submitting'"
                  class="w-full h-10 bg-primary text-primary-foreground font-medium rounded-lg text-sm hover:bg-primary/90 active:bg-primary/80 transition-colors duration-200 disabled:opacity-50 disabled:cursor-not-allowed">
            {{ state === 'submitting' ? t('passwordReset.button.submitting') : t('passwordReset.button.submit') }}
          </button>
        </form>
      </template>
    </div>
  </div>
</template>
