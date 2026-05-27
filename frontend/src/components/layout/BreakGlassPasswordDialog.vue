<script setup>
/**
 * Break-glass credential management dialog. Only ever shown to users
 * holding the SUPER_ADMIN role (`*:*` permission) — AppHeader gates the
 * trigger on `hasPermission('*:*')` and the backend re-verifies the role
 * on every call.
 *
 * The dialog leads with a deliberate explanation of what this password
 * IS and IS NOT, because the concept doesn't map onto users' existing
 * mental model: most people see "password reset" and assume "this changes
 * my login password". Break-glass is a separate credential, used only
 * when Keycloak is unreachable, and naming it badly would result in
 * super-admins setting their daily SSO password here (wrong column,
 * wrong system, no effect) and getting confused when the change doesn't
 * take.
 */
import { ref, watch, computed } from 'vue'
import { useI18n } from 'vue-i18n'
import { ShieldAlert, KeyRound, CheckCircle2, AlertCircle } from 'lucide-vue-next'
import Dialog from '@/components/ui/Dialog.vue'
import Input from '@/components/ui/Input.vue'
import { toast } from '@/composables/useToast'
import { getBreakGlassStatusApi, setBreakGlassPasswordApi } from '../../../services/breakGlass'

const { t } = useI18n()

const props = defineProps({
  open: { type: Boolean, default: false }
})
const emit = defineEmits(['update:open'])

const configured = ref(null)   // null = unknown, true / false after status load
const password = ref('')
const passwordConfirm = ref('')
const saving = ref(false)
const errorMsg = ref('')

const canSubmit = computed(() =>
  password.value.length >= 8 && password.value === passwordConfirm.value
)

watch(() => props.open, async (v) => {
  if (!v) return
  password.value = ''
  passwordConfirm.value = ''
  errorMsg.value = ''
  configured.value = null
  try {
    const r = await getBreakGlassStatusApi()
    if (r.data.code === 0) {
      configured.value = !!r.data.data?.configured
    }
  } catch { /* status is non-critical — let user proceed without it */ }
})

async function save() {
  errorMsg.value = ''
  if (password.value.length < 8) {
    errorMsg.value = t('breakGlass.error.tooShort')
    return
  }
  if (password.value !== passwordConfirm.value) {
    errorMsg.value = t('breakGlass.error.mismatch')
    return
  }
  saving.value = true
  try {
    const r = await setBreakGlassPasswordApi(password.value)
    if (r.data.code === 0) {
      toast.success(t('breakGlass.message.saved'))
      emit('update:open', false)
    } else {
      // 700 / 701 → backend supplied a friendly message (HIBP, policy, etc.)
      errorMsg.value = r.data.msg || t('breakGlass.error.saveFailed')
    }
  } catch (e) {
    errorMsg.value = e.message || t('breakGlass.error.saveFailed')
  } finally {
    saving.value = false
  }
}
</script>

<template>
  <Dialog
    :open="open"
    :title="t('breakGlass.title')"
    width="max-w-lg"
    @update:open="(v) => emit('update:open', v)"
  >
    <div class="space-y-4 text-sm text-foreground">
      <!-- What is this? -->
      <div class="rounded-lg border border-amber-500/30 bg-amber-500/5 p-3 text-xs leading-relaxed text-foreground">
        <div class="flex items-start gap-2">
          <ShieldAlert :size="16" class="shrink-0 mt-0.5 text-amber-600 dark:text-amber-400" />
          <div class="space-y-2">
            <p class="font-medium">{{ t('breakGlass.intro.what') }}</p>
            <p class="text-muted-foreground">{{ t('breakGlass.intro.howDifferent') }}</p>
            <p class="text-muted-foreground">{{ t('breakGlass.intro.whenUsed') }}</p>
          </div>
        </div>
      </div>

      <!-- Status -->
      <div v-if="configured !== null"
           class="flex items-center gap-2 px-3 py-2 rounded-lg border text-xs"
           :class="configured
             ? 'border-emerald-500/30 bg-emerald-500/5 text-emerald-700 dark:text-emerald-300'
             : 'border-amber-500/30 bg-amber-500/5 text-amber-700 dark:text-amber-300'">
        <CheckCircle2 v-if="configured" :size="14" />
        <AlertCircle v-else :size="14" />
        <span>{{ configured ? t('breakGlass.status.configured') : t('breakGlass.status.notConfigured') }}</span>
      </div>

      <!-- Error -->
      <div v-if="errorMsg"
           class="p-3 rounded-lg bg-destructive/10 text-destructive text-xs border border-destructive/20">
        {{ errorMsg }}
      </div>

      <!-- Form -->
      <div class="space-y-3">
        <div>
          <label class="block text-xs font-medium text-foreground mb-1.5">
            {{ t('breakGlass.label.newPassword') }} <span class="text-destructive">*</span>
          </label>
          <Input
            v-model="password"
            type="password"
            minlength="8"
            maxlength="128"
            :placeholder="t('breakGlass.placeholder.newPassword')"
          />
        </div>
        <div>
          <label class="block text-xs font-medium text-foreground mb-1.5">
            {{ t('breakGlass.label.confirmPassword') }} <span class="text-destructive">*</span>
          </label>
          <Input
            v-model="passwordConfirm"
            type="password"
            minlength="8"
            maxlength="128"
            :placeholder="t('breakGlass.placeholder.confirmPassword')"
          />
        </div>
        <p class="text-xs text-muted-foreground">
          {{ t('breakGlass.hint.storeSafely') }}
        </p>
      </div>
    </div>

    <template #footer>
      <div class="flex justify-end gap-2">
        <button
          class="h-9 px-3 rounded border border-border text-sm hover:bg-muted transition-colors"
          @click="emit('update:open', false)"
        >
          {{ t('common.button.cancel') }}
        </button>
        <button
          class="h-9 px-4 rounded bg-primary text-primary-foreground text-sm font-medium disabled:opacity-50 disabled:cursor-not-allowed hover:bg-primary/90 inline-flex items-center gap-1.5 transition-colors"
          :disabled="!canSubmit || saving"
          @click="save"
        >
          <KeyRound :size="14" />
          {{ saving ? t('breakGlass.button.saving') : t('breakGlass.button.save') }}
        </button>
      </div>
    </template>
  </Dialog>
</template>
