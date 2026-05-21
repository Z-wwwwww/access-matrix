<script setup>
import { onMounted, reactive, ref, computed } from 'vue'
import Card from '@/components/ui/Card.vue'
import Input from '@/components/ui/Input.vue'
import Select from '@/components/ui/Select.vue'
import Badge from '@/components/ui/Badge.vue'
import Drawer from '@/components/ui/Drawer.vue'
import { DataTable } from '@/components/shared/DataTable'
import UserPicker from '@/components/shared/UserPicker.vue'
import { toast } from '@/composables/useToast'
import { useConfirm } from '@/composables/useConfirm'
import { Plus, Pencil, Trash2, ChevronRight, ChevronDown, Info } from 'lucide-vue-next'
import {
  getDeptTreeApi, addDeptApi, updateDeptApi, deleteDeptApi
} from '../../../../services/dept'
import { getUserListApi } from '../../../../services/user'

const loading = ref(false)
const tree = ref([])
const expanded = ref(new Set())

const showEdit = ref(false)
const isEdit = ref(false)
const editForm = reactive({
  id: null, parentId: null, code: '', name: '',
  sortOrder: 0, leaderUserId: '', status: 1
})

const statusOptions = [
  { label: '有効', value: 1 }, { label: '無効', value: 0 }
]

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
    toast.error('ユーザー一覧の取得に失敗しました')
  }
}

function leaderLabel(id) {
  if (!id) return '-'
  const u = userMap.value.get(id)
  if (!u) return `${id} (削除済)`
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
const columns = [
  { key: 'name',         title: '名称' },
  { key: 'code',         title: 'コード' },
  { key: 'level',        title: 'レベル', align: 'center' },
  { key: 'leaderUserId', title: 'リーダー' },
  { key: 'status',       title: '状態',   align: 'center' },
  { key: 'actions',      title: '操作',   align: 'center' }
]

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
      if (r.data.code !== 0) { toast.error(r.data.msg || '更新失敗'); return }
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
      if (r.data.code !== 0) { toast.error(r.data.msg || '作成失敗'); return }
    }
    toast.success('保存しました')
    showEdit.value = false
    fetchData()
  } catch (e) { toast.error(e.message) }
}

const { confirm } = useConfirm()

async function handleDelete(row) {
  const ok = await confirm({
    title: '部署削除',
    message: `「${row.name}」を削除しますか？\n（子部署や所属ユーザーがある場合は拒否されます）`,
    variant: 'destructive'
  })
  if (!ok) return
  try {
    const r = await deleteDeptApi(row.id)
    if (r.data.code === 0) { toast.success('削除しました'); fetchData() }
    else toast.error(r.data.msg || '削除失敗')
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
      <h1 class="text-lg font-semibold">部署管理</h1>
      <button class="h-9 px-3 rounded bg-primary text-primary-foreground text-sm inline-flex items-center gap-1"
              @click="openCreate(null)">
        <Plus class="size-4" /> ルート追加
      </button>
    </Card>

    <Card>
      <DataTable
        :columns="columns"
        :data="flatTree"
        :loading="loading"
        :show-pagination="false"
        empty-text="部署がありません"
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
            {{ row.status === 1 ? '有効' : '無効' }}
          </Badge>
        </template>
        <template #cell-actions="{ row }">
          <div class="inline-flex gap-1">
            <button class="h-7 px-2 rounded hover:bg-muted text-xs" @click="openCreate(row)" title="子追加">
              <Plus class="size-3.5" />
            </button>
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

    <Drawer v-model:open="showEdit" :title="isEdit ? '部署編集' : '部署新規'" width="max-w-md">
      <div class="space-y-3">
        <div>
          <label class="text-xs text-muted-foreground block mb-1">親部署 ID</label>
          <Input v-model="editForm.parentId" placeholder="ルートの場合は空" />
          <p class="text-xs text-muted-foreground mt-1">空にするとルート部署になります</p>
        </div>
        <div>
          <label class="text-xs text-muted-foreground block mb-1">コード <span class="text-destructive">*</span></label>
          <Input v-model="editForm.code" :disabled="isEdit" placeholder="HQ / TOKYO" />
        </div>
        <div>
          <label class="text-xs text-muted-foreground block mb-1">名称 <span class="text-destructive">*</span></label>
          <Input v-model="editForm.name" placeholder="本社" />
        </div>
        <div class="grid grid-cols-2 gap-3">
          <div>
            <label class="text-xs text-muted-foreground block mb-1">並び順</label>
            <Input v-model.number="editForm.sortOrder" type="number" />
          </div>
          <div>
            <label class="text-xs text-muted-foreground block mb-1">状態</label>
            <Select v-model="editForm.status" :options="statusOptions" />
          </div>
        </div>
        <div>
          <label class="text-xs text-muted-foreground block mb-1">リーダー</label>
          <UserPicker v-model="editForm.leaderUserId" placeholder="未指定" />
          <p class="mt-1 flex items-start gap-1 text-xs text-muted-foreground">
            <Info class="size-3.5 shrink-0 mt-0.5" />
            <span>表示用のメモです。権限・データ範囲には影響しません。</span>
          </p>
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
