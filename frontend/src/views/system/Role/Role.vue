<script setup>
import { computed, onMounted, reactive, ref } from 'vue'
import { useI18n } from 'vue-i18n'
import Card from '@/components/ui/Card.vue'
import Input from '@/components/ui/Input.vue'
import Badge from '@/components/ui/Badge.vue'
import { DataTable } from '@/components/shared/DataTable'
import { toast } from '@/composables/useToast'
import { useConfirm } from '@/composables/useConfirm'
import { Plus, Search, RotateCcw, Pencil, Trash2 } from 'lucide-vue-next'
import { getRoleListApi, deleteRoleApi } from '../../../../services/role'
import RoleEdit from './RoleEdit.vue'

const { t } = useI18n()
const { confirm } = useConfirm()

const SCOPE_LABEL = computed(() => ({
  1: t('role.option.scope.all'),
  2: t('role.option.scope.deptAndSub'),
  3: t('role.option.scope.dept'),
  4: t('role.option.scope.self'),
  5: t('role.option.scope.custom')
}))

const loading = ref(false)
const list = ref([])
const total = ref(0)
const page = ref(1)
const pageSize = ref(20)
const search = reactive({ keyword: '' })
const showEdit = ref(false)
const current = ref(null)

const columns = computed(() => [
  { key: 'code', title: t('role.column.code'), minWidth: '200px' },
  { key: 'name', title: t('role.column.name'), minWidth: '160px' },
  { key: 'dataScope', title: t('role.column.dataScope'), minWidth: '140px', align: 'center' },
  { key: 'status', title: t('role.column.status'), minWidth: '80px', align: 'center' },
  { key: 'actions', title: t('role.column.actions'), minWidth: '120px', align: 'center', sticky: 'right' }
])

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
  if (row.isBuiltIn === 1) { toast.error(t('role.message.deleteBuiltInFailed')); return }
  const ok = await confirm({
    title: t('role.confirm.deleteTitle'),
    message: t('role.confirm.deleteMessage', { code: row.code }),
    variant: 'destructive'
  })
  if (!ok) return
  try {
    const res = await deleteRoleApi(row.id)
    if (res.data.code === 0) { toast.success(t('common.message.deleteSuccessful')); fetchData() }
    else toast.error(res.data.msg || t('role.message.deleteFailed'))
  } catch (e) { toast.error(e.message) }
}

onMounted(fetchData)
</script>

<template>
  <div class="space-y-3">
    <Card class="p-4">
      <div class="flex flex-wrap items-end gap-3">
        <div>
          <label class="text-xs text-muted-foreground block mb-1">{{ t('common.label.keyword') }}</label>
          <Input v-model="search.keyword" :placeholder="t('role.search.placeholder.keyword')" class="w-60" />
        </div>
        <button class="h-9 px-3 rounded bg-primary text-primary-foreground text-sm inline-flex items-center gap-1"
                @click="() => { page = 1; fetchData() }">
          <Search class="size-4" /> {{ t('common.button.search') }}
        </button>
        <button class="h-9 px-3 rounded border border-border text-sm inline-flex items-center gap-1"
                @click="resetSearch">
          <RotateCcw class="size-4" /> {{ t('common.button.reset') }}
        </button>
        <div class="ml-auto">
          <button class="h-9 px-3 rounded bg-primary text-primary-foreground text-sm inline-flex items-center gap-1"
                  @click="openCreate">
            <Plus class="size-4" /> {{ t('common.button.new') }}
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
          <Badge v-if="row.isBuiltIn === 1" variant="outline" class="ml-2 text-[10px]">{{ t('common.status.builtIn') }}</Badge>
        </template>
        <template #cell-dataScope="{ row }">
          <Badge variant="outline">{{ SCOPE_LABEL[row.dataScope] || '-' }}</Badge>
        </template>
        <template #cell-status="{ row }">
          <Badge :variant="row.status === 1 ? 'default' : 'outline'">{{ row.status === 1 ? t('common.status.active') : t('common.status.inactive') }}</Badge>
        </template>
        <template #cell-actions="{ row }">
          <div class="inline-flex items-center gap-1">
            <button class="h-7 px-2 rounded hover:bg-muted text-xs inline-flex items-center gap-1"
                    :title="row.isBuiltIn === 1 ? t('role.tooltip.viewOnly') : t('role.tooltip.edit')"
                    @click="openEdit(row)">
              <Pencil class="size-3.5" />
            </button>
            <button class="h-7 px-2 rounded hover:bg-destructive/10 text-destructive text-xs inline-flex items-center gap-1 disabled:opacity-40 disabled:cursor-not-allowed"
                    :disabled="row.isBuiltIn === 1"
                    :title="row.isBuiltIn === 1 ? t('role.tooltip.deleteDisabled') : t('common.button.delete')"
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
