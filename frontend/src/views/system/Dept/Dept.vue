<script setup>
import { onMounted, reactive, ref, computed } from 'vue'
import { useI18n } from 'vue-i18n'
import Card from '@/components/ui/Card.vue'
import Input from '@/components/ui/Input.vue'
import Switch from '@/components/ui/Switch.vue'
import Badge from '@/components/ui/Badge.vue'
import Drawer from '@/components/ui/Drawer.vue'
import { DataTable } from '@/components/shared/DataTable'
import UserPicker from '@/components/shared/UserPicker.vue'
import DeptPicker from '@/components/shared/DeptPicker.vue'
import { toast } from '@/composables/useToast'
import { useConfirm } from '@/composables/useConfirm'
import { Plus, Pencil, Trash2, ChevronRight, ChevronDown, Info } from 'lucide-vue-next'
import {
  getDeptTreeApi, addDeptApi, updateDeptApi, deleteDeptApi
} from '../../../../services/dept'
import { getUserListApi } from '../../../../services/user'

const { t } = useI18n()

const loading = ref(false)
const tree = ref([])
const expanded = ref(new Set())

const showEdit = ref(false)
const isEdit = ref(false)
const editForm = reactive({
  id: null, parentId: null, code: '', name: '',
  sortOrder: 0, leaderUserId: '', status: 1
})

// 親部署選択時、自分自身と子孫を候補から除外する（循環防止）
const parentExcludeIds = computed(() => {
  if (!editForm.id) return []
  const ids = []
  function collect(nodes) {
    for (const n of nodes) {
      if (n.id === editForm.id) {
        ids.push(n.id)
        function walk(children) {
          for (const c of children || []) {
            ids.push(c.id)
            walk(c.children)
          }
        }
        walk(n.children)
        return true
      }
      if (n.children?.length && collect(n.children)) return true
    }
    return false
  }
  collect(tree.value)
  return ids
})

// User pool — kept solely for the table column's id→label resolution.
// The drawer dropdown is now <UserPicker/>, which fetches its own options;
// the extra ~5KB round trip is fine and keeps the picker self-contained.
const userList = ref([])
const userMap = computed(() => {
  const m = new Map()
  for (const u of userList.value) m.set(u.id, u)
  return m
})

async function loadUserPool() {
  try {
    const res = await getUserListApi({ page: 1, size: 500 })
    if (res.data.code === 0) {
      userList.value = res.data.data.records || []
    }
  } catch (e) {
    // Non-fatal: the dropdown stays empty and the user can still type-search
    // in other fields. We surface the failure but don't block the page.
    toast.error(t('dept.message.loadUsersFailed'))
  }
}

function leaderLabel(id) {
  if (!id) return '-'
  const u = userMap.value.get(id)
  if (!u) return `${id} ${t('dept.message.userDeleted')}`
  return u.displayName ? `${u.displayName} (${u.username})` : u.username
}

// flat representation for table render (respects expanded state)
const flatTree = computed(() => {
  const out = []
  function walk(nodes) {
    for (const n of nodes) {
      out.push(n)
      if (expanded.value.has(n.id) && n.children?.length) walk(n.children)
    }
  }
  walk(tree.value)
  return out
})

// DataTable column schema. Tree chevron + indent live in the `#cell-name`
// slot; DataTable's built-in `expandable` is row-detail expansion (a slot
// rendered as a panel below the row), not parent/child rows, so we keep
// the expanded-state and flattening logic local and hand DataTable a
// pre-flattened list.
const columns = computed(() => [
  { key: 'name',         title: t('dept.column.name') },
  { key: 'code',         title: t('dept.column.code') },
  { key: 'level',        title: t('dept.column.level'), align: 'center' },
  { key: 'leaderUserId', title: t('dept.column.leader') },
  { key: 'status',       title: t('dept.column.status'), align: 'center' },
  { key: 'actions',      title: t('dept.column.actions'), align: 'center' }
])

async function fetchData() {
  loading.value = true
  try {
    const res = await getDeptTreeApi()
    if (res.data.code === 0) {
      tree.value = res.data.data || []
      // Default expand all level-1 nodes (the roots)
      tree.value.forEach(n => expanded.value.add(n.id))
    }
  } finally { loading.value = false }
}

function toggle(id) {
  if (expanded.value.has(id)) expanded.value.delete(id)
  else expanded.value.add(id)
}

function openCreate(parent = null) {
  isEdit.value = false
  Object.assign(editForm, {
    id: null,
    parentId: parent?.id || null,
    code: '',
    name: '',
    sortOrder: 0,
    leaderUserId: '',
    status: 1
  })
  showEdit.value = true
}

function openEdit(row) {
  isEdit.value = true
  Object.assign(editForm, {
    id: row.id,
    parentId: row.parentId || null,
    code: row.code,
    name: row.name,
    sortOrder: row.sortOrder ?? 0,
    leaderUserId: row.leaderUserId || '',
    status: row.status ?? 1
  })
  showEdit.value = true
}

async function save() {
  try {
    if (isEdit.value) {
      const body = {
        parentId: editForm.parentId || null,
        name: editForm.name,
        sortOrder: editForm.sortOrder,
        leaderUserId: editForm.leaderUserId || null,
        status: editForm.status
      }
      const r = await updateDeptApi(editForm.id, body)
      if (r.data.code !== 0) { toast.error(r.data.msg || t('dept.edit.message.updateFailed')); return }
    } else {
      const body = {
        parentId: editForm.parentId || null,
        code: editForm.code,
        name: editForm.name,
        sortOrder: editForm.sortOrder,
        leaderUserId: editForm.leaderUserId || null,
        status: editForm.status
      }
      const r = await addDeptApi(body)
      if (r.data.code !== 0) { toast.error(r.data.msg || t('dept.edit.message.createFailed')); return }
    }
    toast.success(t('common.message.saveSuccessful'))
    showEdit.value = false
    fetchData()
  } catch (e) { toast.error(e.message) }
}

const { confirm } = useConfirm()

async function handleDelete(row) {
  const ok = await confirm({
    title: t('dept.confirm.deleteTitle'),
    message: t('dept.confirm.deleteMessage', { name: row.name }),
    variant: 'destructive'
  })
  if (!ok) return
  try {
    const r = await deleteDeptApi(row.id)
    if (r.data.code === 0) {
      toast.success(t('common.message.deleteSuccessful'))
      fetchData()
      return
    }
    // IN_USE (703) — backend が利用中を検出。data.children / data.users / data.roles で件数を i18n 整形。
    if (r.data.code === 703) {
      const children = r.data.data?.children ?? 0
      const users = r.data.data?.users ?? 0
      const roles = r.data.data?.roles ?? 0
      const forceOk = await confirm({
        title: t('common.confirm.forceTitle'),
        message: t('dept.confirm.inUseMessage', { children, users, roles }),
        variant: 'destructive',
        confirmText: t('common.button.forceDelete')
      })
      if (!forceOk) return
      const r2 = await deleteDeptApi(row.id, { force: true })
      if (r2.data.code === 0) { toast.success(t('common.message.deleteSuccessful')); fetchData() }
      else toast.error(r2.data.msg || t('dept.message.deleteFailed'))
      return
    }
    toast.error(r.data.msg || t('dept.message.deleteFailed'))
  } catch (e) { toast.error(e.message) }
}

onMounted(() => {
  fetchData()
  loadUserPool()
})
</script>

<template>
  <div class="space-y-3">
    <Card class="p-4 flex items-center justify-between">
      <h1 class="text-lg font-semibold">{{ t('dept.title') }}</h1>
      <button v-permission="'dept:create'"
              class="h-9 px-3 rounded bg-primary text-primary-foreground text-sm inline-flex items-center gap-1"
              @click="openCreate(null)">
        <Plus class="size-4" /> {{ t('dept.button.addRoot') }}
      </button>
    </Card>

    <Card>
      <DataTable
        :columns="columns"
        :data="flatTree"
        :loading="loading"
        :show-pagination="false"
        :empty-text="t('dept.message.noDepts')"
      >
        <template #cell-name="{ row }">
          <div class="flex items-center gap-1" :style="{ paddingLeft: (row.level - 1) * 18 + 'px' }">
            <button v-if="row.children?.length" class="size-4 inline-flex" @click="toggle(row.id)">
              <ChevronDown v-if="expanded.has(row.id)" class="size-4" />
              <ChevronRight v-else class="size-4" />
            </button>
            <span v-else class="inline-block size-4"></span>
            <span class="font-medium">{{ row.name }}</span>
          </div>
        </template>
        <template #cell-code="{ row }">
          <span class="font-mono text-xs">{{ row.code }}</span>
        </template>
        <template #cell-leaderUserId="{ row }">
          <span class="text-xs">{{ leaderLabel(row.leaderUserId) }}</span>
        </template>
        <template #cell-status="{ row }">
          <Badge :variant="row.status === 1 ? 'default' : 'outline'">
            {{ row.status === 1 ? t('common.status.active') : t('common.status.inactive') }}
          </Badge>
        </template>
        <template #cell-actions="{ row }">
          <div class="inline-flex gap-1">
            <button v-permission="'dept:create'"
                    class="h-7 px-2 rounded hover:bg-muted text-xs" @click="openCreate(row)" :title="t('dept.tooltip.addChild')">
              <Plus class="size-3.5" />
            </button>
            <button v-permission="'dept:update'"
                    class="h-7 px-2 rounded hover:bg-muted text-xs" @click="openEdit(row)" :title="t('dept.tooltip.edit')">
              <Pencil class="size-3.5" />
            </button>
            <button v-permission="'dept:delete'"
                    class="h-7 px-2 rounded hover:bg-destructive/10 text-destructive text-xs" @click="handleDelete(row)" :title="t('common.button.delete')">
              <Trash2 class="size-3.5" />
            </button>
          </div>
        </template>
      </DataTable>
    </Card>

    <Drawer v-model:open="showEdit" :title="isEdit ? t('dept.edit.titleEdit') : t('dept.edit.titleCreate')" width="max-w-md">
      <div class="space-y-3">
        <div>
          <label class="text-xs text-muted-foreground block mb-1">{{ t('dept.edit.label.parentId') }}</label>
          <DeptPicker v-model="editForm.parentId" :exclude="parentExcludeIds" :placeholder="t('dept.edit.placeholder.parentId')" />
          <p class="text-xs text-muted-foreground mt-1">{{ t('dept.edit.hint.rootParent') }}</p>
        </div>
        <div>
          <label class="text-xs text-muted-foreground block mb-1">{{ t('dept.edit.label.code') }} <span class="text-destructive">*</span></label>
          <Input v-model="editForm.code" :disabled="isEdit" :placeholder="t('dept.edit.placeholder.code')" />
        </div>
        <div>
          <label class="text-xs text-muted-foreground block mb-1">{{ t('dept.edit.label.name') }} <span class="text-destructive">*</span></label>
          <Input v-model="editForm.name" :placeholder="t('dept.edit.placeholder.name')" />
        </div>
        <div class="grid grid-cols-2 gap-3">
          <div>
            <label class="text-xs text-muted-foreground block mb-1">{{ t('dept.edit.label.sortOrder') }}</label>
            <Input v-model.number="editForm.sortOrder" type="number" />
          </div>
          <div>
            <label class="text-xs text-muted-foreground block mb-1">{{ t('dept.edit.label.status') }}</label>
            <div class="h-9 flex items-center gap-2">
              <Switch v-model="editForm.status" :checked-value="1" :unchecked-value="0" />
              <span class="text-sm">{{ editForm.status === 1 ? t('common.status.active') : t('common.status.inactive') }}</span>
            </div>
          </div>
        </div>
        <div>
          <label class="text-xs text-muted-foreground block mb-1">{{ t('dept.edit.label.leader') }}</label>
          <UserPicker v-model="editForm.leaderUserId" :placeholder="t('dept.edit.placeholder.leader')" />
          <p class="mt-1 flex items-start gap-1 text-xs text-muted-foreground">
            <Info class="size-3.5 shrink-0 mt-0.5" />
            <span>{{ t('dept.edit.hint.leaderInfo') }}</span>
          </p>
        </div>
      </div>
      <template #footer>
        <div class="flex justify-end gap-2">
          <button class="h-9 px-3 rounded border border-border text-sm" @click="showEdit = false">{{ t('common.button.cancel') }}</button>
          <button class="h-9 px-3 rounded bg-primary text-primary-foreground text-sm" @click="save">{{ t('common.button.save') }}</button>
        </div>
      </template>
    </Drawer>
  </div>
</template>
