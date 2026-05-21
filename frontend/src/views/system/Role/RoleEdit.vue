<script setup>
import { ref, reactive, watch, computed } from 'vue'
import { useI18n } from 'vue-i18n'
import Drawer from '@/components/ui/Drawer.vue'
import Input from '@/components/ui/Input.vue'
import Select from '@/components/ui/Select.vue'
import Checkbox from '@/components/ui/Checkbox.vue'
import Tabs from '@/components/ui/Tabs.vue'
import { TabsContent } from 'radix-vue'
import { toast } from '@/composables/useToast'
import {
  addRoleApi, updateRoleApi,
  getRolePermissionsApi, bindRolePermissionsApi,
  getRoleMenusApi, bindRoleMenusApi,
  getRoleDeptsApi, bindRoleDeptsApi
} from '../../../../services/role'
import { getPermissionsByModuleApi } from '../../../../services/permission'
import { getMenuIndexApi } from '../../../../services/menu'
import { getDeptTreeApi } from '../../../../services/dept'

const { t } = useI18n()

const props = defineProps({
  open: Boolean,
  role: { type: Object, default: null }
})
const emit = defineEmits(['update:open', 'saved'])

const isEdit = computed(() => !!props.role)
const isLocked = computed(() => isEdit.value && props.role?.isBuiltIn === 1)
const tab = ref('basic')

const form = reactive({
  code: '',
  name: '',
  description: '',
  dataScope: 4,
  sortOrder: 0,
  status: 1
})

const scopeOptions = computed(() => [
  { label: t('role.edit.option.scope.all'), value: 1 },
  { label: t('role.edit.option.scope.deptAndSub'), value: 2 },
  { label: t('role.edit.option.scope.deptOnly'), value: 3 },
  { label: t('role.edit.option.scope.self'), value: 4 },
  { label: t('role.edit.option.scope.custom'), value: 5 }
])
const statusOptions = computed(() => [
  { label: t('common.status.active'), value: 1 },
  { label: t('common.status.inactive'), value: 0 }
])

const permsByModule = ref({})
const selectedPermIds = ref([])

const flatMenus = ref([])
const selectedMenuIds = ref([])

const flatDepts = ref([])
const selectedDeptIds = ref([])

const saving = ref(false)
const tabItems = computed(() => [
  { value: 'basic',  label: t('role.edit.tab.basic') },
  { value: 'perms',  label: t('role.edit.tab.permissions') },
  { value: 'menus',  label: t('role.edit.tab.menus') },
  ...(form.dataScope === 5 ? [{ value: 'depts', label: t('role.edit.tab.depts') }] : [])
])

watch(() => props.open, async (open) => {
  if (!open) return
  tab.value = 'basic'
  Object.assign(form, {
    code: props.role?.code || '',
    name: props.role?.name || '',
    description: props.role?.description || '',
    dataScope: props.role?.dataScope ?? 4,
    sortOrder: props.role?.sortOrder ?? 0,
    status: props.role?.status ?? 1
  })
  selectedPermIds.value = []
  selectedMenuIds.value = []
  selectedDeptIds.value = []

  // Load reference data
  try {
    const [pRes, mRes, dRes] = await Promise.all([
      getPermissionsByModuleApi(),
      getMenuIndexApi(),
      getDeptTreeApi()
    ])
    if (pRes.data.code === 0) permsByModule.value = pRes.data.data || {}
    if (mRes.data.code === 0) flatMenus.value = mRes.data.data || []
    if (dRes.data.code === 0) flatDepts.value = flatten(dRes.data.data || [])
  } catch { /* ignore */ }

  if (props.role) {
    try {
      const [p, m, d] = await Promise.all([
        getRolePermissionsApi(props.role.id),
        getRoleMenusApi(props.role.id),
        getRoleDeptsApi(props.role.id)
      ])
      if (p.data.code === 0) selectedPermIds.value = p.data.data || []
      if (m.data.code === 0) selectedMenuIds.value = m.data.data || []
      if (d.data.code === 0) selectedDeptIds.value = d.data.data || []
    } catch { /* ignore */ }
  }
})

function flatten(tree, level = 0, out = []) {
  for (const n of tree) {
    out.push({ ...n, level })
    if (n.children?.length) flatten(n.children, level + 1, out)
  }
  return out
}

async function save() {
  saving.value = true
  try {
    let roleId
    if (isEdit.value) {
      const body = { name: form.name, description: form.description, dataScope: form.dataScope, sortOrder: form.sortOrder, status: form.status }
      const r = await updateRoleApi(props.role.id, body)
      if (r.data.code !== 0) { toast.error(r.data.msg || t('role.edit.message.updateFailed')); return }
      roleId = props.role.id
    } else {
      const r = await addRoleApi(form)
      if (r.data.code !== 0) { toast.error(r.data.msg || t('role.edit.message.createFailed')); return }
      roleId = r.data.data
    }
    await Promise.all([
      bindRolePermissionsApi(roleId, selectedPermIds.value),
      bindRoleMenusApi(roleId, selectedMenuIds.value),
      form.dataScope === 5
        ? bindRoleDeptsApi(roleId, selectedDeptIds.value)
        : Promise.resolve()
    ])
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
    :title="isEdit ? t('role.edit.titleEdit') : t('role.edit.titleCreate')"
    width="max-w-2xl"
    @update:open="(v) => emit('update:open', v)"
  >
    <div v-if="isLocked"
         class="mb-3 text-xs px-3 py-2 rounded bg-amber-100 border border-amber-300 text-amber-900">
      {{ t('role.edit.lockedHint') }}
    </div>
    <Tabs v-model="tab" :items="tabItems">
      <TabsContent value="basic" force-mount>
        <div class="space-y-4 pt-2">
          <div class="grid grid-cols-2 gap-3">
            <div>
              <label class="text-xs text-muted-foreground block mb-1">{{ t('role.edit.label.code') }} <span class="text-destructive">*</span></label>
              <Input v-model="form.code" :disabled="isEdit || isLocked" placeholder="PMS_FRONT_DESK" />
            </div>
            <div>
              <label class="text-xs text-muted-foreground block mb-1">{{ t('role.edit.label.name') }} <span class="text-destructive">*</span></label>
              <Input v-model="form.name" :disabled="isLocked" />
            </div>
          </div>
          <div>
            <label class="text-xs text-muted-foreground block mb-1">{{ t('role.edit.label.description') }}</label>
            <Input v-model="form.description" :disabled="isLocked" />
          </div>
          <div class="grid grid-cols-2 gap-3">
            <div>
              <label class="text-xs text-muted-foreground block mb-1">{{ t('role.edit.label.dataScope') }}</label>
              <Select v-model="form.dataScope" :options="scopeOptions" :disabled="isLocked" />
            </div>
            <div>
              <label class="text-xs text-muted-foreground block mb-1">{{ t('role.edit.label.status') }}</label>
              <Select v-model="form.status" :options="statusOptions" :disabled="isLocked" />
            </div>
          </div>
        </div>
      </TabsContent>

      <TabsContent value="perms" force-mount>
        <div class="space-y-3 pt-2" :class="isLocked && 'opacity-60 pointer-events-none'">
          <div v-for="(perms, module) in permsByModule" :key="module">
            <div class="text-xs font-semibold text-muted-foreground mb-1 uppercase">{{ module || 'misc' }}</div>
            <div class="grid grid-cols-2 md:grid-cols-3 gap-1 mb-2">
              <Checkbox v-for="p in perms" :key="p.id"
                        v-model="selectedPermIds" :value="p.id" :disabled="isLocked"
                        class="px-2 py-1 hover:bg-muted rounded text-xs">
                <span class="font-mono">{{ p.code }}</span>
              </Checkbox>
            </div>
          </div>
          <div v-if="!Object.keys(permsByModule).length" class="text-sm text-muted-foreground p-4">
            {{ t('role.edit.message.noPermissions') }}
          </div>
        </div>
      </TabsContent>

      <TabsContent value="menus" force-mount>
        <div class="pt-2 max-h-96 overflow-y-auto" :class="isLocked && 'opacity-60 pointer-events-none'">
          <Checkbox v-for="m in flatMenus" :key="m.id"
                    v-model="selectedMenuIds" :value="m.id" :disabled="isLocked"
                    class="px-2 py-1 hover:bg-muted rounded text-sm">
            <span class="font-mono text-xs">{{ m.code }}</span>
            <span class="text-muted-foreground">— {{ m.title }}</span>
          </Checkbox>
          <div v-if="!flatMenus.length" class="text-sm text-muted-foreground p-4">
            {{ t('role.edit.message.noMenus') }}
          </div>
        </div>
      </TabsContent>

      <TabsContent v-if="form.dataScope === 5" value="depts" force-mount>
        <div class="pt-2 max-h-96 overflow-y-auto" :class="isLocked && 'opacity-60 pointer-events-none'">
          <Checkbox v-for="d in flatDepts" :key="d.id"
                    v-model="selectedDeptIds" :value="d.id" :disabled="isLocked"
                    class="px-2 py-1 hover:bg-muted rounded text-sm"
                    :style="{ paddingLeft: 8 + d.level * 20 + 'px' }">
            <span>{{ d.name }}</span>
            <span class="text-muted-foreground text-xs font-mono">{{ d.code }}</span>
          </Checkbox>
          <div v-if="!flatDepts.length" class="text-sm text-muted-foreground p-4">
            {{ t('role.edit.message.noDepts') }}
          </div>
        </div>
      </TabsContent>
    </Tabs>

    <template #footer>
      <div class="flex justify-end gap-2">
        <button class="h-9 px-3 rounded border border-border text-sm"
                @click="emit('update:open', false)">{{ t('common.button.cancel') }}</button>
        <button class="h-9 px-3 rounded bg-primary text-primary-foreground text-sm disabled:opacity-50 disabled:cursor-not-allowed"
                :disabled="saving || isLocked"
                :title="isLocked ? t('role.edit.tooltip.locked') : ''"
                @click="save">
          {{ saving ? t('role.edit.message.saving') : t('common.button.save') }}
        </button>
      </div>
    </template>
  </Drawer>
</template>
