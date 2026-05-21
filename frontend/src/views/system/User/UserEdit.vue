<script setup>
import { ref, reactive, watch, computed } from 'vue'
import { useI18n } from 'vue-i18n'
import Drawer from '@/components/ui/Drawer.vue'
import Input from '@/components/ui/Input.vue'
import Select from '@/components/ui/Select.vue'
import Checkbox from '@/components/ui/Checkbox.vue'
import { toast } from '@/composables/useToast'
import { addUserApi, updateUserApi, getUserRolesApi, assignUserRolesApi } from '../../../../services/user'
import { getRoleListApi } from '../../../../services/role'

const { t } = useI18n()

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

const statusOptions = computed(() => [
  { label: t('common.status.active'), value: 1 },
  { label: t('common.status.inactive'), value: 0 }
])

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
      if (r.data.code !== 0) { toast.error(r.data.msg || t('user.edit.message.updateFailed')); return }
      userId = props.user.id
    } else {
      const r = await addUserApi(form)
      if (r.data.code !== 0) { toast.error(r.data.msg || t('user.edit.message.createFailed')); return }
      userId = r.data.data
    }
    // assign roles
    const r2 = await assignUserRolesApi(userId, selectedRoleIds.value)
    if (r2.data.code !== 0) { toast.error(r2.data.msg || t('user.edit.message.assignRolesFailed')); return }
    toast.success(t('common.message.saveSuccessful'))
    emit('saved')
    emit('update:open', false)
  } catch (e) { toast.error(e.message) }
  finally { saving.value = false }
}
</script>

<template>
  <Drawer
    :open="open"
    :title="isEdit ? t('user.edit.titleEdit') : t('user.edit.titleCreate')"
    width="max-w-lg"
    @update:open="(v) => emit('update:open', v)"
  >
    <div class="space-y-4">
      <div v-if="isLocked"
           class="text-xs px-3 py-2 rounded bg-amber-100 border border-amber-300 text-amber-900">
        {{ t('user.edit.lockedHint') }}
      </div>
      <div>
        <label class="text-xs text-muted-foreground block mb-1">{{ t('user.edit.label.username') }} <span class="text-destructive">*</span></label>
        <Input v-model="form.username" :disabled="isEdit" />
      </div>
      <div v-if="!isEdit">
        <label class="text-xs text-muted-foreground block mb-1">{{ t('user.edit.label.password') }} <span class="text-destructive">*</span></label>
        <Input v-model="form.password" type="password" :placeholder="t('user.edit.placeholder.password')" />
      </div>
      <div>
        <label class="text-xs text-muted-foreground block mb-1">{{ t('user.edit.label.displayName') }}</label>
        <Input v-model="form.displayName" :disabled="isLocked" />
      </div>
      <div class="grid grid-cols-2 gap-3">
        <div>
          <label class="text-xs text-muted-foreground block mb-1">{{ t('user.edit.label.email') }}</label>
          <Input v-model="form.email" type="email" :disabled="isLocked" />
        </div>
        <div>
          <label class="text-xs text-muted-foreground block mb-1">{{ t('user.edit.label.userNo') }}</label>
          <Input v-model="form.userNo" :disabled="isLocked" />
        </div>
      </div>
      <div class="grid grid-cols-2 gap-3">
        <div>
          <label class="text-xs text-muted-foreground block mb-1">{{ t('user.edit.label.deptId') }}</label>
          <Input v-model="form.deptId" :placeholder="t('common.placeholder.deptId')" :disabled="isLocked" />
        </div>
        <div>
          <label class="text-xs text-muted-foreground block mb-1">{{ t('user.edit.label.status') }}</label>
          <Select v-model="form.status" :options="statusOptions" :disabled="isLocked" />
        </div>
      </div>

      <div>
        <label class="text-xs text-muted-foreground block mb-1">{{ t('user.edit.label.roles') }}</label>
        <div class="border border-border rounded p-2 max-h-48 overflow-y-auto space-y-1"
             :class="isLocked && 'opacity-60 pointer-events-none'">
          <Checkbox v-for="r in allRoles" :key="r.id"
                    v-model="selectedRoleIds" :value="r.id" :disabled="isLocked"
                    class="px-2 py-1 hover:bg-muted rounded text-sm">
            <span class="font-mono">{{ r.code }}</span>
            <span class="text-muted-foreground">— {{ r.name }}</span>
          </Checkbox>
          <div v-if="!allRoles.length" class="text-xs text-muted-foreground p-2">{{ t('user.edit.message.noRoles') }}</div>
        </div>
      </div>
    </div>

    <template #footer>
      <div class="flex justify-end gap-2">
        <button class="h-9 px-3 rounded border border-border text-sm"
                @click="emit('update:open', false)">{{ t('common.button.cancel') }}</button>
        <button class="h-9 px-3 rounded bg-primary text-primary-foreground text-sm disabled:opacity-50 disabled:cursor-not-allowed"
                :disabled="saving || isLocked"
                :title="isLocked ? t('user.tooltip.editDisabled') : ''"
                @click="save">
          {{ saving ? t('user.edit.message.saving') : t('common.button.save') }}
        </button>
      </div>
    </template>
  </Drawer>
</template>
