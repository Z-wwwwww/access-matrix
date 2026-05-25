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
import { deptIconFor as deptIcon } from '@/utils/dept-icons'
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

const deptTree = ref([])
const expandedDeptIds = ref(new Set())
const selectedDeptIds = ref([])

const saving = ref(false)
// 基本情報（name / description / dataScope / status）は tab の外で常に編集可。
// tab には「割当て系」だけを残す：perms / menus / (dataScope=CUSTOM のとき) depts。
const tabItems = computed(() => [
  { value: 'perms',  label: t('role.edit.tab.permissions') },
  { value: 'menus',  label: t('role.edit.tab.menus') },
  ...(form.dataScope === 5 ? [{ value: 'depts', label: t('role.edit.tab.depts') }] : [])
])

watch(() => props.open, async (open) => {
  if (!open) return
  tab.value = 'perms'
  Object.assign(form, {
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
    deptTree.value = dRes.value.data.data || []
    // ルート部署をデフォルトで展開（メニュー tab と同じ初期挙動）
    expandedDeptIds.value = new Set(deptTree.value.map((d) => d.id))
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

// ---- Depts tree: 展開状態を考慮した flat 列（メニュー tab と同じ展開ロジック） ----
// 部署の選択はカスケードしない：data scope = CUSTOM の場合、backend が path-based に
// 部分木展開する（DataScopeQueryService）ので、UI で子孫を自動勾選すると重複・冗長。
// 代わりに「祖先が勾選されている」子孫は disabled + 半透明で「自動包含」を可視化する。
const flatDeptTree = computed(() => {
  const out = []
  function walk(nodes, level) {
    for (const n of nodes) {
      out.push({ ...n, level })
      if (expandedDeptIds.value.has(n.id) && n.children?.length) {
        walk(n.children, level + 1)
      }
    }
  }
  walk(deptTree.value, 0)
  return out
})

/** id → 全子孫 id（自分を含まない）。祖先勾選による「暗黙包含」判定に使う。 */
const deptDescendantsMap = computed(() => {
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
  visit(deptTree.value)
  return map
})

const selectedDeptSet = computed(() => new Set(selectedDeptIds.value.map(String)))

/** 祖先勾選で暗黙的に包含されている dept id 集合（backend の subtree expansion を UI で可視化）。 */
const impliedDeptIds = computed(() => {
  const implied = new Set()
  for (const id of selectedDeptIds.value) {
    const descendants = deptDescendantsMap.value.get(id) || []
    for (const d of descendants) implied.add(String(d))
  }
  return implied
})

function isDeptChecked(id) {
  return selectedDeptSet.value.has(String(id))
}

function isDeptImplied(id) {
  return impliedDeptIds.value.has(String(id))
}

function toggleDept(id) {
  if (isLocked.value) return
  if (isDeptImplied(id)) return // 祖先勾選由来の自動包含 — 解除には祖先側を外す必要がある
  const sel = new Set(selectedDeptIds.value)
  if (sel.has(id)) sel.delete(id)
  else sel.add(id)
  selectedDeptIds.value = [...sel]
}

function toggleDeptExpand(id) {
  if (expandedDeptIds.value.has(id)) expandedDeptIds.value.delete(id)
  else expandedDeptIds.value.add(id)
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

    <!-- 基本情報：常時編集可（tab 外）。分割線なし、下の tab 領域の背景色で視覚分離。 -->
    <div class="space-y-4 mb-4">
      <div>
        <label class="text-xs text-muted-foreground block mb-1">{{ t('role.edit.label.name') }} <span class="text-destructive">*</span></label>
        <Input v-model="form.name" :disabled="isLocked" />
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

    <!-- 割当て tab 領域：outlined card + 内容区 muted bg で基本情報と視覚分離 -->
    <div class="rounded-xl border border-border overflow-hidden">
      <Tabs v-model="tab" :items="tabItems">
      <TabsContent value="perms" force-mount>
        <div class="p-4 bg-muted/20" :class="isLocked && 'opacity-60 pointer-events-none'">
          <!-- 連体カード：rounded + overflow-hidden だけ、bg は parent の muted/20 を継承。
               左 pane だけ muted/50 をかぶせて暗くし、右側＋選択中 tab は muted/20 で同色融合。 -->
          <div v-if="Object.keys(permsByModule).length"
               class="flex max-h-96 min-h-[20rem] rounded-lg overflow-hidden">
            <!-- 左ペイン: 暗めの「ツールパレット」
                 pr-0 にして active button が自然に右端へ届くようにする
                 （calc width だと overflow-y-auto が overflow-x も auto 化されて横スクロールが出る） -->
            <div class="w-48 shrink-0 overflow-y-auto pl-1 py-1 pr-0 bg-muted/50 space-y-0.5">
              <button
                v-for="(perms, m) in permsByModule"
                :key="m"
                type="button"
                :class="[
                  'w-full text-left px-3 py-2 text-sm transition flex items-center justify-between gap-2',
                  m === activePermModule
                    ? 'bg-card text-foreground font-medium rounded-l-md'
                    : 'text-muted-foreground hover:bg-card/60 hover:text-foreground rounded-l-md'
                ]"
                @click="activePermModule = m"
              >
                <span class="truncate">{{ moduleLabel(m) }}</span>
                <span
                  :class="[
                    'text-[11px] tabular-nums shrink-0 px-1.5 py-0.5 rounded-full font-medium leading-none',
                    m === activePermModule
                      ? 'bg-primary text-primary-foreground'
                      : ((moduleCounts[m]?.selected ?? 0) > 0
                          ? 'bg-primary/15 text-primary'
                          : 'bg-card/70 text-muted-foreground')
                  ]"
                >
                  {{ moduleCounts[m]?.selected ?? 0 }}/{{ moduleCounts[m]?.total ?? 0 }}
                </span>
              </button>
            </div>
            <!-- 右ペイン: 主内容（白い content card、選中 tab と同色で融合） -->
            <div class="flex-1 overflow-y-auto p-4 bg-card">
              <div class="flex items-center justify-between mb-3">
                <h3 class="text-base font-semibold truncate min-w-0">{{ moduleLabel(activePermModule) }}</h3>
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
              <div class="space-y-0.5">
                <div
                  v-for="p in activeModulePerms"
                  :key="p.id"
                  :class="[
                    'flex items-center justify-between gap-3 px-3 py-2 rounded-md text-sm cursor-pointer transition',
                    isPermChecked(p.id) ? 'bg-primary/10' : 'hover:bg-muted/40'
                  ]"
                  @click="togglePerm(p.id)"
                >
                  <div class="flex items-center gap-2.5 min-w-0">
                    <Checkbox
                      :model-value="isPermChecked(p.id)"
                      :disabled="isLocked"
                      @change="togglePerm(p.id)"
                    />
                    <span class="truncate" :class="isPermChecked(p.id) && 'font-medium'">
                      {{ t(`permission.${p.code}`, p.code) }}
                    </span>
                  </div>
                  <span class="text-[11px] text-muted-foreground font-mono px-1.5 py-0.5 rounded bg-muted shrink-0">
                    {{ p.code }}
                  </span>
                </div>
                <div v-if="!activeModulePerms.length"
                     class="text-xs text-muted-foreground p-4 text-center">
                  {{ t('role.edit.message.noPermissions') }}
                </div>
              </div>
            </div>
          </div>
          <div v-else class="text-sm text-muted-foreground p-6 text-center">
            {{ t('role.edit.message.noPermissions') }}
          </div>
        </div>
      </TabsContent>

      <TabsContent value="menus" force-mount>
        <div class="p-4 max-h-96 overflow-y-auto bg-muted/20"
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
        <div class="p-4 max-h-96 overflow-y-auto bg-muted/20" :class="isLocked && 'opacity-60 pointer-events-none'">
          <div v-if="flatDeptTree.length" class="space-y-0.5">
            <div
              v-for="d in flatDeptTree"
              :key="d.id"
              :class="[
                'flex items-center gap-2 px-2 py-2 text-sm rounded-md transition',
                isDeptImplied(d.id)
                  ? 'bg-primary/5 opacity-60 cursor-not-allowed'
                  : isDeptChecked(d.id)
                    ? 'bg-primary/10 cursor-pointer'
                    : 'hover:bg-muted/40 cursor-pointer'
              ]"
              :style="{ paddingLeft: 8 + d.level * 20 + 'px' }"
              :title="isDeptImplied(d.id) ? t('role.edit.dept.impliedTooltip') : ''"
              @click="toggleDept(d.id)"
            >
              <button
                v-if="d.children?.length"
                type="button"
                class="size-4 inline-flex items-center justify-center text-muted-foreground hover:text-foreground shrink-0"
                @click.stop="toggleDeptExpand(d.id)"
              >
                <ChevronDown v-if="expandedDeptIds.has(d.id)" class="size-4" />
                <ChevronRight v-else class="size-4" />
              </button>
              <span v-else class="inline-block size-4 shrink-0"></span>
              <Checkbox
                :model-value="isDeptChecked(d.id) || isDeptImplied(d.id)"
                :disabled="isLocked || isDeptImplied(d.id)"
                @change="toggleDept(d.id)"
              />
              <component :is="deptIcon(d.level)" :size="14" class="text-muted-foreground shrink-0" />
              <span class="font-medium truncate min-w-0"
                    :class="isDeptChecked(d.id) ? 'text-foreground' : 'text-foreground/90'">
                {{ d.name }}
              </span>
              <span class="text-[11px] text-muted-foreground font-mono px-1.5 py-0.5 rounded bg-muted shrink-0">
                {{ d.code }}
              </span>
              <span v-if="isDeptImplied(d.id) && !isDeptChecked(d.id)"
                    class="ml-auto text-[10px] text-muted-foreground italic shrink-0">
                {{ t('role.edit.dept.impliedTag') }}
              </span>
            </div>
          </div>
          <div v-else class="text-sm text-muted-foreground p-6 text-center">
            {{ t('role.edit.message.noDepts') }}
          </div>
        </div>
      </TabsContent>
      </Tabs>
    </div>

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
