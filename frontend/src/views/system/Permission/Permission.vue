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

const { t } = useI18n()
const { confirm } = useConfirm()
import {
  getPermissionListApi, addPermissionApi, updatePermissionApi, deletePermissionApi
} from '../../../../services/permission'
import Drawer from '@/components/ui/Drawer.vue'

const loading = ref(false)
const list = ref([])
const total = ref(0)
const page = ref(1)
const pageSize = ref(20)
const search = reactive({ keyword: '', module: '' })

const showEdit = ref(false)
const editForm = reactive({ id: null, code: '', name: '', resource: '', action: '', module: '', description: '' })
const isEdit = ref(false)

const columns = computed(() => [
  { key: 'code', title: t('permission.column.code'), minWidth: '220px' },
  { key: 'name', title: t('permission.column.name'), minWidth: '160px' },
  { key: 'module', title: t('permission.column.module'), minWidth: '100px' },
  { key: 'resource', title: t('permission.column.resource'), minWidth: '100px' },
  { key: 'action', title: t('permission.column.action'), minWidth: '120px' },
  { key: 'actions', title: t('permission.column.actions'), minWidth: '100px', align: 'center', sticky: 'right' }
])

async function fetchData() {
  loading.value = true
  try {
    const res = await getPermissionListApi({ page: page.value, size: pageSize.value, ...search })
    if (res.data.code === 0) {
      list.value = res.data.data.records || []
      total.value = res.data.data.total || 0
    }
  } finally { loading.value = false }
}

function resetSearch() { search.keyword = ''; search.module = ''; page.value = 1; fetchData() }

function openCreate() {
  isEdit.value = false
  Object.assign(editForm, { id: null, code: '', name: '', resource: '', action: '', module: '', description: '' })
  showEdit.value = true
}
function openEdit(row) {
  isEdit.value = true
  Object.assign(editForm, row)
  showEdit.value = true
}

async function save() {
  try {
    if (isEdit.value) {
      const body = { name: editForm.name, module: editForm.module, description: editForm.description }
      const r = await updatePermissionApi(editForm.id, body)
      if (r.data.code !== 0) { toast.error(r.data.msg); return }
    } else {
      const r = await addPermissionApi(editForm)
      if (r.data.code !== 0) { toast.error(r.data.msg); return }
    }
    toast.success(t('common.message.saveSuccessful'))
    showEdit.value = false
    fetchData()
  } catch (e) { toast.error(e.message) }
}

async function handleDelete(row) {
  if (row.isBuiltIn === 1) { toast.error(t('permission.message.deleteBuiltInFailed')); return }
  const ok = await confirm({
    title: t('permission.confirm.deleteTitle'),
    message: t('permission.confirm.deleteMessage', { code: row.code }),
    variant: 'destructive'
  })
  if (!ok) return
  try {
    const r = await deletePermissionApi(row.id)
    if (r.data.code === 0) { toast.success(t('common.message.deleteSuccessful')); fetchData() }
    else toast.error(r.data.msg)
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
          <Input v-model="search.keyword" :placeholder="t('permission.search.placeholder.keyword')" class="w-60" />
        </div>
        <div>
          <label class="text-xs text-muted-foreground block mb-1">{{ t('permission.search.label.module') }}</label>
          <Input v-model="search.module" :placeholder="t('permission.search.placeholder.module')" class="w-40" />
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
          <span class="font-mono text-xs">{{ row.code }}</span>
          <Badge v-if="row.isBuiltIn === 1" variant="outline" class="ml-2 text-[10px]">{{ t('common.status.builtIn') }}</Badge>
        </template>
        <template #cell-actions="{ row }">
          <div class="inline-flex gap-1">
            <button class="h-7 px-2 rounded hover:bg-muted text-xs disabled:opacity-40 disabled:cursor-not-allowed"
                    :disabled="row.isBuiltIn === 1"
                    :title="row.isBuiltIn === 1 ? t('permission.tooltip.editDisabled') : t('permission.tooltip.edit')"
                    @click="openEdit(row)">
              <Pencil class="size-3.5" />
            </button>
            <button class="h-7 px-2 rounded hover:bg-destructive/10 text-destructive text-xs disabled:opacity-40 disabled:cursor-not-allowed"
                    :disabled="row.isBuiltIn === 1"
                    :title="row.isBuiltIn === 1 ? t('permission.tooltip.deleteDisabled') : t('common.button.delete')"
                    @click="handleDelete(row)">
              <Trash2 class="size-3.5" />
            </button>
          </div>
        </template>
      </DataTable>
    </Card>

    <Drawer v-model:open="showEdit" :title="isEdit ? t('permission.edit.titleEdit') : t('permission.edit.titleCreate')" width="max-w-md">
      <div class="space-y-3">
        <div>
          <label class="text-xs text-muted-foreground block mb-1">{{ t('permission.edit.label.code') }} <span class="text-destructive">*</span></label>
          <Input v-model="editForm.code" :disabled="isEdit" :placeholder="t('permission.edit.placeholder.code')" />
        </div>
        <div>
          <label class="text-xs text-muted-foreground block mb-1">{{ t('permission.edit.label.name') }} <span class="text-destructive">*</span></label>
          <Input v-model="editForm.name" />
        </div>
        <div class="grid grid-cols-2 gap-3">
          <div>
            <label class="text-xs text-muted-foreground block mb-1">{{ t('permission.edit.label.resource') }}</label>
            <Input v-model="editForm.resource" :disabled="isEdit" />
          </div>
          <div>
            <label class="text-xs text-muted-foreground block mb-1">{{ t('permission.edit.label.action') }}</label>
            <Input v-model="editForm.action" :disabled="isEdit" />
          </div>
        </div>
        <div>
          <label class="text-xs text-muted-foreground block mb-1">{{ t('permission.edit.label.module') }}</label>
          <Input v-model="editForm.module" :placeholder="t('permission.edit.placeholder.module')" />
        </div>
        <div>
          <label class="text-xs text-muted-foreground block mb-1">{{ t('permission.edit.label.description') }}</label>
          <Input v-model="editForm.description" />
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
