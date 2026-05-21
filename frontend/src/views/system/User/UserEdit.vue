<script setup>
import { ref, reactive, watch, computed } from 'vue'
import Drawer from '@/components/ui/Drawer.vue'
import Input from '@/components/ui/Input.vue'
import Select from '@/components/ui/Select.vue'
import Checkbox from '@/components/ui/Checkbox.vue'
import { toast } from '@/composables/useToast'
import { addUserApi, updateUserApi, getUserRolesApi, assignUserRolesApi } from '../../../../services/user'
import { getRoleListApi } from '../../../../services/role'

const props = defineProps({
  open: Boolean,
  user: { type: Object, default: null }
})
const emit = defineEmits(['update:open', 'saved'])

const isEdit = computed(() => !!props.user)
const isLocked = computed(() => isEdit.value && props.user?.username === 'admin')

const form = reactive({
  username: '',
  password: '',
  email: '',
  userNo: '',
  displayName: '',
  deptId: '',
  status: 1
})

const allRoles = ref([])
const selectedRoleIds = ref([])
const saving = ref(false)

const statusOptions = [
  { label: '有効', value: 1 },
  { label: '無効', value: 0 }
]

watch(() => props.open, async (open) => {
  if (!open) return
  Object.assign(form, {
    username: props.user?.username || '',
    password: '',
    email: props.user?.email || '',
    userNo: props.user?.userNo || '',
    displayName: props.user?.displayName || '',
    deptId: props.user?.deptId || '',
    status: props.user?.status ?? 1
  })
  try {
    const r = await getRoleListApi({ page: 1, size: 100 })
    if (r.data.code === 0) allRoles.value = r.data.data.records || []
  } catch { /* ignore */ }
  if (props.user) {
    try {
      const r = await getUserRolesApi(props.user.id)
      if (r.data.code === 0) selectedRoleIds.value = r.data.data || []
    } catch { selectedRoleIds.value = [] }
  } else {
    selectedRoleIds.value = []
  }
})

async function save() {
  saving.value = true
  try {
    let userId
    if (isEdit.value) {
      const body = {
        email: form.email, userNo: form.userNo,
        displayName: form.displayName, deptId: form.deptId, status: form.status
      }
      const r = await updateUserApi(props.user.id, body)
      if (r.data.code !== 0) { toast.error(r.data.msg || '更新失敗'); return }
      userId = props.user.id
    } else {
      const r = await addUserApi(form)
      if (r.data.code !== 0) { toast.error(r.data.msg || '作成失敗'); return }
      userId = r.data.data
    }
    // assign roles
    const r2 = await assignUserRolesApi(userId, selectedRoleIds.value)
    if (r2.data.code !== 0) { toast.error(r2.data.msg || 'ロール割り当て失敗'); return }
    toast.success('保存しました')
    emit('saved')
    emit('update:open', false)
  } catch (e) { toast.error(e.message) }
  finally { saving.value = false }
}
</script>

<template>
  <Drawer
    :open="open"
    :title="isEdit ? 'ユーザー編集' : 'ユーザー新規'"
    width="max-w-lg"
    @update:open="(v) => emit('update:open', v)"
  >
    <div class="space-y-4">
      <div v-if="isLocked"
           class="text-xs px-3 py-2 rounded bg-amber-100 border border-amber-300 text-amber-900">
        内蔵 admin ユーザーは読み取り専用です。パスワードの変更のみ可能（『パスワードリセット』API 経由）。
      </div>
      <div>
        <label class="text-xs text-muted-foreground block mb-1">ユーザー名 <span class="text-destructive">*</span></label>
        <Input v-model="form.username" :disabled="isEdit" />
      </div>
      <div v-if="!isEdit">
        <label class="text-xs text-muted-foreground block mb-1">パスワード <span class="text-destructive">*</span></label>
        <Input v-model="form.password" type="password" placeholder="8 文字以上 / 4 種類の文字種" />
      </div>
      <div>
        <label class="text-xs text-muted-foreground block mb-1">表示名</label>
        <Input v-model="form.displayName" :disabled="isLocked" />
      </div>
      <div class="grid grid-cols-2 gap-3">
        <div>
          <label class="text-xs text-muted-foreground block mb-1">メール</label>
          <Input v-model="form.email" type="email" :disabled="isLocked" />
        </div>
        <div>
          <label class="text-xs text-muted-foreground block mb-1">番号</label>
          <Input v-model="form.userNo" :disabled="isLocked" />
        </div>
      </div>
      <div class="grid grid-cols-2 gap-3">
        <div>
          <label class="text-xs text-muted-foreground block mb-1">部署 ID</label>
          <Input v-model="form.deptId" placeholder="dept id" :disabled="isLocked" />
        </div>
        <div>
          <label class="text-xs text-muted-foreground block mb-1">状態</label>
          <Select v-model="form.status" :options="statusOptions" :disabled="isLocked" />
        </div>
      </div>

      <div>
        <label class="text-xs text-muted-foreground block mb-1">ロール</label>
        <div class="border border-border rounded p-2 max-h-48 overflow-y-auto space-y-1"
             :class="isLocked && 'opacity-60 pointer-events-none'">
          <Checkbox v-for="r in allRoles" :key="r.id"
                    v-model="selectedRoleIds" :value="r.id" :disabled="isLocked"
                    class="px-2 py-1 hover:bg-muted rounded text-sm">
            <span class="font-mono">{{ r.code }}</span>
            <span class="text-muted-foreground">— {{ r.name }}</span>
          </Checkbox>
          <div v-if="!allRoles.length" class="text-xs text-muted-foreground p-2">ロールがありません</div>
        </div>
      </div>
    </div>

    <template #footer>
      <div class="flex justify-end gap-2">
        <button class="h-9 px-3 rounded border border-border text-sm"
                @click="emit('update:open', false)">キャンセル</button>
        <button class="h-9 px-3 rounded bg-primary text-primary-foreground text-sm disabled:opacity-50 disabled:cursor-not-allowed"
                :disabled="saving || isLocked"
                :title="isLocked ? '内蔵ユーザーは編集不可' : ''"
                @click="save">
          {{ saving ? '保存中...' : '保存' }}
        </button>
      </div>
    </template>
  </Drawer>
</template>
