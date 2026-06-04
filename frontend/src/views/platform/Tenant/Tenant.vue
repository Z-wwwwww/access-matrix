<script setup>
import { onMounted, onBeforeUnmount, reactive, ref, computed } from 'vue'
import { useI18n } from 'vue-i18n'
import VChart from 'vue-echarts'
import { use } from 'echarts/core'
import { CanvasRenderer } from 'echarts/renderers'
import { PieChart, LineChart } from 'echarts/charts'
import { TooltipComponent, LegendComponent, GridComponent } from 'echarts/components'
import Card from '@/components/ui/Card.vue'
import Input from '@/components/ui/Input.vue'
import Badge from '@/components/ui/Badge.vue'
import { DataTable } from '@/components/shared/DataTable'
import { toast } from '@/composables/useToast'
import { useConfirm } from '@/composables/useConfirm'
import { useDict } from '@/composables/useDict'
import { useAuthStore } from '@/stores/auth'
import {
  Plus, Search, RotateCcw, Trash2, Pause, Play, Pencil, LifeBuoy, Send,
  Building2, TrendingUp, CheckCircle2, PauseCircle
} from 'lucide-vue-next'
import {
  listTenantsApi, getTenantStatsApi,
  suspendTenantApi, resumeTenantApi,
  startSupportSessionApi,
  resendInviteApi
} from '@/services/tenant'
import DashboardPanels from './DashboardPanels.vue'
import TenantCreate from './TenantCreate.vue'
import TenantEdit from './TenantEdit.vue'
import TenantSupportSession from './TenantSupportSession.vue'
import TenantHardDelete from './TenantHardDelete.vue'
import TenantResendInvite from './TenantResendInvite.vue'

// First echarts use in the codebase — register only the pieces these two charts
// need (tree-shaken canvas renderer + pie/line + tooltip/legend/grid).
use([CanvasRenderer, PieChart, LineChart, TooltipComponent, LegendComponent, GridComponent])

const { t } = useI18n()
const { confirm } = useConfirm()
const auth = useAuthStore()
const tenantStatus = useDict('tenant_status')

const loading = ref(false)
const list = ref([])
const total = ref(0)
const page = ref(1)
const pageSize = ref(20)
const search = reactive({ keyword: '' })

// ── Dashboard stats ──────────────────────────────────────────────────────
const stats = reactive({ total: 0, active: 0, suspended: 0, newThisMonth: 0, monthly: [] })
const statsLoaded = ref(false)

const showCreate = ref(false)
const editTarget = ref(null)            // null = closed; row object = open with that row
const supportTarget = ref(null)         // null = closed; row object = open support-session dialog
const hardDeleteTarget = ref(null)      // null = closed; row object = open hard-delete confirmation
const resendTarget = ref(null)          // null = closed; row object = open resend-invite dialog

const columns = [
  { key: 'tenantCode',    label: () => t('platform.tenant.column.tenantCode'),    width: '180px' },
  { key: 'displayName',   label: () => t('platform.tenant.column.displayName') },
  { key: 'contactEmail',  label: () => t('platform.tenant.column.contactEmail'), width: '240px' },
  { key: 'status',        label: () => t('platform.tenant.column.status'),        width: '100px' },
  { key: 'createTime',    label: () => t('platform.tenant.column.createTime'),    width: '180px' },
  { key: 'actions',       label: () => t('platform.tenant.column.actions'),       width: '200px' }
]

// ── Theme-aware chart colors ───────────────────────────────────────────────
// echarts needs concrete color values, not Tailwind classes. Read the palette's
// CSS custom properties at option-build time, and bump themeVersion when <html>'s
// class / data-palette changes so the charts recolor on theme switch.
const themeVersion = ref(0)
let themeObserver = null
function cssVar(name) {
  return getComputedStyle(document.documentElement).getPropertyValue(name).trim()
}

const statusDonutOption = computed(() => {
  themeVersion.value // reactive dep — recompute on theme change
  const textColor = cssVar('--muted-foreground')
  return {
    tooltip: { trigger: 'item' },
    legend: { bottom: 0, textStyle: { color: textColor } },
    series: [{
      type: 'pie',
      radius: ['55%', '78%'],
      avoidLabelOverlap: false,
      itemStyle: { borderColor: cssVar('--card'), borderWidth: 2 },
      label: { show: false },
      labelLine: { show: false },
      data: [
        { value: stats.active,    name: t('platform.tenant.status.active'),    itemStyle: { color: cssVar('--signal-green') } },
        { value: stats.suspended, name: t('platform.tenant.status.suspended'), itemStyle: { color: cssVar('--signal-yellow') } }
      ]
    }]
  }
})

const trendLineOption = computed(() => {
  themeVersion.value
  const textColor = cssVar('--muted-foreground')
  const gridColor = cssVar('--border')
  const accent = cssVar('--brand-orange')
  return {
    tooltip: { trigger: 'axis' },
    grid: { left: 8, right: 16, top: 16, bottom: 8, containLabel: true },
    xAxis: {
      type: 'category',
      boundaryGap: false,
      data: stats.monthly.map((m) => m.month.slice(2)),   // 'YY-MM'
      axisLabel: { color: textColor },
      axisLine: { lineStyle: { color: gridColor } }
    },
    yAxis: {
      type: 'value',
      minInterval: 1,
      axisLabel: { color: textColor },
      splitLine: { lineStyle: { color: gridColor } }
    },
    series: [{
      type: 'line',
      smooth: true,
      name: t('platform.tenant.dashboard.trendSeries'),
      data: stats.monthly.map((m) => m.count),
      itemStyle: { color: accent },
      lineStyle: { color: accent },
      areaStyle: { color: accent, opacity: 0.12 }
    }]
  }
})

async function fetchStats() {
  try {
    const res = await getTenantStatsApi()
    if (res.data.code === 0) {
      const d = res.data.data || {}
      stats.total = d.total || 0
      stats.active = d.active || 0
      stats.suspended = d.suspended || 0
      stats.newThisMonth = d.newThisMonth || 0
      stats.monthly = d.monthly || []
      statsLoaded.value = true
    }
  } catch (e) {
    // Stats are decorative on top of the list — a failure here shouldn't block
    // the management table. Surface quietly.
    toast.error(e.message)
  }
}

async function fetchData() {
  loading.value = true
  try {
    const res = await listTenantsApi({
      page: page.value, size: pageSize.value, keyword: search.keyword || undefined
    })
    if (res.data.code === 0) {
      list.value = res.data.data.records || []
      total.value = res.data.data.total || 0
    } else {
      toast.error(res.data.msg || t('platform.tenant.message.loadFailed'))
    }
  } catch (e) {
    toast.error(e.message)
  } finally {
    loading.value = false
  }
}

// Mutations change both the table and the KPI/chart aggregates — refresh both.
function refreshAll() {
  fetchData()
  fetchStats()
}

function resetSearch() {
  search.keyword = ''
  page.value = 1
  fetchData()
}

function openCreate() {
  showCreate.value = true
}

function openEdit(row) {
  editTarget.value = row
}

function isBuiltIn(row) {
  return row.tenantCode === 'system' || row.tenantCode === 'demo'
}

function openHardDelete(row) {
  // Hard delete uses a typed-confirmation modal — too dangerous for the
  // generic useConfirm dialog. The modal also enforces "row must be
  // suspended" matching the backend gate; we don't surface the button
  // for active rows in the first place.
  hardDeleteTarget.value = row
}

async function handleSuspend(row) {
  const ok = await confirm({
    title: t('platform.tenant.confirm.suspendTitle'),
    message: t('platform.tenant.confirm.suspendMessage', {
      tenantCode: row.tenantCode,
      displayName: row.displayName
    }),
    confirmText: t('platform.tenant.confirm.suspendConfirm')
  })
  if (!ok) return
  try {
    const res = await suspendTenantApi(row.id)
    if (res.data.code === 0) {
      toast.success(t('platform.tenant.message.suspendSuccess'))
      refreshAll()
    } else {
      toast.error(res.data.msg || t('platform.tenant.message.suspendFailed'))
    }
  } catch (e) {
    toast.error(e.message)
  }
}

async function handleResume(row) {
  // Resume is non-destructive — confirm-less is fine.
  try {
    const res = await resumeTenantApi(row.id)
    if (res.data.code === 0) {
      toast.success(t('platform.tenant.message.resumeSuccess'))
      refreshAll()
    } else {
      toast.error(res.data.msg || t('platform.tenant.message.resumeFailed'))
    }
  } catch (e) {
    toast.error(e.message)
  }
}

function openSupportSession(row) {
  supportTarget.value = row
}

async function handleSupportSession({ row, reason }) {
  try {
    const res = await startSupportSessionApi(row.id, reason)
    if (res.data.code === 0) {
      const data = res.data.data
      auth.enterSupportSession(data.token, {
        sessionId:   data.sessionId,
        tenantCode:  data.tenantCode,
        displayName: data.displayName,
        expiresAt:   data.expiresAt
      })
      supportTarget.value = null
      toast.success(t('platform.tenant.support.message.started', {
        tenantCode: data.tenantCode
      }))
      // Hard navigation (NOT router.push) so menu / sidebar / /me all re-fetch
      // under the new support identity on a clean page-load. Dynamic routes are
      // registered once per load from the menu and aren't rebuilt on identity
      // change, so a client-side push would keep the stale ops routes. Landing
      // on '/' lets the fresh load redirect to the support identity's home.
      window.location.assign('/')
    } else {
      toast.error(res.data.msg || t('platform.tenant.support.message.startFailed'))
    }
  } catch (e) {
    toast.error(e.message || t('platform.tenant.support.message.startFailed'))
  }
}

function openResendInvite(row) {
  resendTarget.value = row
}

async function handleResendInvite({ row, email }) {
  try {
    const res = await resendInviteApi(row.id, email)
    if (res.data.code === 0) {
      resendTarget.value = null
      toast.success(t('platform.tenant.resendInvite.message.success'))
      if (email) fetchData()   // contact email may have been corrected — refresh the row
    } else {
      toast.error(res.data.msg || t('platform.tenant.resendInvite.message.failed'))
    }
  } catch (e) {
    toast.error(e.message || t('platform.tenant.resendInvite.message.failed'))
  }
}

onMounted(() => {
  fetchData()
  fetchStats()
  // Recolor charts when the theme (dark class) or palette attribute flips.
  themeObserver = new MutationObserver(() => { themeVersion.value++ })
  themeObserver.observe(document.documentElement, {
    attributes: true,
    attributeFilter: ['class', 'data-palette']
  })
})

onBeforeUnmount(() => {
  themeObserver?.disconnect()
})
</script>

<template>
  <div class="space-y-3">
    <!-- ── Dashboard: KPI cards ─────────────────────────────────────── -->
    <div class="grid grid-cols-2 lg:grid-cols-4 gap-3">
      <Card class="p-4 flex items-center gap-3">
        <div class="size-10 rounded-lg bg-primary/10 text-primary inline-flex items-center justify-center shrink-0">
          <Building2 class="size-5" />
        </div>
        <div class="min-w-0">
          <div class="text-2xl font-semibold leading-tight">{{ stats.total }}</div>
          <div class="text-xs text-muted-foreground truncate">{{ t('platform.tenant.dashboard.total') }}</div>
        </div>
      </Card>
      <Card class="p-4 flex items-center gap-3">
        <div class="size-10 rounded-lg inline-flex items-center justify-center shrink-0"
             style="background-color: color-mix(in srgb, var(--signal-green) 12%, transparent); color: var(--signal-green)">
          <CheckCircle2 class="size-5" />
        </div>
        <div class="min-w-0">
          <div class="text-2xl font-semibold leading-tight">{{ stats.active }}</div>
          <div class="text-xs text-muted-foreground truncate">{{ t('platform.tenant.status.active') }}</div>
        </div>
      </Card>
      <Card class="p-4 flex items-center gap-3">
        <div class="size-10 rounded-lg inline-flex items-center justify-center shrink-0"
             style="background-color: color-mix(in srgb, var(--signal-yellow) 14%, transparent); color: var(--signal-yellow)">
          <PauseCircle class="size-5" />
        </div>
        <div class="min-w-0">
          <div class="text-2xl font-semibold leading-tight">{{ stats.suspended }}</div>
          <div class="text-xs text-muted-foreground truncate">{{ t('platform.tenant.status.suspended') }}</div>
        </div>
      </Card>
      <Card class="p-4 flex items-center gap-3">
        <div class="size-10 rounded-lg bg-brand-orange/10 text-brand-orange inline-flex items-center justify-center shrink-0">
          <TrendingUp class="size-5" />
        </div>
        <div class="min-w-0">
          <div class="text-2xl font-semibold leading-tight">{{ stats.newThisMonth }}</div>
          <div class="text-xs text-muted-foreground truncate">{{ t('platform.tenant.dashboard.newThisMonth') }}</div>
        </div>
      </Card>
    </div>

    <!-- ── Dashboard: charts ────────────────────────────────────────── -->
    <div class="grid grid-cols-1 lg:grid-cols-2 gap-3">
      <Card class="p-4">
        <div class="text-sm font-medium mb-2">{{ t('platform.tenant.dashboard.statusTitle') }}</div>
        <VChart v-if="statsLoaded" :option="statusDonutOption" autoresize style="height: 260px" />
        <div v-else class="h-[260px] animate-pulse bg-muted/40 rounded" />
      </Card>
      <Card class="p-4">
        <div class="text-sm font-medium mb-2">{{ t('platform.tenant.dashboard.trendTitle') }}</div>
        <VChart v-if="statsLoaded" :option="trendLineOption" autoresize style="height: 260px" />
        <div v-else class="h-[260px] animate-pulse bg-muted/40 rounded" />
      </Card>
    </div>

    <!-- ── Ops monitoring panels: activation / engagement / reliability / security ── -->
    <DashboardPanels />

    <!-- Search + create -->
    <Card class="p-4">
      <div class="flex flex-wrap items-end gap-3">
        <div>
          <label class="text-xs text-muted-foreground block mb-1">{{ t('common.label.keyword') }}</label>
          <Input v-model="search.keyword" :placeholder="t('platform.tenant.search.placeholder')" class="w-60" />
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
          <button v-permission="'platform:tenant:create'"
                  class="h-9 px-3 rounded bg-primary text-primary-foreground text-sm inline-flex items-center gap-1"
                  @click="openCreate">
            <Plus class="size-4" /> {{ t('platform.tenant.button.new') }}
          </button>
        </div>
      </div>
    </Card>

    <!-- Recycle-bin hint: explain the two-step delete model BEFORE
         someone hunts for a missing trash icon on an active row. -->
    <Card class="p-3 bg-amber-500/5 border-amber-500/30">
      <p class="text-xs text-muted-foreground leading-relaxed">
        <span class="font-medium text-foreground">{{ t('platform.tenant.recycleBinHint.title') }}</span>
        {{ t('platform.tenant.recycleBinHint.body') }}
      </p>
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
        <template #cell-tenantCode="{ row }">
          <span class="font-mono text-sm">{{ row.tenantCode }}</span>
          <Badge v-if="isBuiltIn(row)" variant="outline" class="ml-2 text-[10px]">
            {{ t('common.status.builtIn') }}
          </Badge>
        </template>
        <template #cell-contactEmail="{ row }">
          <span class="text-sm">{{ row.contactEmail || '—' }}</span>
        </template>
        <template #cell-status="{ row }">
          <Badge :variant="tenantStatus.cssClass(row.status) || 'outline'">
            {{ tenantStatus.label(row.status) }}
          </Badge>
        </template>
        <template #cell-actions="{ row }">
          <div class="inline-flex items-center gap-0.5">
            <!-- Edit (displayName + contactEmail) -->
            <button v-permission="'platform:tenant:update'"
                    class="h-7 px-2 rounded hover:bg-muted text-muted-foreground hover:text-foreground text-xs inline-flex items-center gap-1 disabled:opacity-40 disabled:cursor-not-allowed"
                    :disabled="isBuiltIn(row)"
                    :title="isBuiltIn(row)
                        ? t('platform.tenant.tooltip.builtInLocked')
                        : t('platform.tenant.tooltip.edit')"
                    @click="openEdit(row)">
              <Pencil class="size-3.5" />
            </button>

            <!-- Support session — only for active, non-built-in tenants -->
            <button v-permission="'platform:tenant:impersonate'"
                    class="h-7 px-2 rounded hover:bg-amber-500/10 text-amber-600 text-xs inline-flex items-center gap-1 disabled:opacity-40 disabled:cursor-not-allowed"
                    :disabled="isBuiltIn(row) || row.status !== 1"
                    :title="isBuiltIn(row)
                        ? t('platform.tenant.tooltip.builtInLocked')
                        : (row.status !== 1
                            ? t('platform.tenant.support.tooltip.disabledSuspended')
                            : t('platform.tenant.support.tooltip.start'))"
                    @click="openSupportSession(row)">
              <LifeBuoy class="size-3.5" />
            </button>

            <!-- Resend admin invite — missed email or wrong address. Disabled
                 for built-in tenants (their admins are seeded, no invite). -->
            <button v-permission="'platform:tenant:update'"
                    class="h-7 px-2 rounded hover:bg-primary/10 text-primary text-xs inline-flex items-center gap-1 disabled:opacity-40 disabled:cursor-not-allowed"
                    :disabled="isBuiltIn(row)"
                    :title="isBuiltIn(row)
                        ? t('platform.tenant.tooltip.builtInLocked')
                        : t('platform.tenant.resendInvite.tooltip.resend')"
                    @click="openResendInvite(row)">
              <Send class="size-3.5" />
            </button>

            <!-- Suspend / Resume toggle — same column slot, behavior swaps on row.status -->
            <button v-if="row.status === 1"
                    v-permission="'platform:tenant:update'"
                    class="h-7 px-2 rounded hover:bg-muted text-muted-foreground hover:text-foreground text-xs inline-flex items-center gap-1 disabled:opacity-40 disabled:cursor-not-allowed"
                    :disabled="isBuiltIn(row)"
                    :title="isBuiltIn(row)
                        ? t('platform.tenant.tooltip.builtInLocked')
                        : t('platform.tenant.tooltip.suspend')"
                    @click="handleSuspend(row)">
              <Pause class="size-3.5" />
            </button>
            <button v-else
                    v-permission="'platform:tenant:update'"
                    class="h-7 px-2 rounded hover:bg-emerald-500/10 text-emerald-600 text-xs inline-flex items-center gap-1 disabled:opacity-40 disabled:cursor-not-allowed"
                    :disabled="isBuiltIn(row)"
                    :title="isBuiltIn(row)
                        ? t('platform.tenant.tooltip.builtInLocked')
                        : t('platform.tenant.tooltip.resume')"
                    @click="handleResume(row)">
              <Play class="size-3.5" />
            </button>

            <!-- Hard delete — recycle-bin model: only suspended rows
                 expose this button. Active rows must Suspend first.
                 Modal then requires typing the tenantCode exactly. -->
            <button v-if="row.status !== 1"
                    v-permission="'platform:tenant:delete'"
                    class="h-7 px-2 rounded hover:bg-destructive/10 text-destructive text-xs inline-flex items-center gap-1 disabled:opacity-40 disabled:cursor-not-allowed"
                    :disabled="isBuiltIn(row)"
                    :title="isBuiltIn(row)
                        ? t('platform.tenant.tooltip.builtInLocked')
                        : t('platform.tenant.hardDelete.tooltip.confirm')"
                    @click="openHardDelete(row)">
              <Trash2 class="size-3.5" />
            </button>
          </div>
        </template>
      </DataTable>
    </Card>

    <TenantCreate v-model:open="showCreate" @saved="refreshAll" />
    <TenantEdit :row="editTarget" @close="editTarget = null" @saved="() => { editTarget = null; refreshAll() }" />
    <TenantSupportSession :row="supportTarget"
                          @close="supportTarget = null"
                          @start="handleSupportSession" />
    <TenantHardDelete :row="hardDeleteTarget"
                      @close="hardDeleteTarget = null"
                      @deleted="() => { hardDeleteTarget = null; refreshAll() }" />
    <TenantResendInvite :row="resendTarget"
                        @close="resendTarget = null"
                        @resend="handleResendInvite" />
  </div>
</template>
