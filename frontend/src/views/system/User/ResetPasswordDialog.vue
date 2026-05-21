<script setup>
import { ref, watch } from 'vue'
import Dialog from '@/components/ui/Dialog.vue'
import Input from '@/components/ui/Input.vue'
import { toast } from '@/composables/useToast'
import { resetPasswordApi } from '../../../../services/user'

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
    toast.error('パスワードは 8 文字以上で入力してください')
    return
  }
  if (newPassword.value !== confirmPassword.value) {
    toast.error('確認用パスワードが一致しません')
    return
  }
  saving.value = true
  try {
    const r = await resetPasswordApi({ username: props.user.username, newPassword: newPassword.value })
    if (r.data.code === 0) {
      toast.success('パスワードをリセットしました')
      emit('update:open', false)
    } else {
      // 700 / 701 etc → backend already supplies friendly message (HIBP, length, etc.)
      toast.error(r.data.msg || '失敗')
    }
  } catch (e) { toast.error(e.message) }
  finally { saving.value = false }
}
</script>

<template>
  <Dialog :open="open" title="パスワードリセット" @update:open="(v) => emit('update:open', v)">
    <div class="space-y-3">
      <div class="text-sm text-muted-foreground">
        ユーザー <span class="font-mono text-foreground">{{ user?.username }}</span> のパスワードを再設定します。
      </div>
      <div>
        <label class="text-xs text-muted-foreground block mb-1">新しいパスワード <span class="text-destructive">*</span></label>
        <Input v-model="newPassword" type="password" placeholder="8 文字以上 / 4 種類の文字種" />
      </div>
      <div>
        <label class="text-xs text-muted-foreground block mb-1">確認用パスワード <span class="text-destructive">*</span></label>
        <Input v-model="confirmPassword" type="password" placeholder="同じパスワードを再入力" />
      </div>
      <p class="text-xs text-muted-foreground">
        ※ 公開侵害コーパス（HIBP）に登録されたパスワードは拒否されます。
      </p>
    </div>

    <template #footer>
      <div class="flex justify-end gap-2">
        <button class="h-9 px-3 rounded border border-border text-sm"
                @click="emit('update:open', false)">キャンセル</button>
        <button class="h-9 px-3 rounded bg-primary text-primary-foreground text-sm disabled:opacity-50"
                :disabled="saving" @click="save">
          {{ saving ? '保存中...' : 'リセット' }}
        </button>
      </div>
    </template>
  </Dialog>
</template>
