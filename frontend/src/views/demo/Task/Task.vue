<script setup>
import { onMounted, reactive, ref, computed } from 'vue'
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

const STATUS_LABEL = { 1: '未着手', 2: '進行中', 3: '完了', 4: '取消' }
const STATUS_VARIANT = { 1: 'outline', 2: 'default', 3: 'default', 4: 'destructive' }
const PRIORITY_LABEL = { 1: '低', 2: '中', 3: '高' }
const PRIORITY_VARIANT = { 1: 'outline', 2: 'default', 3: 'destructive' }

const statusOptions = [
  { label: 'すべて', value: '' },
  { label: '未着手', value: 1 },
  { label: '進行中', value: 2 },
  { label: '完了', value: 3 },
  { label: '取消', value: 4 }
]
const statusFormOptions = statusOptions.filter(o => o.value !== '')
const priorityOptions = [
  { label: '低', value: 1 },
  { label: '中', value: 2 },
  { label: '高', value: 3 }
]

const showEdit = ref(false)
const isEdit = ref(false)
const editForm = reactive({
  id: null, deptId: '', title: '', content: '',
  status: 1, priority: 2, assigneeUserId: '', dueDate: ''
})

const columns = [
  { key: 'title',          title: 'タイトル' },
  { key: 'deptId',         title: '部署' },
  { key: 'status',         title: '状態',   align: 'center' },
  { key: 'priority',       title: '優先度', align: 'center' },
  { key: 'assigneeUserId', title: '担当者' },
  { key: 'createUser',     title: '作成者' },
  { key: 'dueDate',        title: '期日',   align: 'center' },
  { key: 'actions',        title: '操作',  align: 'center' }
]

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
      toast.error(res.data.msg || '読み込み失敗')
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
    if (r.data.code !== 0) { toast.error(r.data.msg || '保存失敗'); return }
    toast.success('保存しました')
    showEdit.value = false
    fetchData()
  } catch (e) { toast.error(e.message) }
}

async function handleDelete(row) {
  const ok = await confirm({
    title: 'タスク削除',
    message: `「${row.title}」を削除しますか？`,
    variant: 'destructive'
  })
  if (!ok) return
  try {
    const r = await deleteDemoTaskApi(row.id)
    if (r.data.code === 0) { toast.success('削除しました'); fetchData() }
    else toast.error(r.data.msg || '削除失敗')
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
          <h1 class="text-lg font-semibold">タスク（データ範囲デモ）</h1>
          <p class="text-xs text-muted-foreground mt-0.5">
            ロール毎に見えるタスクが変わります。詳細: <code class="text-foreground">docs/data-scope-demo.md</code>
          </p>
        </div>
        <button class="h-9 px-3 rounded bg-primary text-primary-foreground text-sm inline-flex items-center gap-1"
                @click="openCreate">
          <Plus class="size-4" /> 新規
        </button>
      </div>
      <div class="grid grid-cols-1 md:grid-cols-4 gap-3 items-end">
        <div>
          <label class="text-xs text-muted-foreground block mb-1">キーワード</label>
          <Input v-model="search.keyword" placeholder="タイトル検索" />
        </div>
        <div>
          <label class="text-xs text-muted-foreground block mb-1">状態</label>
          <Select v-model="search.status" :options="statusOptions" />
        </div>
        <div class="flex gap-2">
          <button class="h-9 px-3 rounded bg-primary text-primary-foreground text-sm inline-flex items-center gap-1"
                  @click="() => { page = 1; fetchData() }">
            <Search class="size-4" /> 検索
          </button>
          <button class="h-9 px-3 rounded border border-border text-sm inline-flex items-center gap-1"
                  @click="resetSearch">
            <RotateCcw class="size-4" /> リセット
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
        empty-text="該当データがありません"
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
            <button class="h-7 px-2 rounded hover:bg-muted text-xs" @click="openEdit(row)" title="編集">
              <Pencil class="size-3.5" />
            </button>
            <button class="h-7 px-2 rounded hover:bg-destructive/10 text-destructive text-xs" @click="handleDelete(row)" title="削除">
              <Trash2 class="size-3.5" />
            </button>
          </div>
        </template>
      </DataTable>
    </Card>

    <Drawer v-model:open="showEdit" :title="isEdit ? 'タスク編集' : 'タスク新規'" width="max-w-md">
      <div class="space-y-3">
        <div>
          <label class="text-xs text-muted-foreground block mb-1">タイトル <span class="text-destructive">*</span></label>
          <Input v-model="editForm.title" placeholder="タスクタイトル" />
        </div>
        <div>
          <label class="text-xs text-muted-foreground block mb-1">部署 <span class="text-destructive">*</span></label>
          <Select v-model="editForm.deptId" :options="deptOptions" placeholder="部署を選択" :searchable="true" />
        </div>
        <div>
          <label class="text-xs text-muted-foreground block mb-1">内容</label>
          <Input v-model="editForm.content" placeholder="任意" />
        </div>
        <div class="grid grid-cols-2 gap-3">
          <div>
            <label class="text-xs text-muted-foreground block mb-1">状態</label>
            <Select v-model="editForm.status" :options="statusFormOptions" />
          </div>
          <div>
            <label class="text-xs text-muted-foreground block mb-1">優先度</label>
            <Select v-model="editForm.priority" :options="priorityOptions" />
          </div>
        </div>
        <div>
          <label class="text-xs text-muted-foreground block mb-1">担当者</label>
          <UserPicker v-model="editForm.assigneeUserId" placeholder="未指定" />
        </div>
        <div>
          <label class="text-xs text-muted-foreground block mb-1">期日</label>
          <DatePicker v-model="editForm.dueDate" placeholder="期日を選択" />
        </div>
      </div>
      <template #footer>
        <div class="flex justify-end gap-2">
          <button class="h-9 px-3 rounded border border-border text-sm" @click="showEdit = false">キャンセル</button>
          <button class="h-9 px-3 rounded bg-primary text-primary-foreground text-sm" @click="save">保存</button>
        </div>
      </template>
    </Drawer>
  </div>
</template>
