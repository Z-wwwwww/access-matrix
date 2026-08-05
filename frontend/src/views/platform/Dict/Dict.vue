<script setup>
import { onMounted, reactive, ref, computed } from 'vue'
import { useI18n } from 'vue-i18n'
import Card from '@/components/ui/Card.vue'
import Input from '@/components/ui/Input.vue'
import Select from '@/components/ui/Select.vue'
import Badge from '@/components/ui/Badge.vue'
import Drawer from '@/components/ui/Drawer.vue'
import { DataTable } from '@/components/shared/DataTable'
import { toast } from '@/composables/useToast'
import { useConfirm } from '@/composables/useConfirm'
import { useDictStore } from '@/stores/dict'
import { Plus, Pencil, Trash2, ChevronRight } from 'lucide-vue-next'
import {
  listDictTypesApi, addDictTypeApi, updateDictTypeApi, deleteDictTypeApi,
  listDictItemsApi, addDictItemApi, updateDictItemApi, deleteDictItemApi
} from '@/services/dict'

const { t, locale } = useI18n()
const { confirm } = useConfirm()
const dictStore = useDictStore()

const LOCALES = ['ja_JP', 'en', 'zh_CN', 'zh_TW', 'ko_KR']
const CSS_OPTIONS = computed(() => [
  { label: '(default)', value: '' },
  { label: 'default', value: 'default' },
  { label: 'outline', value: 'outline' },
  { label: 'destructive', value: 'destructive' },
  { label: 'secondary', value: 'secondary' }
])
const STATUS_OPTIONS = computed(() => [
  { label: t('dict.item.enabled'), value: 1 },
  { label: t('dict.item.disabled'), value: 0 }
])

const types = ref([])
const typesLoading = ref(false)
const selected = ref(null) // selected type code
const items = ref([])
const itemsLoading = ref(false)

function i18nLabel(map) {
  if (!map) return ''
  return map[locale.value] ?? map.ja_JP ?? Object.values(map)[0] ?? ''
}

const typeColumns = computed(() => [
  { key: 'dictCode', title: t('dict.type.columnCode') },
  { key: 'name', title: t('dict.type.columnName') },
  { key: 'itemCount', title: t('dict.type.columnItems'), align: 'center' },
  { key: 'actions', title: t('dict.type.columnActions'), align: 'center' }
])
const itemColumns = computed(() => [
  { key: 'itemValue', title: t('dict.item.columnValue') },
  { key: 'label', title: t('dict.item.columnLabel') },
  { key: 'sortNo', title: t('dict.item.columnSort'), align: 'center' },
  { key: 'status', title: t('dict.item.columnStatus'), align: 'center' },
  { key: 'actions', title: t('dict.item.columnActions'), align: 'center' }
])

async function loadTypes() {
  typesLoading.value = true
  try {
    const res = await listDictTypesApi()
    if (res.data.code === 0) types.value = res.data.data || []
    else toast.error(res.data.msg || t('dict.message.loadFailed'))
  } catch (e) { toast.error(e.message) } finally { typesLoading.value = false }
}

async function selectType(code) {
  selected.value = code
  itemsLoading.value = true
  try {
    const res = await listDictItemsApi(code)
    if (res.data.code === 0) items.value = res.data.data || []
    else toast.error(res.data.msg || t('dict.message.loadFailed'))
  } catch (e) { toast.error(e.message) } finally { itemsLoading.value = false }
}

const selectedType = computed(() => types.value.find(t0 => t0.dictCode === selected.value) || null)

// ── type drawer ──────────────────────────────────────────────────
const showType = ref(false)
const typeIsEdit = ref(false)
const typeForm = reactive({ id: null, dictCode: '', nameI18n: {}, remark: '' })

function openCreateType() {
  typeIsEdit.value = false
  Object.assign(typeForm, { id: null, dictCode: '', nameI18n: {}, remark: '' })
  showType.value = true
}
function openEditType(row) {
  typeIsEdit.value = true
  Object.assign(typeForm, { id: row.id, dictCode: row.dictCode, nameI18n: { ...(row.nameI18n || {}) }, remark: row.remark || '' })
  showType.value = true
}
function buildI18n(obj) {
  const out = {}
  for (const k of LOCALES) if (obj[k] && obj[k].trim()) out[k] = obj[k].trim()
  return Object.keys(out).length ? out : null
}
async function saveType() {
  try {
    const body = { nameI18n: buildI18n(typeForm.nameI18n), remark: typeForm.remark || null }
    let r
    if (typeIsEdit.value) r = await updateDictTypeApi(typeForm.id, body)
    else r = await addDictTypeApi({ dictCode: typeForm.dictCode, ...body })
    if (r.data.code !== 0) { toast.error(r.data.msg || t('dict.message.saveFailed')); return }
    toast.success(t('dict.message.saveSuccess'))
    showType.value = false
    await loadTypes()
  } catch (e) { toast.error(e.message) }
}
async function deleteType(row) {
  const ok = await confirm({
    title: t('dict.confirm.deleteTypeTitle'),
    message: t('dict.confirm.deleteTypeMessage', { code: row.dictCode }),
    variant: 'destructive'
  })
  if (!ok) return
  try {
    const r = await deleteDictTypeApi(row.id)
    if (r.data.code === 0) {
      toast.success(t('dict.message.deleteSuccess'))
      if (selected.value === row.dictCode) { selected.value = null; items.value = [] }
      loadTypes()
    } else toast.error(r.data.msg || t('dict.message.deleteFailed'))
  } catch (e) { toast.error(e.message) }
}

// ── item drawer ──────────────────────────────────────────────────
const showItem = ref(false)
const itemIsEdit = ref(false)
const itemForm = reactive({ id: null, itemValue: '', labelI18n: {}, sortNo: 0, cssClass: '', status: 1 })

function openCreateItem() {
  itemIsEdit.value = false
  Object.assign(itemForm, { id: null, itemValue: '', labelI18n: {}, sortNo: items.value.length + 1, cssClass: '', status: 1 })
  showItem.value = true
}
function openEditItem(row) {
  itemIsEdit.value = true
  Object.assign(itemForm, {
    id: row.id, itemValue: row.itemValue, labelI18n: { ...(row.labelI18n || {}) },
    sortNo: row.sortNo ?? 0, cssClass: row.cssClass || '', status: row.status ?? 1
  })
  showItem.value = true
}
async function saveItem() {
  try {
    const body = {
      labelI18n: buildI18n(itemForm.labelI18n),
      sortNo: Number(itemForm.sortNo) || 0,
      cssClass: itemForm.cssClass || null,
      status: itemForm.status
    }
    let r
    if (itemIsEdit.value) r = await updateDictItemApi(itemForm.id, body)
    else r = await addDictItemApi(selected.value, { itemValue: itemForm.itemValue, ...body })
    if (r.data.code !== 0) { toast.error(r.data.msg || t('dict.message.saveFailed')); return }
    toast.success(t('dict.message.saveSuccess'))
    showItem.value = false
    dictStore.invalidate(selected.value) // 让正在使用该字典的下拉刷新
    await selectType(selected.value)
    loadTypes()
  } catch (e) { toast.error(e.message) }
}
async function deleteItem(row) {
  const ok = await confirm({
    title: t('dict.confirm.deleteItemTitle'),
    message: t('dict.confirm.deleteItemMessage', { value: row.itemValue }),
    variant: 'destructive'
  })
  if (!ok) return
  try {
    const r = await deleteDictItemApi(row.id)
    if (r.data.code === 0) {
      toast.success(t('dict.message.deleteSuccess'))
      dictStore.invalidate(selected.value)
      selectType(selected.value)
      loadTypes()
    } else toast.error(r.data.msg || t('dict.message.deleteFailed'))
  } catch (e) { toast.error(e.message) }
}

onMounted(loadTypes)
</script>

<template>
  <div class="space-y-3">
    <Card class="p-4">
      <div class="flex items-center justify-between">
        <div>
          <h1 class="text-lg font-semibold">{{ t('dict.title') }}</h1>
          <p class="text-xs text-muted-foreground mt-0.5">{{ t('dict.description') }}</p>
        </div>
        <button v-permission="'platform:dict:create'"
                class="h-9 px-3 rounded bg-primary text-primary-foreground text-sm inline-flex items-center gap-1"
                @click="openCreateType">
          <Plus class="size-4" /> {{ t('dict.type.new') }}
        </button>
      </div>
    </Card>

    <div class="grid grid-cols-1 lg:grid-cols-2 gap-3">
      <!-- dict types -->
      <Card>
        <DataTable :columns="typeColumns" :data="types" :loading="typesLoading" :show-pagination="false"
                   :empty-text="t('dict.type.empty')">
          <template #cell-dictCode="{ row }">
            <button class="inline-flex items-center gap-1 font-medium hover:text-primary"
                    :class="{ 'text-primary': selected === row.dictCode }" @click="selectType(row.dictCode)">
              <ChevronRight class="size-3.5" /> {{ row.dictCode }}
              <Badge v-if="row.builtin === 1" variant="outline" class="ml-1">{{ t('dict.type.builtin') }}</Badge>
            </button>
          </template>
          <template #cell-name="{ row }">{{ i18nLabel(row.nameI18n) || '-' }}</template>
          <template #cell-actions="{ row }">
            <div class="inline-flex gap-1">
              <button v-permission="'platform:dict:update'"
                      class="h-7 px-2 rounded hover:bg-muted text-xs" @click="openEditType(row)" :title="t('common.button.edit')">
                <Pencil class="size-3.5" />
              </button>
              <button v-if="row.builtin !== 1" v-permission="'platform:dict:delete'"
                      class="h-7 px-2 rounded hover:bg-destructive/10 text-destructive text-xs"
                      @click="deleteType(row)" :title="t('common.button.delete')">
                <Trash2 class="size-3.5" />
              </button>
            </div>
          </template>
        </DataTable>
      </Card>

      <!-- dict items of the selected type -->
      <Card>
        <div v-if="!selected" class="p-8 text-center text-sm text-muted-foreground">
          {{ t('dict.type.selectHint') }}
        </div>
        <template v-else>
          <div class="flex items-center justify-between p-3 border-b border-border">
            <span class="text-sm font-medium">{{ selected }}</span>
            <button v-if="selectedType && selectedType.builtin !== 1" v-permission="'platform:dict:create'"
                    class="h-8 px-2.5 rounded bg-primary text-primary-foreground text-xs inline-flex items-center gap-1"
                    @click="openCreateItem">
              <Plus class="size-3.5" /> {{ t('dict.item.new') }}
            </button>
          </div>
          <DataTable :columns="itemColumns" :data="items" :loading="itemsLoading" :show-pagination="false"
                     :empty-text="t('dict.item.empty')">
            <template #cell-label="{ row }">{{ i18nLabel(row.labelI18n) || '-' }}</template>
            <template #cell-status="{ row }">
              <Badge :variant="row.status === 1 ? 'default' : 'outline'">
                {{ row.status === 1 ? t('dict.item.enabled') : t('dict.item.disabled') }}
              </Badge>
            </template>
            <template #cell-actions="{ row }">
              <div class="inline-flex gap-1">
                <button v-permission="'platform:dict:update'"
                        class="h-7 px-2 rounded hover:bg-muted text-xs" @click="openEditItem(row)" :title="t('common.button.edit')">
                  <Pencil class="size-3.5" />
                </button>
                <button v-permission="'platform:dict:delete'"
                        class="h-7 px-2 rounded hover:bg-destructive/10 text-destructive text-xs"
                        @click="deleteItem(row)" :title="t('common.button.delete')">
                  <Trash2 class="size-3.5" />
                </button>
              </div>
            </template>
          </DataTable>
        </template>
      </Card>
    </div>

    <!-- type create/edit -->
    <Drawer v-model:open="showType" :title="typeIsEdit ? t('dict.type.edit') : t('dict.type.create')" width="max-w-md">
      <div class="space-y-3">
        <div>
          <label class="text-xs text-muted-foreground block mb-1">{{ t('dict.label.code') }} <span class="text-destructive">*</span></label>
          <Input v-model="typeForm.dictCode" :disabled="typeIsEdit" placeholder="lower_snake_case" />
          <p v-if="typeIsEdit" class="text-xs text-muted-foreground mt-1">{{ t('dict.type.codeFrozen') }}</p>
        </div>
        <div>
          <label class="text-xs text-muted-foreground block mb-1">{{ t('dict.label.name') }}</label>
          <div class="space-y-1.5">
            <div v-for="lc in LOCALES" :key="lc" class="flex items-center gap-2">
              <span class="text-xs text-muted-foreground w-14 shrink-0">{{ lc }}</span>
              <Input v-model="typeForm.nameI18n[lc]" />
            </div>
          </div>
        </div>
        <div>
          <label class="text-xs text-muted-foreground block mb-1">{{ t('dict.label.remark') }}</label>
          <Input v-model="typeForm.remark" />
        </div>
      </div>
      <template #footer>
        <div class="flex justify-end gap-2">
          <button class="h-9 px-3 rounded border border-border text-sm" @click="showType = false">{{ t('common.button.cancel') }}</button>
          <button class="h-9 px-3 rounded bg-primary text-primary-foreground text-sm" @click="saveType">{{ t('common.button.save') }}</button>
        </div>
      </template>
    </Drawer>

    <!-- item create/edit -->
    <Drawer v-model:open="showItem" :title="itemIsEdit ? t('dict.item.edit') : t('dict.item.create')" width="max-w-md">
      <div class="space-y-3">
        <div>
          <label class="text-xs text-muted-foreground block mb-1">{{ t('dict.label.value') }} <span class="text-destructive">*</span></label>
          <Input v-model="itemForm.itemValue" :disabled="itemIsEdit" />
          <p v-if="itemIsEdit" class="text-xs text-muted-foreground mt-1">{{ t('dict.item.valueFrozen') }}</p>
        </div>
        <div>
          <label class="text-xs text-muted-foreground block mb-1">{{ t('dict.label.label') }}</label>
          <div class="space-y-1.5">
            <div v-for="lc in LOCALES" :key="lc" class="flex items-center gap-2">
              <span class="text-xs text-muted-foreground w-14 shrink-0">{{ lc }}</span>
              <Input v-model="itemForm.labelI18n[lc]" />
            </div>
          </div>
        </div>
        <div class="grid grid-cols-2 gap-3">
          <div>
            <label class="text-xs text-muted-foreground block mb-1">{{ t('dict.label.sort') }}</label>
            <Input v-model="itemForm.sortNo" type="number" />
          </div>
          <div>
            <label class="text-xs text-muted-foreground block mb-1">{{ t('dict.label.status') }}</label>
            <Select v-model="itemForm.status" :options="STATUS_OPTIONS" />
          </div>
        </div>
        <div>
          <label class="text-xs text-muted-foreground block mb-1">{{ t('dict.label.cssClass') }}</label>
          <Select v-model="itemForm.cssClass" :options="CSS_OPTIONS" />
        </div>
      </div>
      <template #footer>
        <div class="flex justify-end gap-2">
          <button class="h-9 px-3 rounded border border-border text-sm" @click="showItem = false">{{ t('common.button.cancel') }}</button>
          <button class="h-9 px-3 rounded bg-primary text-primary-foreground text-sm" @click="saveItem">{{ t('common.button.save') }}</button>
        </div>
      </template>
    </Drawer>
  </div>
</template>
