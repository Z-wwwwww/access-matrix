<script setup>
import { reactive, ref, watch } from 'vue'
import { useI18n } from 'vue-i18n'
import Dialog from '@/components/ui/Dialog.vue'
import Input from '@/components/ui/Input.vue'
import { toast } from '@/composables/useToast'
import { updatePwdApi } from '../../../services/auth'

const { t } = useI18n()

const props = defineProps({
  open: {
    type: Boolean,
    default: false
  }
})

const emit = defineEmits(['update:open'])

const form = reactive({
  oldPassword: '',
  password: '',
  confirmPassword: ''
})

const errors = reactive({})
const saving = ref(false)

function clearError(key) {
  if (errors[key]) delete errors[key]
}

function onConfirmPasswordInput() {
  clearError('confirmPassword')
  delete errors.confirmPasswordMismatch
}

function resetForm() {
  form.oldPassword = ''
  form.password = ''
  form.confirmPassword = ''
  Object.keys(errors).forEach((k) => delete errors[k])
  saving.value = false
}

function validate() {
  Object.keys(errors).forEach((k) => delete errors[k])
  if (!form.oldPassword) errors.oldPassword = true
  if (!form.password) errors.password = true
  if (!form.confirmPassword) errors.confirmPassword = true
  if (form.confirmPassword && form.password && form.confirmPassword !== form.password) {
    errors.confirmPassword = true
    errors.confirmPasswordMismatch = true
  }
  return Object.keys(errors).length === 0
}

async function handleSave() {
  if (!validate()) return
  saving.value = true
  try {
    const res = await updatePwdApi({
      oldPassword: form.oldPassword,
      newPassword: form.password
    })
    if (res.data.code === 0) {
      toast.success(res.data.msg || t('common.message.saveSuccessful'))
      emit('update:open', false)
    } else {
      toast.error(res.data.msg)
    }
  } finally {
    saving.value = false
  }
}

function handleCancel() {
  emit('update:open', false)
}

watch(() => props.open, (val) => {
  if (!val) resetForm()
})
</script>

<template>
  <Dialog
    :open="open"
    :title="t('layout.header.password')"
    width="max-w-md"
    @update:open="(v) => emit('update:open', v)"
  >
    <div class="space-y-4">
      <div data-field="oldPassword" class="grid grid-cols-12 gap-3 items-center">
        <label class="col-span-4 text-sm text-foreground text-right">
          <span class="text-destructive mr-0.5">*</span>{{ t('password.oldPassword') }}
        </label>
        <div class="col-span-8">
          <Input
            v-model="form.oldPassword"
            type="password"
            :placeholder="t('password.oldPassword')"
            :error="!!errors.oldPassword"
            @update:model-value="clearError('oldPassword')"
          />
        </div>
      </div>

      <div data-field="password" class="grid grid-cols-12 gap-3 items-center">
        <label class="col-span-4 text-sm text-foreground text-right">
          <span class="text-destructive mr-0.5">*</span>{{ t('password.password') }}
        </label>
        <div class="col-span-8">
          <Input
            v-model="form.password"
            type="password"
            :placeholder="t('password.password')"
            :error="!!errors.password"
            @update:model-value="clearError('password')"
          />
        </div>
      </div>

      <div data-field="confirmPassword" class="grid grid-cols-12 gap-3 items-start">
        <label class="col-span-4 pt-2 text-sm text-foreground text-right">
          <span class="text-destructive mr-0.5">*</span>{{ t('password.confirmPassword') }}
        </label>
        <div class="col-span-8">
          <Input
            v-model="form.confirmPassword"
            type="password"
            :placeholder="t('password.confirmPassword')"
            :error="!!errors.confirmPassword"
            @update:model-value="onConfirmPasswordInput"
          />
          <p v-if="errors.confirmPasswordMismatch" class="mt-1 text-xs text-destructive">
            {{ t('password.message.inconsistent') }}
          </p>
        </div>
      </div>
    </div>

    <template #footer>
      <button
        class="inline-flex items-center gap-1.5 h-9 px-4 rounded-lg text-sm font-medium bg-muted text-foreground hover:bg-muted/80 transition-colors"
        @click="handleCancel"
      >
        {{ t('common.button.cancel') }}
      </button>
      <button
        class="inline-flex items-center gap-1.5 h-9 px-4 rounded-lg text-sm font-medium bg-primary text-primary-foreground hover:bg-primary/90 transition-colors disabled:opacity-60"
        :disabled="saving"
        @click="handleSave"
      >
        {{ t('common.button.save') }}
      </button>
    </template>
  </Dialog>
</template>
