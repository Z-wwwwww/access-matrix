<script setup>
import { onMounted, reactive, ref } from 'vue'
import { useI18n } from 'vue-i18n'
import Card from '@/components/ui/Card.vue'
import Input from '@/components/ui/Input.vue'
import Badge from '@/components/ui/Badge.vue'
import Drawer from '@/components/ui/Drawer.vue'
import { DataTable } from '@/components/shared/DataTable'
import { toast } from '@/composables/useToast'
import { useConfirm } from '@/composables/useConfirm'
import { toJSTDateTimeDisp } from '@/lib/date'
import { Search, RotateCcw, Eye, RefreshCw } from 'lucide-vue-next'
import { listEventsApi, getEventApi, redriveEventApi, redriveFailedEventsApi } from '@/services/event'

const { t } = useI18n()
const { confirm } = useConfirm()

const loading = ref(false)
const list = ref([])
const total = ref(0)
const page = ref(1)
const pageSize = ref(20)
// dispatchState: null = all, 0 pending, 1 dispatched, 2 failed
const search = reactive({ dispatchState: null, eventType: '', keyword: '' })

const detail = ref(null)        // event Detail for the drawer; null = closed
const detailLoading = ref(false)

const STATES = [
  { value: null, label: () => t('platform.event.state.all') },
  { value: 0, label: () => t('platform.event.state.pending') },
  { value: 1, label: () => t('platform.event.state.dispatched') },
  { value: 2, label: () => t('platform.event.state.failed') }
]

const columns = [
  { key: 'occurredAt',     label: () => t('platform.event.column.occurredAt'),  width: '170px' },
  { key: 'eventType',      label: () => t('platform.event.column.eventType') },
  { key: 'aggregate',      label: () => t('platform.event.column.aggregate') },
  { key: 'dispatchState',  label: () => t('platform.event.column.status'),      width: '110px' },
  { key: 'dispatchAttempts', label: () => t('platform.event.column.attempts'),  width: '90px' },
  { key: 'actions',        label: () => t('platform.event.column.actions'),     width: '130px' }
]

// state code → { label, badge variant/classes }
function stateLabel(s) {
  return s === 1 ? t('platform.event.state.dispatched')
       : s === 2 ? t('platform.event.state.failed')
       : t('platform.event.state.pending')
}
function stateClass(s) {
  return s === 1 ? 'border-emerald-500/40 text-emerald-600'
       : s === 2 ? 'border-destructive/40 text-destructive'
       : 'border-amber-500/40 text-amber-600'
}
function actorTypeLabel(a) {
  return a === 2 ? t('platform.event.actorType.ai')
       : a === 3 ? t('platform.event.actorType.system')
       : t('platform.event.actorType.human')
}
function fmtDate(s) {
  return s ? toJSTDateTimeDisp(s) : '—'
}
function prettyPayload(raw) {
  if (!raw) return '—'
  try {
    return JSON.stringify(JSON.parse(raw), null, 2)
  } catch {
    return raw
  }
}

async function fetchData() {
  loading.value = true
  try {
    const res = await listEventsApi({
      page: page.value,
      size: pageSize.value,
      dispatchState: search.dispatchState ?? undefined,
      eventType: search.eventType || undefined,
      keyword: search.keyword || undefined
    })
    if (res.data.code === 0) {
      list.value = res.data.data.records || []
      total.value = res.data.data.total || 0
    } else {
      toast.error(res.data.msg || t('platform.event.message.loadFailed'))
    }
  } catch (e) {
    toast.error(e.message)
  } finally {
    loading.value = false
  }
}

function applySearch() {
  page.value = 1
  fetchData()
}
function resetSearch() {
  search.dispatchState = null
  search.eventType = ''
  search.keyword = ''
  applySearch()
}
function pickState(v) {
  search.dispatchState = v
  applySearch()
}

async function openDetail(row) {
  detailLoading.value = true
  detail.value = { id: row.id }   // open the drawer immediately
  try {
    const res = await getEventApi(row.id)
    if (res.data.code === 0) {
      detail.value = res.data.data
    } else {
      toast.error(res.data.msg || t('platform.event.message.loadFailed'))
      detail.value = null
    }
  } catch (e) {
    toast.error(e.message)
    detail.value = null
  } finally {
    detailLoading.value = false
  }
}

async function handleRedrive(row) {
  const ok = await confirm({
    title: t('platform.event.confirm.redriveTitle'),
    message: t('platform.event.confirm.redriveMessage', { eventType: row.eventType }),
    confirmText: t('platform.event.confirm.redriveConfirm')
  })
  if (!ok) return
  try {
    const res = await redriveEventApi(row.id)
    if (res.data.code === 0) {
      toast.success(t('platform.event.message.redriveSuccess'))
      fetchData()
    } else {
      toast.error(res.data.msg || t('platform.event.message.redriveFailed'))
    }
  } catch (e) {
    toast.error(e.message)
  }
}

async function handleRedriveAll() {
  const ok = await confirm({
    title: t('platform.event.confirm.redriveAllTitle'),
    message: t('platform.event.confirm.redriveAllMessage'),
    confirmText: t('platform.event.confirm.redriveAllConfirm')
  })
  if (!ok) return
  try {
    const res = await redriveFailedEventsApi()
    if (res.data.code === 0) {
      toast.success(t('platform.event.message.redriveAllSuccess', { count: res.data.data ?? 0 }))
      fetchData()
    } else {
      toast.error(res.data.msg || t('platform.event.message.redriveFailed'))
    }
  } catch (e) {
    toast.error(e.message)
  }
}

onMounted(fetchData)
</script>

<template>
  <div class="space-y-3">
    <!-- Filters -->
    <Card class="p-4">
      <div class="flex flex-wrap items-end gap-3">
        <div>
          <label class="text-xs text-muted-foreground block mb-1">{{ t('platform.event.column.status') }}</label>
          <div class="inline-flex rounded-lg border border-border overflow-hidden">
            <button v-for="s in STATES" :key="String(s.value)"
                    class="h-9 px-3 text-sm transition-colors"
                    :class="search.dispatchState === s.value
                        ? 'bg-primary text-primary-foreground'
                        : 'bg-card hover:bg-muted text-foreground'"
                    @click="pickState(s.value)">
              {{ s.label() }}
            </button>
          </div>
        </div>
        <div>
          <label class="text-xs text-muted-foreground block mb-1">{{ t('platform.event.column.eventType') }}</label>
          <Input v-model="search.eventType" :placeholder="t('platform.event.search.eventTypePlaceholder')" class="w-48" />
        </div>
        <div>
          <label class="text-xs text-muted-foreground block mb-1">{{ t('common.label.keyword') }}</label>
          <Input v-model="search.keyword" :placeholder="t('platform.event.search.keywordPlaceholder')" class="w-56" />
        </div>
        <button class="h-9 px-3 rounded bg-primary text-primary-foreground text-sm inline-flex items-center gap-1"
                @click="applySearch">
          <Search class="size-4" /> {{ t('common.button.search') }}
        </button>
        <button class="h-9 px-3 rounded border border-border text-sm inline-flex items-center gap-1"
                @click="resetSearch">
          <RotateCcw class="size-4" /> {{ t('common.button.reset') }}
        </button>
        <div class="ml-auto">
          <button v-permission="'platform:event:redrive'"
                  class="h-9 px-3 rounded border border-destructive/40 text-destructive hover:bg-destructive/10 text-sm inline-flex items-center gap-1"
                  @click="handleRedriveAll">
            <RefreshCw class="size-4" /> {{ t('platform.event.button.redriveAll') }}
          </button>
        </div>
      </div>
    </Card>

    <!-- Table -->
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
        <template #cell-occurredAt="{ row }">
          <span class="text-sm whitespace-nowrap">{{ fmtDate(row.occurredAt) }}</span>
        </template>
        <template #cell-eventType="{ row }">
          <span class="font-mono text-sm">{{ row.eventType }}</span>
        </template>
        <template #cell-aggregate="{ row }">
          <span class="text-sm">{{ row.aggregateType }}</span>
          <span class="text-xs text-muted-foreground ml-1 font-mono">{{ row.aggregateId }}</span>
        </template>
        <template #cell-dispatchState="{ row }">
          <Badge variant="outline" :class="stateClass(row.dispatchState)">{{ stateLabel(row.dispatchState) }}</Badge>
        </template>
        <template #cell-dispatchAttempts="{ row }">
          <span class="text-sm tabular-nums">{{ row.dispatchAttempts }}</span>
        </template>
        <template #cell-actions="{ row }">
          <div class="inline-flex items-center gap-0.5">
            <button class="h-7 px-2 rounded hover:bg-muted text-muted-foreground hover:text-foreground text-xs inline-flex items-center gap-1"
                    :title="t('platform.event.tooltip.view')"
                    @click="openDetail(row)">
              <Eye class="size-3.5" />
            </button>
            <button v-permission="'platform:event:redrive'"
                    class="h-7 px-2 rounded hover:bg-amber-500/10 text-amber-600 text-xs inline-flex items-center gap-1 disabled:opacity-40 disabled:cursor-not-allowed"
                    :disabled="row.dispatchState !== 2"
                    :title="row.dispatchState === 2
                        ? t('platform.event.tooltip.redrive')
                        : t('platform.event.tooltip.redriveOnlyFailed')"
                    @click="handleRedrive(row)">
              <RefreshCw class="size-3.5" />
            </button>
          </div>
        </template>
      </DataTable>
    </Card>

    <!-- Detail drawer -->
    <Drawer :open="!!detail" :title="t('platform.event.drawer.title')" width="max-w-xl"
            @close="detail = null">
      <div v-if="detail" class="space-y-3 text-sm">
        <div class="grid grid-cols-3 gap-2">
          <div class="text-muted-foreground">{{ t('platform.event.column.eventType') }}</div>
          <div class="col-span-2 font-mono break-all">{{ detail.eventType }}</div>

          <div class="text-muted-foreground">{{ t('platform.event.column.aggregate') }}</div>
          <div class="col-span-2"><span>{{ detail.aggregateType }}</span>
            <span class="text-xs text-muted-foreground ml-1 font-mono">{{ detail.aggregateId }}</span></div>

          <div class="text-muted-foreground">{{ t('platform.event.column.status') }}</div>
          <div class="col-span-2">
            <Badge variant="outline" :class="stateClass(detail.dispatchState)">{{ stateLabel(detail.dispatchState) }}</Badge>
            <span class="text-xs text-muted-foreground ml-2">{{ t('platform.event.column.attempts') }}: {{ detail.dispatchAttempts }}</span>
          </div>

          <div class="text-muted-foreground">{{ t('platform.event.drawer.actor') }}</div>
          <div class="col-span-2">{{ detail.actor || '—' }}
            <span class="text-xs text-muted-foreground ml-1">({{ actorTypeLabel(detail.actorType) }})</span></div>

          <div class="text-muted-foreground">{{ t('platform.event.column.occurredAt') }}</div>
          <div class="col-span-2">{{ fmtDate(detail.occurredAt) }}</div>

          <div class="text-muted-foreground">{{ t('platform.event.drawer.dispatchedAt') }}</div>
          <div class="col-span-2">{{ fmtDate(detail.dispatchedAt) }}</div>

          <div class="text-muted-foreground">{{ t('platform.event.drawer.traceId') }}</div>
          <div class="col-span-2 font-mono break-all">{{ detail.traceId || '—' }}</div>

          <div class="text-muted-foreground">tenant</div>
          <div class="col-span-2 font-mono">{{ detail.tenantId }}</div>
        </div>

        <div>
          <div class="text-muted-foreground mb-1">{{ t('platform.event.drawer.payload') }}</div>
          <pre class="text-xs bg-muted/50 rounded-lg p-3 overflow-auto max-h-[50vh] whitespace-pre-wrap break-all">{{ detailLoading ? '…' : prettyPayload(detail.payload) }}</pre>
        </div>
      </div>
      <template #footer>
        <button v-if="detail && detail.dispatchState === 2"
                v-permission="'platform:event:redrive'"
                class="h-9 px-3 rounded bg-primary text-primary-foreground text-sm inline-flex items-center gap-1"
                @click="() => { const r = detail; detail = null; handleRedrive(r) }">
          <RefreshCw class="size-4" /> {{ t('platform.event.tooltip.redrive') }}
        </button>
        <button class="h-9 px-3 rounded border border-border text-sm" @click="detail = null">
          {{ t('common.button.close') }}
        </button>
      </template>
    </Drawer>
  </div>
</template>
