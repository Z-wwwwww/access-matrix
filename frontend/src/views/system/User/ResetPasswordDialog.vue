<script setup>
import { ref, watch } from 'vue'
import { useI18n } from 'vue-i18n'
import Dialog from '@/components/ui/Dialog.vue'
import Input from '@/components/ui/Input.vue'
import { toast } from '@/composables/useToast'
import { resetPasswordApi } from '../../../../services/user'

const { t } = useI18n()

const props = defineProps({
  open: Boolean,
  user: { type: Object, default: null }
})
const emit = defineEmits(['update:open'])

const newPassword = ref('')
const confirmPassword = ref('')
const saving = ref(false)

watch(() => props.open, (v) => {
  if (v) { newPassword.value = ''; confirmPassword.value = '' }
})

async function save() {
  if (!newPassword.value || newPassword.value.length < 8) {
    toast.error(t('user.resetPassword.error.tooShort'))
    return
  }
  if (newPassword.value !== confirmPassword.value) {
    toast.error(t('user.resetPassword.error.mismatch'))
    return
  }
  saving.value = true
  try {
    const r = await resetPasswordApi({ username: props.user.username, newPassword: newPassword.value })
    if (r.data.code === 0) {
      toast.success(t('user.resetPassword.message.success'))
      emit('update:open', false)
    } else {
      // 700 / 701 etc → backend already supplies friendly message (HIBP, length, etc.)
      toast.error(r.data.msg || t('user.resetPassword.message.failed'))
    }
  } catch (e) { toast.error(e.message) }
  finally { saving.value = false }
}
</script>

<template>
  <Dialog :open="open" :title="t('user.resetPassword.title')" @update:open="(v) => emit('update:open', v)">
    <div class="space-y-3">
      <div class="text-sm text-muted-foreground">
        {{ t('user.resetPassword.label.user') }} <span class="font-mono text-foreground">{{ user?.username }}</span>
      </div>
      <div>
        <label class="text-xs text-muted-foreground block mb-1">{{ t('user.resetPassword.label.newPassword') }} <span class="text-destructive">*</span></label>
        <Input v-model="newPassword" type="password" :placeholder="t('user.resetPassword.placeholder.value')" />
      </div>
      <div>
        <label class="text-xs text-muted-foreground block mb-1">{{ t('user.resetPassword.label.confirmPassword') }} <span class="text-destructive">*</span></label>
        <Input v-model="confirmPassword" type="password" :placeholder="t('user.resetPassword.placeholder.confirm')" />
      </div>
      <p class="text-xs text-muted-foreground">
        {{ t('user.resetPassword.hint') }}
      </p>
    </div>

    <template #footer>
      <div class="flex justify-end gap-2">
        <button class="h-9 px-3 rounded border border-border text-sm"
                @click="emit('update:open', false)">{{ t('common.button.cancel') }}</button>
        <button class="h-9 px-3 rounded bg-primary text-primary-foreground text-sm disabled:opacity-50"
                :disabled="saving" @click="save">
          {{ saving ? t('user.resetPassword.message.saving') : t('user.resetPassword.button.reset') }}
        </button>
      </div>
    </template>
  </Dialog>
</template>
