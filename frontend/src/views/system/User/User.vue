<script setup>
import { onMounted, reactive, ref } from 'vue'
import Card from '@/components/ui/Card.vue'
import Input from '@/components/ui/Input.vue'
import Badge from '@/components/ui/Badge.vue'
import { DataTable } from '@/components/shared/DataTable'
import { toast } from '@/composables/useToast'
import { useConfirm } from '@/composables/useConfirm'
import { Plus, Search, RotateCcw, Pencil, Trash2, Power, Key, LogOut } from 'lucide-vue-next'

const { confirm } = useConfirm()
import {
  getUserListApi, deleteUserApi, changeUserStatusApi, forceLogoutApi
} from '../../../../services/user'
import UserEdit from './UserEdit.vue'
import ResetPasswordDialog from './ResetPasswordDialog.vue'

const loading = ref(false)
const list = ref([])
const total = ref(0)
const page = ref(1)
const pageSize = ref(20)
const search = reactive({ keyword: '', deptId: '' })

const showEdit = ref(false)
const showResetPwd = ref(false)
const current = ref(null)

const columns = [
  { key: 'username', title: 'ユーザー名', minWidth: '140px' },
  { key: 'displayName', title: '表示名', minWidth: '120px' },
  { key: 'userNo', title: '番号', minWidth: '100px' },
  { key: 'email', title: 'メール', minWidth: '160px' },
  { key: 'deptId', title: '部署 ID', minWidth: '140px' },
  { key: 'status', title: '状態', minWidth: '80px', align: 'center' },
  { key: 'actions', title: '操作', minWidth: '220px', align: 'center', sticky: 'right' }
]

async function fetchData() {
  loading.value = true
  try {
    const res = await getUserListApi({ page: page.value, size: pageSize.value, ...search })
    if (res.data.code === 0) {
      list.value = res.data.data.records || []
      total.value = res.data.data.total || 0
    }
  } finally {
    loading.value = false
  }
}

function resetSearch() {
  search.keyword = ''
  search.deptId = ''
  page.value = 1
  fetchData()
}

function openCreate() {
  current.value = null
  showEdit.value = true
}

function openEdit(row) {
  current.value = row
  showEdit.value = true
}

function openResetPwd(row) {
  current.value = row
  showResetPwd.value = true
}

async function handleDelete(row) {
  const ok = await confirm({
    title: 'ユーザー削除',
    message: `「${row.username}」を削除しますか？`,
    variant: 'destructive'
  })
  if (!ok) return
  try {
    const res = await deleteUserApi(row.id)
    if (res.data.code === 0) { toast.success('削除しました'); fetchData() }
    else toast.error(res.data.msg || '削除失敗')
  } catch (e) { toast.error(e.message) }
}

async function toggleStatus(row) {
  const next = row.status === 1 ? 0 : 1
  try {
    const res = await changeUserStatusApi(row.id, next)
    if (res.data.code === 0) {
      toast.success(next === 1 ? '有効化しました' : '無効化しました')
      fetchData()
    }
  } catch (e) { toast.error(e.message) }
}

async function handleForceLogout(row) {
  const ok = await confirm({
    title: '強制ログアウト',
    message: `「${row.username}」を強制ログアウトしますか？\n（進行中の access token は次回 API 呼び出し時点で無効化されます）`,
    variant: 'destructive'
  })
  if (!ok) return
  try {
    const res = await forceLogoutApi(row.id)
    if (res.data.code === 0) toast.success('強制ログアウトしました')
    else toast.error(res.data.msg || '失敗')
  } catch (e) { toast.error(e.message) }
}

onMounted(fetchData)
</script>

<template>
  <div class="space-y-3">
    <Card class="p-4">
      <div class="flex flex-wrap items-end gap-3">
        <div>
          <label class="text-xs text-muted-foreground block mb-1">キーワード</label>
          <Input v-model="search.keyword" placeholder="ユーザー名 / メール / 表示名" class="w-60" />
        </div>
        <div>
          <label class="text-xs text-muted-foreground block mb-1">部署 ID</label>
          <Input v-model="search.deptId" placeholder="dept id" class="w-60" />
        </div>
        <button class="h-9 px-3 rounded bg-primary text-primary-foreground text-sm inline-flex items-center gap-1"
                @click="() => { page = 1; fetchData() }">
          <Search class="size-4" /> 検索
        </button>
        <button class="h-9 px-3 rounded border border-border text-sm inline-flex items-center gap-1"
                @click="resetSearch">
          <RotateCcw class="size-4" /> リセット
        </button>
        <div class="ml-auto">
          <button class="h-9 px-3 rounded bg-primary text-primary-foreground text-sm inline-flex items-center gap-1"
                  @click="openCreate">
            <Plus class="size-4" /> 新規
          </button>
        </div>
      </div>
    </Card>

    <Card>
      <DataTable
        :columns="columns"
        :data="list"
        :loading="loading"
        v-model:page="page"
        v-model:page-size="pageSize"
        :total="total"
        @update:page="fetchData"
        @update:page-size="fetchData"
      >
        <template #cell-username="{ row }">
          <span>{{ row.username }}</span>
          <Badge v-if="row.username === 'admin'" variant="outline" class="ml-2 text-[10px]">内蔵</Badge>
        </template>
        <template #cell-status="{ row }">
          <Badge :variant="row.status === 1 ? 'default' : 'outline'">
            {{ row.status === 1 ? '有効' : '無効' }}
          </Badge>
        </template>
        <template #cell-actions="{ row }">
          <div class="inline-flex items-center gap-1">
            <button class="h-7 px-2 rounded hover:bg-muted text-xs inline-flex items-center gap-1 disabled:opacity-40 disabled:cursor-not-allowed"
                    :disabled="row.username === 'admin'"
                    :title="row.username === 'admin' ? '内蔵ユーザーは編集不可' : '編集'"
                    @click="openEdit(row)">
              <Pencil class="size-3.5" />
            </button>
            <button class="h-7 px-2 rounded hover:bg-muted text-xs inline-flex items-center gap-1"
                    title="パスワードリセット"
                    @click="openResetPwd(row)">
              <Key class="size-3.5" />
            </button>
            <button class="h-7 px-2 rounded hover:bg-muted text-xs inline-flex items-center gap-1 disabled:opacity-40 disabled:cursor-not-allowed"
                    :disabled="row.username === 'admin'"
                    :title="row.username === 'admin' ? '内蔵ユーザーは状態変更不可' : '有効/無効'"
                    @click="toggleStatus(row)">
              <Power class="size-3.5" />
            </button>
            <button class="h-7 px-2 rounded hover:bg-muted text-xs inline-flex items-center gap-1"
                    title="強制ログアウト"
                    @click="handleForceLogout(row)">
              <LogOut class="size-3.5" />
            </button>
            <button class="h-7 px-2 rounded hover:bg-destructive/10 text-destructive text-xs inline-flex items-center gap-1 disabled:opacity-40 disabled:cursor-not-allowed"
                    :disabled="row.username === 'admin'"
                    :title="row.username === 'admin' ? '内蔵ユーザーは削除不可' : '削除'"
                    @click="handleDelete(row)">
              <Trash2 class="size-3.5" />
            </button>
          </div>
        </template>
      </DataTable>
    </Card>

    <UserEdit v-model:open="showEdit" :user="current" @saved="fetchData" />
    <ResetPasswordDialog v-model:open="showResetPwd" :user="current" />
  </div>
</template>
