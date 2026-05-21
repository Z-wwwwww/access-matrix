<script setup>
import { onMounted, reactive, ref, computed } from 'vue'
import Card from '@/components/ui/Card.vue'
import Input from '@/components/ui/Input.vue'
import Select from '@/components/ui/Select.vue'
import Badge from '@/components/ui/Badge.vue'
import Drawer from '@/components/ui/Drawer.vue'
import { DataTable } from '@/components/shared/DataTable'
import { toast } from '@/composables/useToast'
import { useConfirm } from '@/composables/useConfirm'
import { Plus, Pencil, Trash2, ChevronRight, ChevronDown } from 'lucide-vue-next'
import {
  getMenuIndexApi, addMenuApi, editMenuApi, deleteMenuApi
} from '../../../../services/menu'

const { confirm } = useConfirm()

const list = ref([])
const loading = ref(false)
const expanded = ref(new Set())

const showEdit = ref(false)
const isEdit = ref(false)
const editForm = reactive({
  id: null, parentId: null, code: '', title: '', menuType: 2,
  path: '', component: '', icon: '', sortOrder: 0,
  hide: 0, hideFooter: 0, hideSidebar: 0,
  permissionCode: '', status: 1
})

const TYPE_LABEL = { 1: 'ディレクトリ', 2: 'メニュー', 3: 'ボタン' }
const menuTypeOptions = [
  { label: 'ディレクトリ', value: 1 },
  { label: 'メニュー', value: 2 },
  { label: 'ボタン', value: 3 }
]
const yesNoOptions = [
  { label: 'いいえ', value: 0 }, { label: 'はい', value: 1 }
]

const tree = computed(() => buildTree(list.value))

function buildTree(flat) {
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

// DataTable column schema. Tree chevron + indent live in the `#cell-title`
// slot; DataTable's built-in `expandable` is row-detail expansion (a slot
// rendered as a panel below the row), not parent/child indentation, so we
// keep the expanded-state and flattening logic local and hand DataTable a
// pre-flattened list.
const columns = [
  { key: 'title',          title: '名称 / パス' },
  { key: 'menuType',       title: '種類' },
  { key: 'component',      title: 'コンポーネント' },
  { key: 'permissionCode', title: '権限' },
  { key: 'hide',           title: '非表示', align: 'center' },
  { key: 'actions',        title: '操作',  align: 'center' }
]

async function fetchData() {
  loading.value = true
  try {
    const res = await getMenuIndexApi()
    if (res.data.code === 0) {
      list.value = res.data.data || []
      // 默认展开根
      list.value.filter(m => !m.parentId).forEach(m => expanded.value.add(m.id))
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
    id: null, parentId: parent?.id || null, code: '', title: '', menuType: parent ? 2 : 1,
    path: '', component: '', icon: '', sortOrder: 0,
    hide: 0, hideFooter: 0, hideSidebar: 0,
    permissionCode: '', status: 1
  })
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
      const r = await editMenuApi(editForm)
      if (r.data.code !== 0) { toast.error(r.data.msg); return }
    } else {
      const r = await addMenuApi(editForm)
      if (r.data.code !== 0) { toast.error(r.data.msg); return }
    }
    toast.success('保存しました')
    showEdit.value = false
    fetchData()
  } catch (e) { toast.error(e.message) }
}

async function handleDelete(row) {
  const ok = await confirm({
    title: 'メニュー削除',
    message: `「${row.code}」を削除しますか？`,
    variant: 'destructive'
  })
  if (!ok) return
  try {
    const r = await deleteMenuApi(row.id)
    if (r.data.code === 0) { toast.success('削除しました'); fetchData() }
    else toast.error(r.data.msg)
  } catch (e) { toast.error(e.message) }
}

onMounted(fetchData)
</script>

<template>
  <div class="space-y-3">
    <Card class="p-4 flex items-center justify-between">
      <h1 class="text-lg font-semibold">メニュー管理</h1>
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
        empty-text="メニューがありません"
      >
        <template #cell-title="{ row }">
          <div class="flex items-center gap-1" :style="{ paddingLeft: row.level * 18 + 'px' }">
            <button v-if="row.children?.length" class="size-4 inline-flex" @click="toggle(row.id)">
              <ChevronDown v-if="expanded.has(row.id)" class="size-4" />
              <ChevronRight v-else class="size-4" />
            </button>
            <span v-else class="inline-block size-4"></span>
            <span class="font-medium">{{ row.title }}</span>
            <span class="text-xs text-muted-foreground font-mono ml-2">{{ row.path || '-' }}</span>
          </div>
        </template>
        <template #cell-menuType="{ row }">
          <Badge variant="outline">{{ TYPE_LABEL[row.menuType] }}</Badge>
        </template>
        <template #cell-component="{ row }">
          <span class="text-xs text-muted-foreground font-mono">{{ row.component || '-' }}</span>
        </template>
        <template #cell-permissionCode="{ row }">
          <span class="text-xs font-mono">{{ row.permissionCode || '-' }}</span>
        </template>
        <template #cell-hide="{ row }">{{ row.hide === 1 ? '✓' : '' }}</template>
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

    <Drawer v-model:open="showEdit" :title="isEdit ? 'メニュー編集' : 'メニュー新規'" width="max-w-lg">
      <div class="space-y-3">
        <div class="grid grid-cols-2 gap-3">
          <div>
            <label class="text-xs text-muted-foreground block mb-1">コード <span class="text-destructive">*</span></label>
            <Input v-model="editForm.code" :disabled="isEdit" placeholder="system.user" />
          </div>
          <div>
            <label class="text-xs text-muted-foreground block mb-1">名称 <span class="text-destructive">*</span></label>
            <Input v-model="editForm.title" />
          </div>
        </div>
        <div class="grid grid-cols-2 gap-3">
          <div>
            <label class="text-xs text-muted-foreground block mb-1">種類</label>
            <Select v-model="editForm.menuType" :options="menuTypeOptions" />
          </div>
          <div>
            <label class="text-xs text-muted-foreground block mb-1">並び順</label>
            <Input v-model.number="editForm.sortOrder" type="number" />
          </div>
        </div>
        <div>
          <label class="text-xs text-muted-foreground block mb-1">親メニュー ID</label>
          <Input v-model="editForm.parentId" placeholder="ルートの場合は空" />
        </div>
        <div>
          <label class="text-xs text-muted-foreground block mb-1">パス</label>
          <Input v-model="editForm.path" placeholder="/system/user" />
        </div>
        <div>
          <label class="text-xs text-muted-foreground block mb-1">コンポーネント</label>
          <Input v-model="editForm.component" placeholder="/system/User/User" />
        </div>
        <div class="grid grid-cols-2 gap-3">
          <div>
            <label class="text-xs text-muted-foreground block mb-1">アイコン</label>
            <Input v-model="editForm.icon" placeholder="user / setting" />
          </div>
          <div>
            <label class="text-xs text-muted-foreground block mb-1">権限コード</label>
            <Input v-model="editForm.permissionCode" placeholder="user:read" />
          </div>
        </div>
        <div class="grid grid-cols-3 gap-3">
          <div>
            <label class="text-xs text-muted-foreground block mb-1">非表示</label>
            <Select v-model="editForm.hide" :options="yesNoOptions" />
          </div>
          <div>
            <label class="text-xs text-muted-foreground block mb-1">サイドバー非表示</label>
            <Select v-model="editForm.hideSidebar" :options="yesNoOptions" />
          </div>
          <div>
            <label class="text-xs text-muted-foreground block mb-1">フッター非表示</label>
            <Select v-model="editForm.hideFooter" :options="yesNoOptions" />
          </div>
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
