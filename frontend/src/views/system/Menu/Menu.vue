<script setup>
import { onMounted, reactive, ref, computed, watch } from 'vue'
import { useI18n } from 'vue-i18n'
import Card from '@/components/ui/Card.vue'
import Input from '@/components/ui/Input.vue'
import Select from '@/components/ui/Select.vue'
import Switch from '@/components/ui/Switch.vue'
import Badge from '@/components/ui/Badge.vue'
import Drawer from '@/components/ui/Drawer.vue'
import IconPicker from '@/components/shared/IconPicker.vue'
import MenuPicker from '@/components/shared/MenuPicker.vue'
import LucideIcon from '@/components/shared/LucideIcon.vue'
import { DataTable } from '@/components/shared/DataTable'
import { toast } from '@/composables/useToast'
import { useConfirm } from '@/composables/useConfirm'
import { useMenuTitle } from '@/composables/useMenuTitle'
import { Plus, Pencil, Trash2, ChevronRight, ChevronDown, HelpCircle, Pin } from 'lucide-vue-next'
import {
  getMenuIndexApi, addMenuApi, editMenuApi, deleteMenuApi
} from '../../../../services/menu'
import { getPermissionsByModuleApi } from '../../../../services/permission'

const { t } = useI18n()
const { confirm } = useConfirm()
const { translate: translateMenu } = useMenuTitle()

// AppHeader の langOptions と同じ並び。primary locale は ja_JP（システム既定）。
const SUPPORTED_LANGS = [
  { code: 'ja_JP', label: '日本語', primary: true },
  { code: 'en',    label: 'English' },
  { code: 'zh_CN', label: '简体中文' },
  { code: 'zh_TW', label: '繁體中文' },
  { code: 'ko_KR', label: '한국어' }
]

const list = ref([])
const loading = ref(false)
const expanded = ref(new Set())

const showEdit = ref(false)
const isEdit = ref(false)
const editForm = reactive({
  id: null, parentId: null, code: '', title: '', titleI18n: {}, menuType: 2,
  path: '', component: '', icon: '', sortOrder: 0,
  hide: 0, hideFooter: 0, hideSidebar: 0, pinned: 0,
  permissionCode: '', status: 1
})

const TYPE_LABEL = computed(() => ({
  1: t('menu.option.type.directory'),
  2: t('menu.option.type.menu'),
  3: t('menu.option.type.button')
}))
const menuTypeOptions = computed(() => [
  { label: t('menu.option.type.directory'), value: 1 },
  { label: t('menu.option.type.menu'), value: 2 },
  { label: t('menu.option.type.button'), value: 3 }
])

// 只允许 menuType=2（页面/菜单）置顶 —— 目录和按钮置顶在 UI 上没意义
const canPin = computed(() => editForm.menuType === 2)

// 権限コードのドロップダウン用：起動時に /admin/permission/by-module を一度取って
// module ごとにグルーピングされた一覧から Select options を組み立てる。
// label は i18n（permission.<code>）から日本語表示名を引き、code を末尾に小書きする。
const permissionOptions = ref([])

async function loadPermissionOptions() {
  try {
    const res = await getPermissionsByModuleApi()
    if (res.data.code !== 0) return
    const byModule = res.data.data || {}
    const opts = []
    for (const module of Object.keys(byModule).sort()) {
      for (const p of byModule[module] || []) {
        opts.push({
          value: p.code,
          // 表示名（i18n）+ code を 1 行で見せる。Select は label のみ検索対象だが
          // code を label に含めることで code 検索もそのまま効く。
          label: `${t(`permission.${p.code}`, p.code)} (${p.code})`
        })
      }
    }
    permissionOptions.value = opts
  } catch { /* 非致命：ドロップダウンが空になるだけ */ }
}

// 親メニュー選択時、自分自身と子孫を候補から除外する（循環防止）。
// ボタン (menuType=3) は親に出来ないので候補から外す。
const parentExcludeIds = computed(() => {
  if (!editForm.id) return []
  const ids = [editForm.id]
  const byParent = new Map()
  for (const m of list.value) {
    const k = m.parentId || ''
    if (!byParent.has(k)) byParent.set(k, [])
    byParent.get(k).push(m)
  }
  function walk(id) {
    for (const c of byParent.get(id) || []) {
      ids.push(c.id)
      walk(c.id)
    }
  }
  walk(editForm.id)
  return ids
})

// menuType 切换离开 2 → 强制把 pinned 清零，避免历史脏数据保留
watch(
  () => editForm.menuType,
  (t) => { if (t !== 2 && editForm.pinned) editForm.pinned = 0 }
)

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
const columns = computed(() => [
  { key: 'title',          title: t('menu.column.title') },
  { key: 'menuType',       title: t('menu.column.type') },
  { key: 'component',      title: t('menu.column.component') },
  { key: 'permissionCode', title: t('menu.column.permission') },
  { key: 'hide',           title: t('menu.column.hide'), align: 'center' },
  { key: 'actions',        title: t('menu.column.actions'), align: 'center' }
])

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
    id: null, parentId: parent?.id || null, code: '', title: '', titleI18n: {},
    menuType: parent ? 2 : 1,
    path: '', component: '', icon: '', sortOrder: 0,
    hide: 0, hideFooter: 0, hideSidebar: 0, pinned: 0,
    permissionCode: '', status: 1
  })
  showEdit.value = true
}
function openEdit(row) {
  isEdit.value = true
  Object.assign(editForm, row)
  // backend が null を返してくる場合があるので reactive オブジェクトとして空 map を担保。
  editForm.titleI18n = { ...(row.titleI18n || {}) }
  // 防御：历史脏数据可能存在 menuType≠2 但 pinned=1 的记录，
  // 进入编辑时直接清零，否则 switch 被 disabled 用户无法手动关掉。
  if (editForm.menuType !== 2) editForm.pinned = 0
  showEdit.value = true
}

async function save() {
  // 提交前再做一次保险：menuType 不是「メニュー」时不允许 pinned。
  if (editForm.menuType !== 2) editForm.pinned = 0
  // titleI18n の空文字キーは送らない（DB に "" を入れたくない）。
  // `title`（fallback カラム）は ja_JP の翻訳から自動派生する（NOT NULL 制約のため）。
  const cleanedI18n = {}
  for (const [k, v] of Object.entries(editForm.titleI18n || {})) {
    if (v && String(v).trim()) cleanedI18n[k] = String(v).trim()
  }
  editForm.titleI18n = cleanedI18n
  if (!editForm.title || !editForm.title.trim()) {
    editForm.title = cleanedI18n.ja_JP || Object.values(cleanedI18n)[0] || editForm.code
  }
  if (!cleanedI18n.ja_JP) {
    toast.error(t('menu.edit.error.titleJaRequired'))
    return
  }
  try {
    if (isEdit.value) {
      const r = await editMenuApi(editForm)
      if (r.data.code !== 0) { toast.error(r.data.msg); return }
    } else {
      const r = await addMenuApi(editForm)
      if (r.data.code !== 0) { toast.error(r.data.msg); return }
    }
    toast.success(t('common.message.saveSuccessful'))
    showEdit.value = false
    fetchData()
  } catch (e) { toast.error(e.message) }
}

async function handleDelete(row) {
  const ok = await confirm({
    title: t('menu.confirm.deleteTitle'),
    message: t('menu.confirm.deleteMessage', { code: row.code }),
    variant: 'destructive'
  })
  if (!ok) return
  try {
    const r = await deleteMenuApi(row.id)
    if (r.data.code === 0) { toast.success(t('common.message.deleteSuccessful')); fetchData() }
    else toast.error(r.data.msg)
  } catch (e) { toast.error(e.message) }
}

onMounted(() => {
  fetchData()
  loadPermissionOptions()
})
</script>

<template>
  <div class="space-y-3">
    <Card class="p-4 flex items-center justify-between">
      <h1 class="text-lg font-semibold">{{ t('menu.title') }}</h1>
      <button class="h-9 px-3 rounded bg-primary text-primary-foreground text-sm inline-flex items-center gap-1"
              @click="openCreate(null)">
        <Plus class="size-4" /> {{ t('menu.button.addRoot') }}
      </button>
    </Card>

    <Card>
      <DataTable
        :columns="columns"
        :data="flatTree"
        :loading="loading"
        :show-pagination="false"
        :empty-text="t('menu.message.noMenus')"
      >
        <template #cell-title="{ row }">
          <div class="flex items-center gap-1" :style="{ paddingLeft: row.level * 18 + 'px' }">
            <button v-if="row.children?.length" class="size-4 inline-flex" @click="toggle(row.id)">
              <ChevronDown v-if="expanded.has(row.id)" class="size-4" />
              <ChevronRight v-else class="size-4" />
            </button>
            <span v-else class="inline-block size-4"></span>
            <LucideIcon v-if="row.icon" :name="row.icon" :size="14" />
            <span class="font-medium">{{ translateMenu(row) }}</span>
            <Pin
              v-if="row.pinned === 1"
              class="size-3.5 text-brand-orange fill-brand-orange"
              :title="t('menu.edit.tip.pinned')"
            />
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
          <template v-if="row.permissionCode">
            <span>{{ t(`permission.${row.permissionCode}`, row.permissionCode) }}</span>
            <span class="text-xs text-muted-foreground font-mono ml-1">({{ row.permissionCode }})</span>
          </template>
          <span v-else class="text-xs text-muted-foreground">-</span>
        </template>
        <template #cell-hide="{ row }">{{ row.hide === 1 ? '✓' : '' }}</template>
        <template #cell-actions="{ row }">
          <div class="inline-flex gap-1">
            <button class="h-7 px-2 rounded hover:bg-muted text-xs" @click="openCreate(row)" :title="t('menu.tooltip.addChild')">
              <Plus class="size-3.5" />
            </button>
            <button class="h-7 px-2 rounded hover:bg-muted text-xs" @click="openEdit(row)" :title="t('menu.tooltip.edit')">
              <Pencil class="size-3.5" />
            </button>
            <button class="h-7 px-2 rounded hover:bg-destructive/10 text-destructive text-xs" @click="handleDelete(row)" :title="t('common.button.delete')">
              <Trash2 class="size-3.5" />
            </button>
          </div>
        </template>
      </DataTable>
    </Card>

    <Drawer v-model:open="showEdit" :title="isEdit ? t('menu.edit.titleEdit') : t('menu.edit.titleCreate')" width="max-w-lg">
      <div class="space-y-3">
        <div>
          <label class="text-xs text-muted-foreground block mb-1">{{ t('menu.edit.label.code') }} <span class="text-destructive">*</span></label>
          <Input v-model="editForm.code" :disabled="isEdit" :placeholder="t('menu.edit.placeholder.code')" />
        </div>
        <!-- 多言語タイトル：ja_JP は必須（既定 locale）、他は任意。 -->
        <div class="space-y-2 p-3 rounded border border-border bg-muted/30">
          <div class="text-xs text-muted-foreground">{{ t('menu.edit.label.titleI18n') }}</div>
          <div
            v-for="lang in SUPPORTED_LANGS"
            :key="lang.code"
            class="flex items-center gap-2"
          >
            <span class="w-20 shrink-0 text-xs font-mono text-muted-foreground">{{ lang.label }}</span>
            <Input
              v-model="editForm.titleI18n[lang.code]"
              :placeholder="lang.primary
                ? t('menu.edit.placeholder.titleI18nPrimary')
                : t('menu.edit.placeholder.titleI18nOptional')"
              class="flex-1"
            />
            <span v-if="lang.primary" class="text-destructive text-xs shrink-0">*</span>
            <span v-else class="text-xs text-muted-foreground shrink-0">&nbsp;</span>
          </div>
        </div>
        <div class="grid grid-cols-2 gap-3">
          <div>
            <label class="text-xs text-muted-foreground block mb-1">{{ t('menu.edit.label.type') }}</label>
            <Select v-model="editForm.menuType" :options="menuTypeOptions" />
          </div>
          <div>
            <label class="text-xs text-muted-foreground block mb-1">{{ t('menu.edit.label.sortOrder') }}</label>
            <Input v-model.number="editForm.sortOrder" type="number" />
          </div>
        </div>
        <div>
          <label class="text-xs text-muted-foreground block mb-1">{{ t('menu.edit.label.parentId') }}</label>
          <MenuPicker v-model="editForm.parentId" :exclude="parentExcludeIds" :exclude-types="[3]" :placeholder="t('menu.edit.placeholder.parentId')" />
        </div>
        <div>
          <label class="text-xs text-muted-foreground block mb-1">{{ t('menu.edit.label.path') }}</label>
          <Input v-model="editForm.path" :placeholder="t('menu.edit.placeholder.path')" />
        </div>
        <div>
          <label class="text-xs text-muted-foreground block mb-1">{{ t('menu.edit.label.component') }}</label>
          <Input v-model="editForm.component" :placeholder="t('menu.edit.placeholder.component')" />
        </div>
        <div class="grid grid-cols-2 gap-3">
          <div>
            <label class="text-xs text-muted-foreground block mb-1">{{ t('menu.edit.label.icon') }}</label>
            <IconPicker v-model="editForm.icon" />
          </div>
          <div>
            <label class="text-xs text-muted-foreground block mb-1">{{ t('menu.edit.label.permissionCode') }}</label>
            <Select v-model="editForm.permissionCode"
                    :options="permissionOptions"
                    :placeholder="t('menu.edit.placeholder.permissionCode')"
                    :searchable="true"
                    clearable
                    panel-auto-width />
          </div>
        </div>
        <div class="grid grid-cols-2 gap-3">
          <div>
            <label class="text-xs text-muted-foreground block mb-1 flex items-center gap-1">
              {{ t('menu.edit.label.pinned') }}
              <span
                class="inline-flex cursor-help text-muted-foreground/70 hover:text-foreground"
                :title="canPin ? t('menu.edit.tip.pinned') : t('menu.edit.tip.pinnedDisabled')"
              >
                <HelpCircle class="size-3.5" />
              </span>
            </label>
            <Switch
              v-model="editForm.pinned"
              :checked-value="1"
              :unchecked-value="0"
              :disabled="!canPin"
            />
          </div>
          <div>
            <label class="text-xs text-muted-foreground block mb-1 flex items-center gap-1">
              {{ t('menu.edit.label.hide') }}
              <span class="inline-flex cursor-help text-muted-foreground/70 hover:text-foreground" :title="t('menu.edit.tip.hide')">
                <HelpCircle class="size-3.5" />
              </span>
            </label>
            <Switch v-model="editForm.hide" :checked-value="1" :unchecked-value="0" />
          </div>
          <div>
            <label class="text-xs text-muted-foreground block mb-1 flex items-center gap-1">
              {{ t('menu.edit.label.hideSidebar') }}
              <span class="inline-flex cursor-help text-muted-foreground/70 hover:text-foreground" :title="t('menu.edit.tip.hideSidebar')">
                <HelpCircle class="size-3.5" />
              </span>
            </label>
            <Switch v-model="editForm.hideSidebar" :checked-value="1" :unchecked-value="0" />
          </div>
          <div>
            <label class="text-xs text-muted-foreground block mb-1 flex items-center gap-1">
              {{ t('menu.edit.label.hideFooter') }}
              <span class="inline-flex cursor-help text-muted-foreground/70 hover:text-foreground" :title="t('menu.edit.tip.hideFooter')">
                <HelpCircle class="size-3.5" />
              </span>
            </label>
            <Switch v-model="editForm.hideFooter" :checked-value="1" :unchecked-value="0" />
          </div>
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
