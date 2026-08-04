<script setup>
import { onMounted, onActivated, onDeactivated, reactive, ref, computed, watch } from 'vue'
import { useI18n } from 'vue-i18n'
import { useNotificationStore } from '@/stores/notification'
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
import { useDict } from '@/composables/useDict'
import { Plus, Search, RotateCcw, Pencil, Trash2 } from 'lucide-vue-next'
import {
  getDemoTaskListApi, getDemoTaskApi, addDemoTaskApi, updateDemoTaskApi, deleteDemoTaskApi
} from '@/services/demoTask'
import { getDeptTreeApi } from '@/services/dept'
import { getUserListApi } from '@/services/user'

const { t } = useI18n()
const { confirm } = useConfirm()
const notificationStore = useNotificationStore()

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

// 状态/优先级下拉与标签全部走字典（来源：后端内置枚举 task_status / task_priority）
const taskStatus = useDict('task_status')
const taskPriority = useDict('task_priority')

// 搜索框需要额外的「全て」选项；表单/徽章直接用字典
const statusSearchOptions = computed(() => [
  { label: t('task.option.statusAll'), value: '' },
  ...taskStatus.options.value
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

/**
 * 通知からの遷移:詳細は専用ルートではなくドロワー表示。taskId は URL ではなく
 * notification store(pendingNav)経由で受け取り、該当タスクを取得して開く。
 */
async function openById(id) {
  try {
    const res = await getDemoTaskApi(id)
    if (res.data.code === 0 && res.data.data) openEdit(res.data.data)
    else toast.error(res.data.msg || t('task.message.loadFailed'))
  } catch (e) { toast.error(e.message) }
}

onMounted(() => {
  fetchData()
  loadDepts()
  loadUsers()
})

// store(pendingNav)から対象 id を取り出してドロワーを開く。take は consume(一度きり)。
function consumeDrawerNav() {
  const nav = notificationStore.takePendingNav('/demo/task')
  if (nav && nav.bizType === 'demo_task' && nav.id) openById(nav.id)
}

// URL は常に /demo/task で不変 → keep-alive のインスタンスは 1 つだけ。よって以下の
// watch も 1 インスタンスにしか存在せず、過去の“多重発火”は起きない。
//   - onActivated:別ページから遷移してきた / 初回マウント時。
//   - watch     :既に本ページがアクティブな状態で別の通知を再クリック(route 変化なし)した時。
// drawerNavActive で「アクティブな時だけ開く」よう絞り、非表示状態での誤オープンを防ぐ。
let drawerNavActive = false
onActivated(() => { drawerNavActive = true; consumeDrawerNav() })
onDeactivated(() => { drawerNavActive = false })
watch(() => notificationStore.pendingNav, () => { if (drawerNavActive) consumeDrawerNav() })
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
        <button v-permission="'task:create'"
                class="h-9 px-3 rounded bg-primary text-primary-foreground text-sm inline-flex items-center gap-1"
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
          <Select v-model="search.status" :options="statusSearchOptions" />
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
          <Badge :variant="taskStatus.cssClass(row.status) || 'outline'">{{ taskStatus.label(row.status) }}</Badge>
        </template>
        <template #cell-priority="{ row }">
          <Badge :variant="taskPriority.cssClass(row.priority) || 'outline'">{{ taskPriority.label(row.priority) }}</Badge>
        </template>
        <template #cell-assigneeUserId="{ row }">{{ userLabel(row.assigneeUserId) }}</template>
        <template #cell-createUser="{ row }">{{ userLabel(row.createUser) }}</template>
        <template #cell-dueDate="{ row }">{{ row.dueDate || '-' }}</template>
        <template #cell-actions="{ row }">
          <div class="inline-flex gap-1">
            <button v-permission="'task:update'"
                    class="h-7 px-2 rounded hover:bg-muted text-xs" @click="openEdit(row)" :title="t('common.button.edit')">
              <Pencil class="size-3.5" />
            </button>
            <button v-permission="'task:delete'"
                    class="h-7 px-2 rounded hover:bg-destructive/10 text-destructive text-xs" @click="handleDelete(row)" :title="t('common.button.delete')">
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
            <Select v-model="editForm.status" :options="taskStatus.options.value" />
          </div>
          <div>
            <label class="text-xs text-muted-foreground block mb-1">{{ t('task.column.priority') }}</label>
            <Select v-model="editForm.priority" :options="taskPriority.options.value" />
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
