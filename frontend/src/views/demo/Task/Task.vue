<script setup>
import { onMounted, reactive, ref, computed } from 'vue'
import { useI18n } from 'vue-i18n'
import Card from '@/components/ui/Card.vue'
import Input from '@/components/ui/Input.vue'
import Select from '@/components/ui/Select.vue'
import Badge from '@/components/ui/Badge.vue'
import Drawer from '@/components/ui/Drawer.vue'
import DatePicker from '@/components/ui/DatePicker.vue'
import { DataTable } from '@/components/shared/DataTable'
import UserPicker from '@/components/shared/UserPicker.vue'
import { toast } from '@/composables/useToast'
import { useConfirm } from '@/composables/useConfirm'
import { Plus, Search, RotateCcw, Pencil, Trash2 } from 'lucide-vue-next'
import {
  getDemoTaskListApi, addDemoTaskApi, updateDemoTaskApi, deleteDemoTaskApi
} from '../../../../services/demoTask'
import { getDeptTreeApi } from '../../../../services/dept'
import { getUserListApi } from '../../../../services/user'

const { t } = useI18n()
const { confirm } = useConfirm()

const loading = ref(false)
const list = ref([])
const total = ref(0)
const page = ref(1)
const pageSize = ref(20)

const search = reactive({ keyword: '', status: '' })

// dept tree → flat lookup for table column rendering
const deptMap = ref(new Map())
const deptOptions = ref([])

// user list → flat lookup for creator column rendering
const userMap = ref(new Map())

const STATUS_LABEL = computed(() => ({
  1: t('task.status.todo'),
  2: t('task.status.doing'),
  3: t('task.status.done'),
  4: t('task.status.cancelled')
}))
const STATUS_VARIANT = { 1: 'outline', 2: 'default', 3: 'default', 4: 'destructive' }
const PRIORITY_LABEL = computed(() => ({
  1: t('task.priority.low'),
  2: t('task.priority.medium'),
  3: t('task.priority.high')
}))
const PRIORITY_VARIANT = { 1: 'outline', 2: 'default', 3: 'destructive' }

const statusOptions = computed(() => [
  { label: t('task.option.statusAll'), value: '' },
  { label: t('task.status.todo'), value: 1 },
  { label: t('task.status.doing'), value: 2 },
  { label: t('task.status.done'), value: 3 },
  { label: t('task.status.cancelled'), value: 4 }
])
const statusFormOptions = computed(() => statusOptions.value.filter(o => o.value !== ''))
const priorityOptions = computed(() => [
  { label: t('task.priority.low'), value: 1 },
  { label: t('task.priority.medium'), value: 2 },
  { label: t('task.priority.high'), value: 3 }
])

const showEdit = ref(false)
const isEdit = ref(false)
const editForm = reactive({
  id: null, deptId: '', title: '', content: '',
  status: 1, priority: 2, assigneeUserId: '', dueDate: ''
})

const columns = computed(() => [
  { key: 'title',          title: t('task.column.title') },
  { key: 'deptId',         title: t('task.column.deptId') },
  { key: 'status',         title: t('task.column.status'),   align: 'center' },
  { key: 'priority',       title: t('task.column.priority'), align: 'center' },
  { key: 'assigneeUserId', title: t('task.column.assignee') },
  { key: 'createUser',     title: t('task.column.creator') },
  { key: 'dueDate',        title: t('task.column.dueDate'),  align: 'center' },
  { key: 'actions',        title: t('task.column.actions'),  align: 'center' }
])

async function fetchData() {
  loading.value = true
  try {
    const params = { page: page.value, size: pageSize.value }
    if (search.keyword) params.keyword = search.keyword
    if (search.status !== '') params.status = search.status
    const res = await getDemoTaskListApi(params)
    if (res.data.code === 0) {
      list.value = res.data.data?.records || []
      total.value = res.data.data?.total || 0
    } else {
      toast.error(res.data.msg || t('task.message.loadFailed'))
    }
  } catch (e) {
    toast.error(e.message)
  } finally {
    loading.value = false
  }
}

async function loadDepts() {
  try {
    const res = await getDeptTreeApi()
    if (res.data.code === 0) {
      const flat = []
      const walk = (nodes) => nodes.forEach(n => {
        flat.push(n)
        if (n.children?.length) walk(n.children)
      })
      walk(res.data.data || [])
      const m = new Map()
      for (const d of flat) m.set(d.id, d.name)
      deptMap.value = m
      deptOptions.value = flat.map(d => ({ value: d.id, label: '·'.repeat(d.level - 1) + d.name }))
    }
  } catch (e) { /* non-fatal — column falls back to raw id */ }
}

async function loadUsers() {
  try {
    const res = await getUserListApi({ page: 1, size: 500 })
    if (res.data.code === 0) {
      const m = new Map()
      for (const u of (res.data.data?.records || [])) m.set(u.id, u)
      userMap.value = m
    }
  } catch (e) { /* non-fatal */ }
}

function userLabel(id) {
  if (!id) return '-'
  const u = userMap.value.get(id)
  if (!u) return id
  return u.displayName ? `${u.displayName} (${u.username})` : u.username
}

function deptLabel(id) {
  return deptMap.value.get(id) || id || '-'
}

function resetSearch() {
  search.keyword = ''
  search.status = ''
  page.value = 1
  fetchData()
}

function openCreate() {
  isEdit.value = false
  Object.assign(editForm, {
    id: null, deptId: '', title: '', content: '',
    status: 1, priority: 2, assigneeUserId: '', dueDate: ''
  })
  showEdit.value = true
}

function openEdit(row) {
  isEdit.value = true
  Object.assign(editForm, {
    id: row.id,
    deptId: row.deptId || '',
    title: row.title || '',
    content: row.content || '',
    status: row.status ?? 1,
    priority: row.priority ?? 2,
    assigneeUserId: row.assigneeUserId || '',
    dueDate: row.dueDate || ''
  })
  showEdit.value = true
}

async function save() {
  try {
    const body = {
      deptId: editForm.deptId,
      title: editForm.title,
      content: editForm.content,
      status: editForm.status,
      priority: editForm.priority,
      assigneeUserId: editForm.assigneeUserId || null,
      dueDate: editForm.dueDate || null
    }
    const r = isEdit.value
      ? await updateDemoTaskApi(editForm.id, body)
      : await addDemoTaskApi(body)
    if (r.data.code !== 0) { toast.error(r.data.msg || t('task.message.saveFailed')); return }
    toast.success(t('task.message.saveSuccess'))
    showEdit.value = false
    fetchData()
  } catch (e) { toast.error(e.message) }
}

async function handleDelete(row) {
  const ok = await confirm({
    title: t('task.confirm.deleteTitle'),
    message: t('task.confirm.deleteMessage', { title: row.title }),
    variant: 'destructive'
  })
  if (!ok) return
  try {
    const r = await deleteDemoTaskApi(row.id)
    if (r.data.code === 0) { toast.success(t('task.message.deleteSuccess')); fetchData() }
    else toast.error(r.data.msg || t('task.message.deleteFailed'))
  } catch (e) { toast.error(e.message) }
}

onMounted(() => {
  fetchData()
  loadDepts()
  loadUsers()
})
</script>

<template>
  <div class="space-y-3">
    <Card class="p-4">
      <div class="flex items-center justify-between mb-3">
        <div>
          <h1 class="text-lg font-semibold">{{ t('task.title') }}</h1>
          <p class="text-xs text-muted-foreground mt-0.5">
            {{ t('task.description') }} <code class="text-foreground">docs/data-scope-demo.md</code>
          </p>
        </div>
        <button class="h-9 px-3 rounded bg-primary text-primary-foreground text-sm inline-flex items-center gap-1"
                @click="openCreate">
          <Plus class="size-4" /> {{ t('common.button.new') }}
        </button>
      </div>
      <div class="grid grid-cols-1 md:grid-cols-4 gap-3 items-end">
        <div>
          <label class="text-xs text-muted-foreground block mb-1">{{ t('task.search.label.keyword') }}</label>
          <Input v-model="search.keyword" :placeholder="t('task.search.placeholder.keyword')" />
        </div>
        <div>
          <label class="text-xs text-muted-foreground block mb-1">{{ t('task.search.label.status') }}</label>
          <Select v-model="search.status" :options="statusOptions" />
        </div>
        <div class="flex gap-2">
          <button class="h-9 px-3 rounded bg-primary text-primary-foreground text-sm inline-flex items-center gap-1"
                  @click="() => { page = 1; fetchData() }">
            <Search class="size-4" /> {{ t('common.button.search') }}
          </button>
          <button class="h-9 px-3 rounded border border-border text-sm inline-flex items-center gap-1"
                  @click="resetSearch">
            <RotateCcw class="size-4" /> {{ t('common.button.reset') }}
          </button>
        </div>
      </div>
    </Card>

    <Card>
      <DataTable
        :columns="columns"
        :data="list"
        :loading="loading"
        :total="total"
        v-model:page="page"
        v-model:page-size="pageSize"
        @update:page="fetchData"
        @update:page-size="fetchData"
        :empty-text="t('task.emptyState')"
      >
        <template #cell-title="{ row }">
          <span class="font-medium">{{ row.title }}</span>
        </template>
        <template #cell-deptId="{ row }">{{ deptLabel(row.deptId) }}</template>
        <template #cell-status="{ row }">
          <Badge :variant="STATUS_VARIANT[row.status] || 'outline'">{{ STATUS_LABEL[row.status] || row.status }}</Badge>
        </template>
        <template #cell-priority="{ row }">
          <Badge :variant="PRIORITY_VARIANT[row.priority] || 'outline'">{{ PRIORITY_LABEL[row.priority] || row.priority }}</Badge>
        </template>
        <template #cell-assigneeUserId="{ row }">{{ userLabel(row.assigneeUserId) }}</template>
        <template #cell-createUser="{ row }">{{ userLabel(row.createUser) }}</template>
        <template #cell-dueDate="{ row }">{{ row.dueDate || '-' }}</template>
        <template #cell-actions="{ row }">
          <div class="inline-flex gap-1">
            <button class="h-7 px-2 rounded hover:bg-muted text-xs" @click="openEdit(row)" :title="t('common.button.edit')">
              <Pencil class="size-3.5" />
            </button>
            <button class="h-7 px-2 rounded hover:bg-destructive/10 text-destructive text-xs" @click="handleDelete(row)" :title="t('common.button.delete')">
              <Trash2 class="size-3.5" />
            </button>
          </div>
        </template>
      </DataTable>
    </Card>

    <Drawer v-model:open="showEdit" :title="isEdit ? t('task.edit.titleEdit') : t('task.edit.titleCreate')" width="max-w-md">
      <div class="space-y-3">
        <div>
          <label class="text-xs text-muted-foreground block mb-1">{{ t('task.column.title') }} <span class="text-destructive">*</span></label>
          <Input v-model="editForm.title" :placeholder="t('task.edit.placeholder.title')" />
        </div>
        <div>
          <label class="text-xs text-muted-foreground block mb-1">{{ t('task.column.deptId') }} <span class="text-destructive">*</span></label>
          <Select v-model="editForm.deptId" :options="deptOptions" :placeholder="t('task.edit.placeholder.deptSelect')" :searchable="true" />
        </div>
        <div>
          <label class="text-xs text-muted-foreground block mb-1">{{ t('task.edit.label.content') }}</label>
          <Input v-model="editForm.content" :placeholder="t('task.edit.placeholder.optional')" />
        </div>
        <div class="grid grid-cols-2 gap-3">
          <div>
            <label class="text-xs text-muted-foreground block mb-1">{{ t('task.column.status') }}</label>
            <Select v-model="editForm.status" :options="statusFormOptions" />
          </div>
          <div>
            <label class="text-xs text-muted-foreground block mb-1">{{ t('task.column.priority') }}</label>
            <Select v-model="editForm.priority" :options="priorityOptions" />
          </div>
        </div>
        <div>
          <label class="text-xs text-muted-foreground block mb-1">{{ t('task.column.assignee') }}</label>
          <UserPicker v-model="editForm.assigneeUserId" :placeholder="t('task.edit.placeholder.unassigned')" />
        </div>
        <div>
          <label class="text-xs text-muted-foreground block mb-1">{{ t('task.column.dueDate') }}</label>
          <DatePicker v-model="editForm.dueDate" :placeholder="t('task.edit.placeholder.dueDate')" />
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
