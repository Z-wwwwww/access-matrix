<script setup>
import { onMounted, reactive, ref } from 'vue'
import Card from '@/components/ui/Card.vue'
import Input from '@/components/ui/Input.vue'
import Badge from '@/components/ui/Badge.vue'
import { DataTable } from '@/components/shared/DataTable'
import { toast } from '@/composables/useToast'
import { useConfirm } from '@/composables/useConfirm'
import { Plus, Search, RotateCcw, Pencil, Trash2 } from 'lucide-vue-next'
import { getRoleListApi, deleteRoleApi } from '../../../../services/role'
import RoleEdit from './RoleEdit.vue'

const { confirm } = useConfirm()

const SCOPE_LABEL = { 1: '全部', 2: '本部署+下位', 3: '本部署', 4: '本人のみ', 5: 'カスタム' }

const loading = ref(false)
const list = ref([])
const total = ref(0)
const page = ref(1)
const pageSize = ref(20)
const search = reactive({ keyword: '' })
const showEdit = ref(false)
const current = ref(null)

const columns = [
  { key: 'code', title: 'コード', minWidth: '200px' },
  { key: 'name', title: '名称', minWidth: '160px' },
  { key: 'dataScope', title: 'データスコープ', minWidth: '140px', align: 'center' },
  { key: 'status', title: '状態', minWidth: '80px', align: 'center' },
  { key: 'actions', title: '操作', minWidth: '120px', align: 'center', sticky: 'right' }
]

async function fetchData() {
  loading.value = true
  try {
    const res = await getRoleListApi({ page: page.value, size: pageSize.value, ...search })
    if (res.data.code === 0) {
      list.value = res.data.data.records || []
      total.value = res.data.data.total || 0
    }
  } finally { loading.value = false }
}

function resetSearch() { search.keyword = ''; page.value = 1; fetchData() }
function openCreate() { current.value = null; showEdit.value = true }
function openEdit(row) { current.value = row; showEdit.value = true }

async function handleDelete(row) {
  if (row.isBuiltIn === 1) { toast.error('内蔵ロールは削除できません'); return }
  const ok = await confirm({
    title: 'ロール削除',
    message: `「${row.code}」を削除しますか？`,
    variant: 'destructive'
  })
  if (!ok) return
  try {
    const res = await deleteRoleApi(row.id)
    if (res.data.code === 0) { toast.success('削除しました'); fetchData() }
    else toast.error(res.data.msg || '削除失敗')
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
          <Input v-model="search.keyword" placeholder="コード / 名称" class="w-60" />
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
        <template #cell-code="{ row }">
          <span class="font-mono">{{ row.code }}</span>
          <Badge v-if="row.isBuiltIn === 1" variant="outline" class="ml-2 text-[10px]">内蔵</Badge>
        </template>
        <template #cell-dataScope="{ row }">
          <Badge variant="outline">{{ SCOPE_LABEL[row.dataScope] || '-' }}</Badge>
        </template>
        <template #cell-status="{ row }">
          <Badge :variant="row.status === 1 ? 'default' : 'outline'">{{ row.status === 1 ? '有効' : '無効' }}</Badge>
        </template>
        <template #cell-actions="{ row }">
          <div class="inline-flex items-center gap-1">
            <button class="h-7 px-2 rounded hover:bg-muted text-xs inline-flex items-center gap-1"
                    :title="row.isBuiltIn === 1 ? '内蔵ロールは閲覧のみ（編集ボタンで詳細表示）' : '編集'"
                    @click="openEdit(row)">
              <Pencil class="size-3.5" />
            </button>
            <button class="h-7 px-2 rounded hover:bg-destructive/10 text-destructive text-xs inline-flex items-center gap-1 disabled:opacity-40 disabled:cursor-not-allowed"
                    :disabled="row.isBuiltIn === 1"
                    :title="row.isBuiltIn === 1 ? '内蔵ロールは削除不可' : '削除'"
                    @click="handleDelete(row)">
              <Trash2 class="size-3.5" />
            </button>
          </div>
        </template>
      </DataTable>
    </Card>

    <RoleEdit v-model:open="showEdit" :role="current" @saved="fetchData" />
  </div>
</template>
