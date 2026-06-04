<script setup>
import { computed, onMounted, reactive, ref } from 'vue'
import { useI18n } from 'vue-i18n'
import Card from '@/components/ui/Card.vue'
import Input from '@/components/ui/Input.vue'
import Badge from '@/components/ui/Badge.vue'
import Switch from '@/components/ui/Switch.vue'
import Drawer from '@/components/ui/Drawer.vue'
import { DataTable } from '@/components/shared/DataTable'
import { toast } from '@/composables/useToast'
import { useConfirm } from '@/composables/useConfirm'
import { Search, RotateCcw, Pencil, Play, ScrollText } from 'lucide-vue-next'
import {
  getJobListApi, getJobLogListApi, updateJobApi,
  enableJobApi, disableJobApi, runJobApi
} from '@/services/job'

const { t } = useI18n()
const { confirm } = useConfirm()

// ── list state ──────────────────────────────────────────────
const loading = ref(false)
const list = ref([])
const total = ref(0)
const page = ref(1)
const pageSize = ref(20)
const search = reactive({ keyword: '' })

const columns = computed(() => [
  { key: 'name', title: t('job.column.name'), minWidth: '180px' },
  { key: 'cron', title: t('job.column.cron'), minWidth: '130px' },
  { key: 'status', title: t('job.column.status'), minWidth: '80px', align: 'center' },
  { key: 'nextFireTime', title: t('job.column.nextFire'), minWidth: '150px' },
  { key: 'lastResult', title: t('job.column.lastResult'), minWidth: '120px' },
  { key: 'actions', title: t('job.column.actions'), minWidth: '180px', align: 'center', sticky: 'right' }
])

function fmtTime(s) {
  return s ? String(s).replace('T', ' ').slice(0, 19) : '—'
}

async function fetchData() {
  loading.value = true
  try {
    const res = await getJobListApi({ page: page.value, size: pageSize.value, keyword: search.keyword })
    if (res.data.code === 0) {
      list.value = res.data.data.records || []
      total.value = res.data.data.total || 0
    }
  } finally {
    loading.value = false
  }
}

function doSearch() { page.value = 1; fetchData() }
function resetSearch() { search.keyword = ''; doSearch() }

// ── enable / disable ────────────────────────────────────────
async function toggle(row, next) {
  try {
    const res = next === 1 ? await enableJobApi(row.id) : await disableJobApi(row.id)
    if (res.data.code === 0) {
      toast.success(next === 1 ? t('job.message.enabled') : t('job.message.disabled'))
    } else {
      toast.error(res.data.msg || t('job.message.updateFailed'))
    }
  } catch (e) {
    toast.error(e.message)
  } finally {
    fetchData()   // 結果を再取得して UI を真実に同期(失敗時はロールバック)
  }
}

// ── run now ─────────────────────────────────────────────────
async function runNow(row) {
  const ok = await confirm({ title: t('job.confirm.runTitle'), message: t('job.confirm.runMessage', { name: row.name }) })
  if (!ok) return
  try {
    const res = await runJobApi(row.id)
    if (res.data.code === 0) toast.success(t('job.message.runStarted'))
    else toast.error(res.data.msg || t('job.message.runFailed'))
  } catch (e) {
    toast.error(e.message)
  }
}

// ── edit drawer ─────────────────────────────────────────────
const showEdit = ref(false)
const editForm = reactive({ id: '', name: '', cron: '', maxRunSeconds: 300, concurrent: 0, remark: '' })

function openEdit(row) {
  editForm.id = row.id
  editForm.name = row.name
  editForm.cron = row.cron
  editForm.maxRunSeconds = row.maxRunSeconds
  editForm.concurrent = row.concurrent
  editForm.remark = row.remark || ''
  showEdit.value = true
}

async function saveEdit() {
  try {
    const res = await updateJobApi(editForm.id, {
      cron: editForm.cron,
      maxRunSeconds: editForm.maxRunSeconds,
      concurrent: editForm.concurrent,
      remark: editForm.remark
    })
    if (res.data.code === 0) {
      toast.success(t('job.message.saveSuccess'))
      showEdit.value = false
      fetchData()
    } else {
      toast.error(res.data.msg || t('job.message.updateFailed'))
    }
  } catch (e) {
    toast.error(e.message)
  }
}

// ── logs drawer ─────────────────────────────────────────────
const showLog = ref(false)
const logJobCode = ref('')
const logLoading = ref(false)
const logList = ref([])
const logTotal = ref(0)
const logPage = ref(1)
const logPageSize = ref(20)

const logColumns = computed(() => [
  { key: 'triggerType', title: t('job.log.column.triggerType'), minWidth: '80px', align: 'center' },
  { key: 'status', title: t('job.log.column.status'), minWidth: '90px', align: 'center' },
  { key: 'startTime', title: t('job.log.column.startTime'), minWidth: '150px' },
  { key: 'durationMs', title: t('job.log.column.duration'), minWidth: '90px', align: 'right' },
  { key: 'nodeId', title: t('job.log.column.node'), minWidth: '120px' },
  { key: 'triggeredBy', title: t('job.log.column.triggeredBy'), minWidth: '110px' },
  { key: 'error', title: t('job.log.column.error'), minWidth: '200px' }
])

const triggerLabel = (v) => ({ 1: t('job.triggerType.cron'), 2: t('job.triggerType.manual'), 3: t('job.triggerType.startup') }[v] || v)
const runStatusLabel = (v) => ({ 1: t('job.runStatus.running'), 2: t('job.runStatus.success'), 3: t('job.runStatus.fail'), 4: t('job.runStatus.skipped') }[v] || t('job.runStatus.none'))
const runStatusClass = (v) => ({ 1: 'text-amber-600', 2: 'text-emerald-600', 3: 'text-destructive', 4: 'text-muted-foreground' }[v] || 'text-muted-foreground')

async function fetchLogs() {
  logLoading.value = true
  try {
    const res = await getJobLogListApi({ jobCode: logJobCode.value, page: logPage.value, size: logPageSize.value })
    if (res.data.code === 0) {
      logList.value = res.data.data.records || []
      logTotal.value = res.data.data.total || 0
    }
  } finally {
    logLoading.value = false
  }
}

function openLogs(row) {
  logJobCode.value = row.jobCode
  logPage.value = 1
  showLog.value = true
  fetchLogs()
}

onMounted(fetchData)
</script>

<template>
  <div class="space-y-3">
    <Card class="p-4">
      <div class="flex flex-wrap items-end gap-3">
        <div>
          <label class="text-xs text-muted-foreground block mb-1">{{ t('common.label.keyword') }}</label>
          <Input v-model="search.keyword" :placeholder="t('job.search.placeholder.keyword')" class="w-64" />
        </div>
        <button class="h-9 px-3 rounded bg-primary text-primary-foreground text-sm inline-flex items-center gap-1"
                @click="doSearch">
          <Search class="size-4" /> {{ t('common.button.search') }}
        </button>
        <button class="h-9 px-3 rounded border border-border text-sm inline-flex items-center gap-1"
                @click="resetSearch">
          <RotateCcw class="size-4" /> {{ t('common.button.reset') }}
        </button>
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
        <template #cell-cron="{ row }">
          <code class="text-xs">{{ row.cron }}</code>
        </template>
        <template #cell-status="{ row }">
          <Switch :model-value="row.enabled" :checked-value="1" :unchecked-value="0"
                  @change="(v) => toggle(row, v)" />
        </template>
        <template #cell-nextFireTime="{ row }">
          <span class="text-xs text-muted-foreground">{{ fmtTime(row.nextFireTime) }}</span>
        </template>
        <template #cell-lastResult="{ row }">
          <span :class="['text-xs', runStatusClass(row.lastStatus)]">
            {{ runStatusLabel(row.lastStatus) }}
          </span>
          <span v-if="row.lastDurationMs != null" class="text-xs text-muted-foreground ml-1">
            ({{ row.lastDurationMs }}ms)
          </span>
        </template>
        <template #cell-actions="{ row }">
          <div class="inline-flex items-center gap-1">
            <button class="h-7 px-2 rounded hover:bg-muted text-xs inline-flex items-center gap-1"
                    :title="t('job.action.edit')" @click="openEdit(row)">
              <Pencil class="size-3.5" />
            </button>
            <button class="h-7 px-2 rounded hover:bg-muted text-xs inline-flex items-center gap-1"
                    :title="t('job.action.run')" @click="runNow(row)">
              <Play class="size-3.5" />
            </button>
            <button class="h-7 px-2 rounded hover:bg-muted text-xs inline-flex items-center gap-1"
                    :title="t('job.action.viewLog')" @click="openLogs(row)">
              <ScrollText class="size-3.5" />
            </button>
          </div>
        </template>
      </DataTable>
    </Card>

    <!-- Edit cron drawer -->
    <Drawer v-model:open="showEdit" :title="t('job.edit.title')" width="max-w-md">
      <div class="space-y-3">
        <div>
          <label class="text-xs text-muted-foreground block mb-1">{{ t('job.column.name') }}</label>
          <div class="text-sm">{{ editForm.name }}</div>
        </div>
        <div>
          <label class="text-xs text-muted-foreground block mb-1">{{ t('job.edit.label.cron') }}</label>
          <Input v-model="editForm.cron" :placeholder="t('job.edit.placeholder.cron')" />
        </div>
        <div>
          <label class="text-xs text-muted-foreground block mb-1">{{ t('job.edit.label.maxRunSeconds') }}</label>
          <Input v-model.number="editForm.maxRunSeconds" type="number" />
        </div>
        <div class="flex items-center gap-2">
          <Switch v-model="editForm.concurrent" :checked-value="1" :unchecked-value="0" />
          <span class="text-sm">{{ t('job.edit.label.concurrent') }}</span>
        </div>
        <div>
          <label class="text-xs text-muted-foreground block mb-1">{{ t('job.edit.label.remark') }}</label>
          <Input v-model="editForm.remark" :placeholder="t('job.edit.placeholder.remark')" />
        </div>
      </div>
      <template #footer>
        <div class="flex justify-end gap-2">
          <button class="h-9 px-3 rounded border border-border text-sm" @click="showEdit = false">
            {{ t('common.button.cancel') }}
          </button>
          <button class="h-9 px-3 rounded bg-primary text-primary-foreground text-sm" @click="saveEdit">
            {{ t('common.button.save') }}
          </button>
        </div>
      </template>
    </Drawer>

    <!-- Execution log drawer -->
    <Drawer v-model:open="showLog" :title="t('job.log.title')" width="max-w-3xl">
      <DataTable
        :columns="logColumns"
        :data="logList"
        :loading="logLoading"
        v-model:page="logPage"
        v-model:page-size="logPageSize"
        :total="logTotal"
        :empty-text="t('job.log.empty')"
        @update:page="fetchLogs"
        @update:page-size="fetchLogs"
      >
        <template #cell-triggerType="{ row }">
          <Badge variant="outline">{{ triggerLabel(row.triggerType) }}</Badge>
        </template>
        <template #cell-status="{ row }">
          <span :class="['text-xs font-medium', runStatusClass(row.status)]">{{ runStatusLabel(row.status) }}</span>
        </template>
        <template #cell-startTime="{ row }">
          <span class="text-xs">{{ fmtTime(row.startTime) }}</span>
        </template>
        <template #cell-error="{ row }">
          <span class="text-xs text-destructive truncate block max-w-xs" :title="row.error">{{ row.error || '—' }}</span>
        </template>
      </DataTable>
    </Drawer>
  </div>
</template>
