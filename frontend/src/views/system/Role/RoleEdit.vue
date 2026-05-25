<script setup>
import { ref, reactive, watch, computed } from 'vue'
import { useI18n } from 'vue-i18n'
import Drawer from '@/components/ui/Drawer.vue'
import Input from '@/components/ui/Input.vue'
import Select from '@/components/ui/Select.vue'
import Switch from '@/components/ui/Switch.vue'
import Checkbox from '@/components/ui/Checkbox.vue'
import Tabs from '@/components/ui/Tabs.vue'
import { TabsContent } from 'radix-vue'
import { ChevronRight, ChevronDown } from 'lucide-vue-next'
import LucideIcon from '@/components/shared/LucideIcon.vue'
import { toast } from '@/composables/useToast'
import { useMenuTitle } from '@/composables/useMenuTitle'
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
const { translate: translateMenu } = useMenuTitle()

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

const permsByModule = ref({})
const selectedPermIds = ref([])
const activePermModule = ref(null)

const flatMenus = ref([])
const selectedMenuIds = ref([])
const expandedMenuIds = ref(new Set())

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
  // Promise.allSettled: 3 つの呼び出しの一つが 403/失敗してももう 2 つは生かす。
  // 以前は Promise.all + try/catch だったため、たとえば dept tree が落ちただけで
  // permission/menu 一覧まで空に見えていた。
  const [pRes, mRes, dRes] = await Promise.allSettled([
    getPermissionsByModuleApi(),
    getMenuIndexApi(),
    getDeptTreeApi()
  ])
  if (pRes.status === 'fulfilled' && pRes.value.data.code === 0) {
    permsByModule.value = pRes.value.data.data || {}
    // 左ペインのデフォルト選択を最初の module に
    const modules = Object.keys(permsByModule.value)
    activePermModule.value = modules[0] || null
  } else {
    console.warn('[RoleEdit] 権限一覧の取得に失敗:', pRes.reason || pRes.value?.data?.msg)
  }
  if (mRes.status === 'fulfilled' && mRes.value.data.code === 0) {
    flatMenus.value = mRes.value.data.data || []
    // ルートメニューを既定で展開（Menu.vue の挙動と合わせる）
    expandedMenuIds.value = new Set(flatMenus.value.filter((m) => !m.parentId).map((m) => m.id))
  } else {
    console.warn('[RoleEdit] メニュー一覧の取得に失敗:', mRes.reason || mRes.value?.data?.msg)
  }
  if (dRes.status === 'fulfilled' && dRes.value.data.code === 0) {
    flatDepts.value = flatten(dRes.value.data.data || [])
  } else {
    console.warn('[RoleEdit] 部署ツリーの取得に失敗:', dRes.reason || dRes.value?.data?.msg)
  }

  if (props.role) {
    const [p, m, d] = await Promise.allSettled([
      getRolePermissionsApi(props.role.id),
      getRoleMenusApi(props.role.id),
      getRoleDeptsApi(props.role.id)
    ])
    if (p.status === 'fulfilled' && p.value.data.code === 0) selectedPermIds.value = p.value.data.data || []
    if (m.status === 'fulfilled' && m.value.data.code === 0) selectedMenuIds.value = m.value.data.data || []
    if (d.status === 'fulfilled' && d.value.data.code === 0) selectedDeptIds.value = d.value.data.data || []
  }
})

function flatten(tree, level = 0, out = []) {
  for (const n of tree) {
    out.push({ ...n, level })
    if (n.children?.length) flatten(n.children, level + 1, out)
  }
  return out
}

// ---- Permissions two-pane: module ごとのカウント + 全選択トグル ----
const selectedPermSet = computed(() => new Set(selectedPermIds.value.map(String)))

/** module → { selected, total } */
const moduleCounts = computed(() => {
  const out = {}
  for (const [m, perms] of Object.entries(permsByModule.value)) {
    let selected = 0
    for (const p of perms) if (selectedPermSet.value.has(String(p.id))) selected++
    out[m] = { selected, total: perms.length }
  }
  return out
})

const activeModulePerms = computed(() =>
  activePermModule.value ? permsByModule.value[activePermModule.value] || [] : []
)

const activeModuleAllChecked = computed(() => {
  const perms = activeModulePerms.value
  if (!perms.length) return false
  return perms.every((p) => selectedPermSet.value.has(String(p.id)))
})

const activeModuleIndeterminate = computed(() => {
  const perms = activeModulePerms.value
  if (!perms.length) return false
  const checked = perms.filter((p) => selectedPermSet.value.has(String(p.id))).length
  return checked > 0 && checked < perms.length
})

function isPermChecked(id) {
  return selectedPermSet.value.has(String(id))
}

function togglePerm(id) {
  if (isLocked.value) return
  const sel = new Set(selectedPermIds.value)
  if (sel.has(id)) sel.delete(id)
  else sel.add(id)
  selectedPermIds.value = [...sel]
}

function toggleAllInActiveModule() {
  if (isLocked.value) return
  const perms = activeModulePerms.value
  if (!perms.length) return
  const sel = new Set(selectedPermIds.value)
  const allOn = perms.every((p) => sel.has(p.id))
  if (allOn) {
    for (const p of perms) sel.delete(p.id)
  } else {
    for (const p of perms) sel.add(p.id)
  }
  selectedPermIds.value = [...sel]
}

// module → ルートメニュー（code 一致）。サイドバーと同じ翻訳パスを使う。
const moduleRootMenuMap = computed(() => {
  const map = new Map()
  for (const m of flatMenus.value) {
    if (!m.parentId && m.code) map.set(m.code, m)
  }
  return map
})

function moduleLabel(key) {
  if (!key) return ''
  const rootMenu = moduleRootMenuMap.value.get(key)
  if (rootMenu) return translateMenu(rootMenu)
  return key.charAt(0).toUpperCase() + key.slice(1)
}

// ---- Menu tree: 親子カスケード + indeterminate 表示（Menu.vue と同じ tree 形） ----
const menuTree = computed(() => buildMenuTree(flatMenus.value))

function buildMenuTree(flat) {
  const byParent = new Map()
  for (const m of flat) {
    const k = m.parentId || ''
    if (!byParent.has(k)) byParent.set(k, [])
    byParent.get(k).push(m)
  }
  function walk(parentId, level) {
    return (byParent.get(parentId || '') || [])
      .sort((a, b) => (a.sortOrder ?? 0) - (b.sortOrder ?? 0))
      .map((m) => ({ ...m, level, children: walk(m.id, level + 1) }))
  }
  return walk(null, 0)
}

/** expanded 状態を考慮したフラット列（描画用） */
const flatMenuTree = computed(() => {
  const out = []
  function walk(nodes) {
    for (const n of nodes) {
      out.push(n)
      if (expandedMenuIds.value.has(n.id) && n.children?.length) walk(n.children)
    }
  }
  walk(menuTree.value)
  return out
})

/** id → 全子孫 id（自分を含まない） */
const menuDescendantsMap = computed(() => {
  const map = new Map()
  function visit(nodes) {
    for (const n of nodes) {
      const ids = []
      ;(function collect(children) {
        for (const c of children) {
          ids.push(c.id)
          if (c.children?.length) collect(c.children)
        }
      })(n.children || [])
      map.set(n.id, ids)
      if (n.children?.length) visit(n.children)
    }
  }
  visit(menuTree.value)
  return map
})

const selectedMenuSet = computed(() => new Set(selectedMenuIds.value.map(String)))

function isMenuChecked(id) {
  return selectedMenuSet.value.has(String(id))
}

function menuIndeterminate(id) {
  const descendants = menuDescendantsMap.value.get(id) || []
  if (!descendants.length) return false
  const selfChecked = selectedMenuSet.value.has(String(id))
  const anyDesc = descendants.some((d) => selectedMenuSet.value.has(String(d)))
  const allDesc = descendants.every((d) => selectedMenuSet.value.has(String(d)))
  if (!selfChecked && !anyDesc) return false
  if (selfChecked && allDesc) return false
  return true
}

function toggleMenuNode(id) {
  if (isLocked.value) return
  const descendants = menuDescendantsMap.value.get(id) || []
  const allIds = [id, ...descendants]
  const sel = new Set(selectedMenuIds.value)
  const allOn = allIds.every((i) => sel.has(i))
  if (allOn) {
    for (const i of allIds) sel.delete(i)
  } else {
    for (const i of allIds) sel.add(i)
  }
  selectedMenuIds.value = [...sel]
}

function toggleMenuExpand(id) {
  if (expandedMenuIds.value.has(id)) expandedMenuIds.value.delete(id)
  else expandedMenuIds.value.add(id)
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
              <div class="h-9 flex items-center gap-2">
                <Switch v-model="form.status" :checked-value="1" :unchecked-value="0" :disabled="isLocked" />
                <span class="text-sm">{{ form.status === 1 ? t('common.status.active') : t('common.status.inactive') }}</span>
              </div>
            </div>
          </div>
        </div>
      </TabsContent>

      <TabsContent value="perms" force-mount>
        <div class="pt-2" :class="isLocked && 'opacity-60 pointer-events-none'">
          <div v-if="Object.keys(permsByModule).length" class="flex max-h-96 min-h-[20rem] gap-3">
            <!-- 左ペイン: module 一覧 -->
            <div class="w-44 shrink-0 overflow-y-auto py-1">
              <button
                v-for="(perms, m) in permsByModule"
                :key="m"
                type="button"
                :class="[
                  'w-full text-left px-3 py-2 text-sm border-l-2 transition flex items-center justify-between gap-2',
                  m === activePermModule
                    ? 'border-primary bg-primary/10 text-primary font-medium'
                    : 'border-transparent hover:bg-muted text-foreground'
                ]"
                @click="activePermModule = m"
              >
                <span class="truncate">{{ moduleLabel(m) }}</span>
                <span class="text-[10px] font-mono shrink-0"
                      :class="m === activePermModule ? 'text-primary/80' : 'text-muted-foreground'">
                  {{ moduleCounts[m]?.selected ?? 0 }}/{{ moduleCounts[m]?.total ?? 0 }}
                </span>
              </button>
            </div>
            <!-- 右ペイン: active module の権限一覧 -->
            <div class="flex-1 overflow-y-auto pl-3 border-l border-border">
              <div class="flex items-center justify-between pb-2 mb-2 border-b border-border">
                <div class="text-sm font-semibold">{{ moduleLabel(activePermModule) }}</div>
                <Checkbox
                  :model-value="activeModuleAllChecked"
                  :indeterminate="activeModuleIndeterminate"
                  :disabled="isLocked || !activeModulePerms.length"
                  @change="toggleAllInActiveModule"
                >
                  <span class="text-xs text-muted-foreground">
                    {{ t('common.button.selectAll') }}
                  </span>
                </Checkbox>
              </div>
              <div class="space-y-1">
                <div
                  v-for="p in activeModulePerms"
                  :key="p.id"
                  class="flex items-center gap-2 px-2 py-1 rounded text-sm cursor-pointer hover:bg-muted"
                  @click="togglePerm(p.id)"
                >
                  <Checkbox
                    :model-value="isPermChecked(p.id)"
                    :disabled="isLocked"
                    @change="togglePerm(p.id)"
                  />
                  <span>{{ t(`permission.${p.code}`, p.code) }}</span>
                  <span class="text-muted-foreground font-mono text-xs">({{ p.code }})</span>
                </div>
                <div v-if="!activeModulePerms.length" class="text-xs text-muted-foreground p-2">
                  {{ t('role.edit.message.noPermissions') }}
                </div>
              </div>
            </div>
          </div>
          <div v-else class="text-sm text-muted-foreground p-4">
            {{ t('role.edit.message.noPermissions') }}
          </div>
        </div>
      </TabsContent>

      <TabsContent value="menus" force-mount>
        <div class="pt-2 max-h-96 overflow-y-auto"
             :class="isLocked && 'opacity-60 pointer-events-none'">
          <div v-if="flatMenuTree.length" class="py-1">
            <div
              v-for="m in flatMenuTree"
              :key="m.id"
              class="flex items-center gap-1.5 px-2 py-1 text-sm cursor-pointer hover:bg-muted"
              :style="{ paddingLeft: 8 + m.level * 18 + 'px' }"
              @click="toggleMenuNode(m.id)"
            >
              <button
                v-if="m.children?.length"
                type="button"
                class="size-4 inline-flex items-center justify-center text-muted-foreground hover:text-foreground"
                @click.stop="toggleMenuExpand(m.id)"
              >
                <ChevronDown v-if="expandedMenuIds.has(m.id)" class="size-4" />
                <ChevronRight v-else class="size-4" />
              </button>
              <span v-else class="inline-block size-4 shrink-0"></span>
              <Checkbox
                :model-value="isMenuChecked(m.id)"
                :indeterminate="menuIndeterminate(m.id)"
                :disabled="isLocked"
                @change="toggleMenuNode(m.id)"
              />
              <LucideIcon v-if="m.icon" :name="m.icon" :size="14" class="text-muted-foreground shrink-0" />
              <span class="font-medium truncate">{{ translateMenu(m) }}</span>
              <span class="text-muted-foreground font-mono text-xs truncate">{{ m.code }}</span>
            </div>
          </div>
          <div v-else class="text-sm text-muted-foreground p-4">
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
