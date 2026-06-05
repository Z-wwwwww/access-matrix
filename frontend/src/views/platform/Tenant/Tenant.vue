<script setup>
import { reactive, ref, computed, onMounted, onBeforeUnmount, watch } from 'vue'
import { useI18n } from 'vue-i18n'
import { toast } from '@/composables/useToast'
import { useConfirm } from '@/composables/useConfirm'
import { useDict } from '@/composables/useDict'
import { useAuthStore } from '@/stores/auth'
import { toJSTDateTimeDisp } from '@/lib/date'
import {
  Plus, Search, RotateCcw, LayoutGrid, List, Info, X, Building2,
  ChevronLeft, ChevronRight, ChevronsLeft, ChevronsRight,
  Pencil, LifeBuoy, Send, Pause, Play, Trash2
} from 'lucide-vue-next'
import {
  listTenantsApi, getTenantStatsApi,
  suspendTenantApi, resumeTenantApi,
  startSupportSessionApi, resendInviteApi
} from '@/services/tenant'
import TenantRowActions from './TenantRowActions.vue'
import TenantCreate from './TenantCreate.vue'
import TenantEdit from './TenantEdit.vue'
import TenantSupportSession from './TenantSupportSession.vue'
import TenantHardDelete from './TenantHardDelete.vue'
import TenantResendInvite from './TenantResendInvite.vue'

const { t, locale } = useI18n()
const { confirm } = useConfirm()
const auth = useAuthStore()
const tenantStatus = useDict('tenant_status')

// ── List state ─────────────────────────────────────────────────────────────
const loading = ref(false)
const list = ref([])
const total = ref(0)
const page = ref(1)
const pageSize = ref(10)
const search = reactive({ keyword: '' })
const statusFilter = ref('all')          // 'all' | 1 (active) | 0 (suspended)

// Table / cards view, remembered across visits. Cards is the default; only an
// explicit prior 'table' choice overrides it.
const view = ref(localStorage.getItem('tenant.view') === 'table' ? 'table' : 'cards')
watch(view, (v) => localStorage.setItem('tenant.view', v))

// Segment counts come from the aggregate stats endpoint so they reflect ALL
// tenants, not just the current page.
const counts = reactive({ all: 0, active: 0, suspended: 0 })

// ── Dialog / drawer targets ──────────────────────────────────────────────
const showCreate = ref(false)
const editTarget = ref(null)
const supportTarget = ref(null)
const hardDeleteTarget = ref(null)
const resendTarget = ref(null)
const drawerTenant = ref(null)

// ── Derived ────────────────────────────────────────────────────────────────
const totalPages = computed(() => Math.max(1, Math.ceil(total.value / pageSize.value)))
// Windowed page numbers (≤5) centred on the current page — keeps the pager
// compact even with many pages.
const pageWindow = computed(() => {
  const tp = totalPages.value
  const span = 5
  let start = Math.max(1, page.value - Math.floor(span / 2))
  let end = Math.min(tp, start + span - 1)
  start = Math.max(1, end - span + 1)
  return Array.from({ length: end - start + 1 }, (_, i) => start + i)
})

const segs = computed(() => [
  ['all', t('platform.tenant.list.segAll'), counts.all],
  [1, t('platform.tenant.status.active'), counts.active],
  [0, t('platform.tenant.status.suspended'), counts.suspended]
])

function isBuiltIn(row) {
  return row.tenantCode === 'system' || row.tenantCode === 'demo'
}

// Deterministic warm-tinted monogram derived from the code; built on the theme's
// --card / --foreground so it adapts to palette + dark mode.
function monoStyle(code) {
  let h = 0
  for (let i = 0; i < code.length; i++) h = (h * 31 + code.charCodeAt(i)) >>> 0
  const hue = h % 360
  return {
    background: `color-mix(in oklab, hsl(${hue} 60% 50%) 16%, var(--card))`,
    color: `color-mix(in oklab, hsl(${hue} 65% 45%) 90%, var(--foreground))`,
    borderColor: `color-mix(in oklab, hsl(${hue} 60% 50%) 24%, transparent)`
  }
}
function monoChar(code) { return (code || '?').charAt(0).toUpperCase() }

function statusDot(status) {
  return status === 1 ? 'var(--signal-green)' : 'var(--signal-yellow)'
}

// Locale-aware relative time ("2 days ago" / "2天前") with no extra deps.
const LOCALE_MAP = { zh_CN: 'zh-CN', zh_TW: 'zh-TW', ja_JP: 'ja', ko_KR: 'ko', en: 'en' }
function relTime(iso) {
  if (!iso) return ''
  const diffSec = Math.round((Date.now() - new Date(iso).getTime()) / 1000)
  const rtf = new Intl.RelativeTimeFormat(LOCALE_MAP[locale.value] || 'en', { numeric: 'auto' })
  const min = diffSec / 60, hr = min / 60, day = hr / 24, mo = day / 30
  if (Math.abs(diffSec) < 60) return rtf.format(-diffSec, 'second')
  if (Math.abs(min) < 60) return rtf.format(-Math.round(min), 'minute')
  if (Math.abs(hr) < 24) return rtf.format(-Math.round(hr), 'hour')
  if (Math.abs(day) < 30) return rtf.format(-Math.round(day), 'day')
  if (Math.abs(mo) < 12) return rtf.format(-Math.round(mo), 'month')
  return rtf.format(-Math.round(mo / 12), 'year')
}
const fmtAbs = (iso) => (iso ? toJSTDateTimeDisp(iso) : '')

// ── Data fetching ──────────────────────────────────────────────────────────
async function fetchData() {
  loading.value = true
  try {
    const res = await listTenantsApi({
      page: page.value,
      size: pageSize.value,
      keyword: search.keyword || undefined,
      status: statusFilter.value === 'all' ? undefined : statusFilter.value
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

async function fetchCounts() {
  try {
    const res = await getTenantStatsApi()
    if (res.data.code === 0) {
      const d = res.data.data || {}
      counts.all = d.total || 0
      counts.active = d.active || 0
      counts.suspended = d.suspended || 0
    }
  } catch {
    // Segment counts are decorative — a failure here must not block the list.
  }
}

function refreshAll() {
  fetchData()
  fetchCounts()
}

// Live search (debounced) + filter changes reset to page 1.
let searchTimer = null
watch(() => search.keyword, () => {
  clearTimeout(searchTimer)
  searchTimer = setTimeout(() => { page.value = 1; fetchData() }, 250)
})
watch([statusFilter, pageSize], () => { page.value = 1; fetchData() })

function goPage(p) {
  if (p < 1 || p > totalPages.value || p === page.value) return
  page.value = p
  fetchData()
}

function resetSearch() {
  search.keyword = ''
  statusFilter.value = 'all'
  page.value = 1
  fetchData()
}

// ── Action handlers (unchanged behaviour) ─────────────────────────────────
function openCreate() { showCreate.value = true }
function openEdit(row) { editTarget.value = row }
function openHardDelete(row) { hardDeleteTarget.value = row }
function openSupportSession(row) { supportTarget.value = row }
function openResendInvite(row) { resendTarget.value = row }

async function handleSuspend(row) {
  const ok = await confirm({
    title: t('platform.tenant.confirm.suspendTitle'),
    message: t('platform.tenant.confirm.suspendMessage', {
      tenantCode: row.tenantCode, displayName: row.displayName
    }),
    confirmText: t('platform.tenant.confirm.suspendConfirm')
  })
  if (!ok) return
  try {
    const res = await suspendTenantApi(row.id)
    if (res.data.code === 0) {
      toast.success(t('platform.tenant.message.suspendSuccess'))
      if (drawerTenant.value?.id === row.id) drawerTenant.value = { ...row, status: 0 }
      refreshAll()
    } else {
      toast.error(res.data.msg || t('platform.tenant.message.suspendFailed'))
    }
  } catch (e) {
    toast.error(e.message)
  }
}

async function handleResume(row) {
  try {
    const res = await resumeTenantApi(row.id)
    if (res.data.code === 0) {
      toast.success(t('platform.tenant.message.resumeSuccess'))
      if (drawerTenant.value?.id === row.id) drawerTenant.value = { ...row, status: 1 }
      refreshAll()
    } else {
      toast.error(res.data.msg || t('platform.tenant.message.resumeFailed'))
    }
  } catch (e) {
    toast.error(e.message)
  }
}

async function handleSupportSession({ row, reason }) {
  try {
    const res = await startSupportSessionApi(row.id, reason)
    if (res.data.code === 0) {
      const data = res.data.data
      auth.enterSupportSession(data.token, {
        sessionId: data.sessionId, tenantCode: data.tenantCode,
        displayName: data.displayName, expiresAt: data.expiresAt
      })
      supportTarget.value = null
      toast.success(t('platform.tenant.support.message.started', { tenantCode: data.tenantCode }))
      // Hard navigation so menu / sidebar / /me re-fetch under the new identity.
      window.location.assign('/')
    } else {
      toast.error(res.data.msg || t('platform.tenant.support.message.startFailed'))
    }
  } catch (e) {
    toast.error(e.message || t('platform.tenant.support.message.startFailed'))
  }
}

async function handleResendInvite({ row, email }) {
  try {
    const res = await resendInviteApi(row.id, email)
    if (res.data.code === 0) {
      resendTarget.value = null
      toast.success(t('platform.tenant.resendInvite.message.success'))
      if (email) fetchData()
    } else {
      toast.error(res.data.msg || t('platform.tenant.resendInvite.message.failed'))
    }
  } catch (e) {
    toast.error(e.message || t('platform.tenant.resendInvite.message.failed'))
  }
}

// Esc closes the drawer.
function onKey(e) { if (e.key === 'Escape') drawerTenant.value = null }
onMounted(() => {
  fetchData()
  fetchCounts()
  window.addEventListener('keydown', onKey)
})
onBeforeUnmount(() => window.removeEventListener('keydown', onKey))
</script>

<template>
  <div class="space-y-4 pt-4">
    <!-- ── Toolbar ───────────────────────────────────────────────────── -->
    <div class="flex flex-wrap items-center gap-3">
      <div class="relative flex-1 min-w-[220px] max-w-[420px]">
        <Search class="size-4 text-muted-foreground absolute left-3.5 top-1/2 -translate-y-1/2" />
        <input v-model="search.keyword"
               :placeholder="t('platform.tenant.search.placeholder')"
               class="w-full h-10 rounded-lg border border-border bg-card pl-10 pr-3.5 text-sm text-foreground outline-none transition focus:border-primary focus:ring-2 focus:ring-primary/20 placeholder:text-muted-foreground/70" />
      </div>

      <!-- Status segmented control -->
      <div class="inline-flex items-center gap-0.5 rounded-lg border border-border bg-muted/50 p-0.5">
        <button v-for="[k, label, n] in segs" :key="String(k)" type="button"
                class="h-8 px-3 rounded-md text-[13px] font-semibold inline-flex items-center gap-1.5 transition-colors"
                :class="statusFilter === k
                    ? 'bg-card text-foreground shadow-sm'
                    : 'text-muted-foreground hover:text-foreground'"
                @click="statusFilter = k">
          {{ label }}
          <span class="font-mono text-[11px]"
                :class="statusFilter === k ? 'text-primary' : 'text-muted-foreground/70'">{{ n }}</span>
        </button>
      </div>

      <button type="button"
              class="h-9 px-3 rounded-lg border border-border bg-card text-sm font-medium text-foreground inline-flex items-center gap-1.5 hover:bg-muted transition"
              @click="resetSearch">
        <RotateCcw class="size-3.5" /> {{ t('common.button.reset') }}
      </button>

      <button v-permission="'platform:tenant:create'" type="button"
              class="h-9 px-3 rounded-lg bg-primary text-primary-foreground text-sm font-semibold inline-flex items-center gap-1.5 hover:opacity-90 transition"
              @click="openCreate">
        <Plus class="size-4" /> {{ t('platform.tenant.button.new') }}
      </button>

      <div class="flex-1"></div>

      <!-- View toggle -->
      <div class="inline-flex items-center gap-0.5 rounded-lg border border-border bg-muted/50 p-0.5">
        <button type="button" :title="t('platform.tenant.list.viewTable')"
                class="size-8 rounded-md inline-grid place-items-center transition-colors"
                :class="view === 'table' ? 'bg-card text-primary shadow-sm' : 'text-muted-foreground hover:text-foreground'"
                @click="view = 'table'">
          <List class="size-4" />
        </button>
        <button type="button" :title="t('platform.tenant.list.viewCards')"
                class="size-8 rounded-md inline-grid place-items-center transition-colors"
                :class="view === 'cards' ? 'bg-card text-primary shadow-sm' : 'text-muted-foreground hover:text-foreground'"
                @click="view = 'cards'">
          <LayoutGrid class="size-4" />
        </button>
      </div>
    </div>

    <!-- ── Recycle-bin hint (static small note) ──────────────────────── -->
    <div class="flex gap-2 items-start text-xs text-muted-foreground leading-relaxed px-1">
      <Info class="size-3.5 text-amber-600 dark:text-amber-400 shrink-0 mt-0.5" />
      <p>
        <span class="font-medium text-foreground">{{ t('platform.tenant.recycleBinHint.title') }}</span>
        {{ t('platform.tenant.recycleBinHint.body') }}
      </p>
    </div>

    <!-- ── Panel ─────────────────────────────────────────────────────── -->
    <div class="rounded-2xl border border-border bg-card shadow-sm overflow-hidden">
      <!-- Empty state -->
      <div v-if="!loading && list.length === 0" class="px-5 py-16 text-center">
        <div class="text-lg font-medium text-foreground">
          {{ search.keyword ? t('platform.tenant.list.noMatch', { q: search.keyword }) : t('platform.tenant.ops.empty') }}
        </div>
        <button type="button" class="mt-4 h-9 px-3 rounded-lg border border-border bg-card text-sm inline-flex items-center gap-1.5 hover:bg-muted transition"
                @click="resetSearch">
          <RotateCcw class="size-3.5" /> {{ t('platform.tenant.list.clearFilters') }}
        </button>
      </div>

      <!-- Cards view -->
      <div v-else-if="view === 'cards'" class="grid gap-4 p-4" style="grid-template-columns: repeat(auto-fill, minmax(290px, 1fr))">
        <div v-for="row in list" :key="row.id"
             class="group relative rounded-xl border bg-card p-4 cursor-pointer transition hover:shadow-md hover:-translate-y-0.5"
             :class="row.status === 1 ? 'border-border' : 'border-amber-500/40 bg-amber-500/[0.04]'"
             @click="drawerTenant = row">
          <div class="flex items-center justify-between gap-3">
            <div class="size-11 rounded-xl grid place-items-center font-semibold text-lg border" :style="monoStyle(row.tenantCode)">
              {{ monoChar(row.tenantCode) }}
            </div>
            <span class="inline-flex items-center gap-2 text-[13px] font-semibold text-foreground">
              <span class="size-2 rounded-full" :style="{ background: statusDot(row.status) }"></span>
              {{ tenantStatus.label(row.status) }}
            </span>
          </div>
          <div class="mt-4">
            <div class="text-lg font-semibold leading-tight flex items-center gap-2 flex-wrap text-foreground">
              {{ row.displayName }}
              <span v-if="isBuiltIn(row)" class="text-[10px] font-bold uppercase tracking-wide text-muted-foreground border border-border bg-muted rounded-full px-2 py-0.5">
                {{ t('platform.tenant.list.builtIn') }}
              </span>
            </div>
            <div class="font-mono text-xs text-muted-foreground mt-1">{{ row.tenantCode }}</div>
          </div>
          <div class="grid grid-cols-2 gap-x-3 gap-y-3 mt-4 pt-3.5 border-t border-border">
            <div>
              <div class="text-[11px] uppercase tracking-wide font-bold text-muted-foreground">{{ t('platform.tenant.column.contactEmail') }}</div>
              <div class="text-[13px] font-mono mt-0.5 truncate" :class="row.contactEmail ? 'text-foreground' : 'text-muted-foreground/60'">
                {{ row.contactEmail || '—' }}
              </div>
            </div>
            <div>
              <div class="text-[11px] uppercase tracking-wide font-bold text-muted-foreground">{{ t('platform.tenant.column.createTime') }}</div>
              <div class="text-[13px] font-mono mt-0.5 text-foreground" :title="fmtAbs(row.createTime)">{{ relTime(row.createTime) }}</div>
            </div>
          </div>
          <div class="flex items-center gap-0.5 mt-3.5 pt-3 border-t border-border">
            <TenantRowActions :row="row" @edit="openEdit" @support="openSupportSession"
                              @resend="openResendInvite" @suspend="handleSuspend"
                              @resume="handleResume" @delete="openHardDelete" />
          </div>
        </div>
      </div>

      <!-- Table view -->
      <table v-else class="w-full border-collapse">
        <thead>
          <tr class="bg-muted/40 border-b border-border">
            <th class="text-left text-[11px] font-bold uppercase tracking-wider text-muted-foreground px-4 py-3.5 w-[32%] min-w-[260px]">{{ t('platform.tenant.column.displayName') }}</th>
            <th class="text-left text-[11px] font-bold uppercase tracking-wider text-muted-foreground px-4 py-3.5">{{ t('platform.tenant.column.contactEmail') }}</th>
            <th class="text-left text-[11px] font-bold uppercase tracking-wider text-muted-foreground px-4 py-3.5">{{ t('platform.tenant.column.status') }}</th>
            <th class="text-left text-[11px] font-bold uppercase tracking-wider text-muted-foreground px-4 py-3.5">{{ t('platform.tenant.column.createTime') }}</th>
            <th class="text-right text-[11px] font-bold uppercase tracking-wider text-muted-foreground px-4 py-3.5 w-px whitespace-nowrap">{{ t('platform.tenant.column.actions') }}</th>
          </tr>
        </thead>
        <tbody>
          <tr v-for="row in list" :key="row.id"
              class="group border-b border-border last:border-0 cursor-pointer transition-colors"
              :class="row.status === 1 ? 'hover:bg-muted/50' : 'tenant-suspended hover:bg-amber-500/10'"
              @click="drawerTenant = row">
            <!-- Tenant -->
            <td class="px-4 py-3.5 align-middle">
              <div class="flex items-center gap-3.5">
                <div class="size-10 rounded-xl grid place-items-center font-semibold text-[17px] border shrink-0" :style="monoStyle(row.tenantCode)">
                  {{ monoChar(row.tenantCode) }}
                </div>
                <div class="min-w-0">
                  <div class="font-semibold text-[15px] leading-tight text-foreground flex items-center gap-2 whitespace-nowrap">
                    {{ row.displayName }}
                    <span v-if="isBuiltIn(row)" class="text-[10px] font-bold uppercase tracking-wide text-muted-foreground border border-border bg-muted rounded-full px-2 py-0.5">
                      {{ t('platform.tenant.list.builtIn') }}
                    </span>
                  </div>
                  <div class="font-mono text-xs text-muted-foreground mt-0.5">{{ row.tenantCode }}</div>
                </div>
              </div>
            </td>
            <!-- Administrator -->
            <td class="px-4 py-3.5 align-middle">
              <span v-if="row.contactEmail" class="font-mono text-[12.5px] text-muted-foreground">{{ row.contactEmail }}</span>
              <span v-else class="font-mono text-[12.5px] text-muted-foreground/50">{{ t('platform.tenant.list.noAdmin') }}</span>
            </td>
            <!-- Status -->
            <td class="px-4 py-3.5 align-middle">
              <span class="inline-flex items-center gap-2 text-[13px] font-semibold text-foreground">
                <span class="size-2 rounded-full" :style="{ background: statusDot(row.status) }"></span>
                {{ tenantStatus.label(row.status) }}
              </span>
            </td>
            <!-- Created -->
            <td class="px-4 py-3.5 align-middle">
              <span class="font-mono text-[12.5px] text-muted-foreground whitespace-nowrap" :title="fmtAbs(row.createTime)">{{ relTime(row.createTime) }}</span>
            </td>
            <!-- Actions (hover-reveal) -->
            <td class="px-4 py-3.5 align-middle text-right">
              <div class="opacity-0 group-hover:opacity-100 focus-within:opacity-100 transition-opacity">
                <TenantRowActions :row="row" @edit="openEdit" @support="openSupportSession"
                                  @resend="openResendInvite" @suspend="handleSuspend"
                                  @resume="handleResume" @delete="openHardDelete" />
              </div>
            </td>
          </tr>
        </tbody>
      </table>

      <!-- Footer / pagination -->
      <div v-if="list.length" class="flex items-center justify-between gap-4 flex-wrap px-5 py-3.5 border-t border-border bg-muted/30">
        <div class="text-[13px] text-muted-foreground">
          {{ t('platform.tenant.list.showing', { shown: list.length, total }) }}
        </div>
        <div class="inline-flex items-center gap-1.5">
          <select v-model.number="pageSize"
                  class="h-9 mr-1.5 rounded-lg border border-border bg-card px-3 text-[13px] text-foreground cursor-pointer outline-none">
            <option :value="10">{{ t('platform.tenant.list.perPage', { n: 10 }) }}</option>
            <option :value="20">{{ t('platform.tenant.list.perPage', { n: 20 }) }}</option>
            <option :value="50">{{ t('platform.tenant.list.perPage', { n: 50 }) }}</option>
          </select>
          <button class="pgbtn" :disabled="page === 1" @click="goPage(1)"><ChevronsLeft class="size-4" /></button>
          <button class="pgbtn" :disabled="page === 1" @click="goPage(page - 1)"><ChevronLeft class="size-4" /></button>
          <button v-for="p in pageWindow" :key="p" class="pgbtn" :class="{ 'pgbtn-active': p === page }" @click="goPage(p)">{{ p }}</button>
          <button class="pgbtn" :disabled="page === totalPages" @click="goPage(page + 1)"><ChevronRight class="size-4" /></button>
          <button class="pgbtn" :disabled="page === totalPages" @click="goPage(totalPages)"><ChevronsRight class="size-4" /></button>
        </div>
      </div>
    </div>

    <!-- ── Detail drawer ─────────────────────────────────────────────── -->
    <Transition name="drawer-overlay">
      <div v-if="drawerTenant" class="fixed inset-0 z-[60] bg-black/30 backdrop-blur-[2px]" @click="drawerTenant = null"></div>
    </Transition>
    <Transition name="drawer-panel">
      <aside v-if="drawerTenant"
             class="fixed top-0 right-0 bottom-0 z-[61] w-[min(460px,94vw)] bg-card border-l border-border shadow-2xl flex flex-col overflow-y-auto">
        <div class="relative p-6 border-b border-border">
          <button type="button" class="absolute top-4 right-4 size-9 rounded-lg border border-border bg-muted/50 grid place-items-center text-muted-foreground hover:bg-muted hover:text-foreground transition"
                  @click="drawerTenant = null">
            <X class="size-4" />
          </button>
          <div class="size-14 rounded-2xl grid place-items-center font-semibold text-2xl border" :style="monoStyle(drawerTenant.tenantCode)">
            {{ monoChar(drawerTenant.tenantCode) }}
          </div>
          <h2 class="text-2xl font-semibold mt-4 text-foreground tracking-tight">{{ drawerTenant.displayName }}</h2>
          <div class="font-mono text-[13px] text-muted-foreground mt-1.5">
            {{ drawerTenant.tenantCode }}<span v-if="isBuiltIn(drawerTenant)"> · {{ t('platform.tenant.list.builtIn') }}</span>
          </div>
          <div class="mt-3.5 inline-flex items-center gap-2 text-[13px] font-semibold text-foreground">
            <span class="size-2 rounded-full" :style="{ background: statusDot(drawerTenant.status) }"></span>
            {{ tenantStatus.label(drawerTenant.status) }}
          </div>
        </div>

        <div class="p-6 border-b border-border">
          <h4 class="text-[11px] font-bold uppercase tracking-wider text-muted-foreground mb-3.5">{{ t('platform.tenant.list.overview') }}</h4>
          <dl class="grid grid-cols-[130px_1fr] gap-x-3 gap-y-3 text-[13px]">
            <dt class="text-muted-foreground">{{ t('platform.tenant.column.contactEmail') }}</dt>
            <dd class="m-0 font-mono text-right text-foreground break-all">{{ drawerTenant.contactEmail || '—' }}</dd>
            <dt class="text-muted-foreground">{{ t('platform.tenant.list.fieldRealm') }}</dt>
            <dd class="m-0 font-mono text-right text-foreground">{{ drawerTenant.tenantCode }}</dd>
            <dt class="text-muted-foreground">{{ t('platform.tenant.column.createTime') }}</dt>
            <dd class="m-0 font-mono text-right text-foreground">{{ fmtAbs(drawerTenant.createTime) }}</dd>
            <dt class="text-muted-foreground">{{ t('platform.tenant.list.fieldUpdated') }}</dt>
            <dd class="m-0 font-mono text-right text-foreground">{{ fmtAbs(drawerTenant.updateTime) }}</dd>
          </dl>
        </div>

        <div class="p-6 flex flex-col gap-2.5 mt-auto">
          <button v-permission="'platform:tenant:update'" type="button"
                  class="drawer-btn bg-primary text-primary-foreground hover:opacity-90 disabled:opacity-40"
                  :disabled="isBuiltIn(drawerTenant)" @click="openEdit(drawerTenant)">
            <Pencil class="size-4" /> {{ t('platform.tenant.button.edit') }}
          </button>
          <button v-permission="'platform:tenant:impersonate'" type="button"
                  class="drawer-btn border border-border bg-card text-foreground hover:bg-muted disabled:opacity-40"
                  :disabled="isBuiltIn(drawerTenant) || drawerTenant.status !== 1" @click="openSupportSession(drawerTenant)">
            <LifeBuoy class="size-4" /> {{ t('platform.tenant.list.actSupport') }}
          </button>
          <button v-permission="'platform:tenant:update'" type="button"
                  class="drawer-btn border border-border bg-card text-foreground hover:bg-muted disabled:opacity-40"
                  :disabled="isBuiltIn(drawerTenant)" @click="openResendInvite(drawerTenant)">
            <Send class="size-4" /> {{ t('platform.tenant.list.actResend') }}
          </button>
          <button v-if="drawerTenant.status === 1" v-permission="'platform:tenant:update'" type="button"
                  class="drawer-btn border border-border bg-card text-foreground hover:bg-muted disabled:opacity-40"
                  :disabled="isBuiltIn(drawerTenant)" @click="handleSuspend(drawerTenant)">
            <Pause class="size-4" /> {{ t('platform.tenant.button.suspend') }}
          </button>
          <template v-else>
            <button v-permission="'platform:tenant:update'" type="button"
                    class="drawer-btn border border-border bg-card text-foreground hover:bg-muted"
                    @click="handleResume(drawerTenant)">
              <Play class="size-4" /> {{ t('platform.tenant.button.resume') }}
            </button>
            <button v-permission="'platform:tenant:delete'" type="button"
                    class="drawer-btn border border-destructive/30 bg-destructive/10 text-destructive hover:bg-destructive/15 disabled:opacity-40"
                    :disabled="isBuiltIn(drawerTenant)" @click="openHardDelete(drawerTenant)">
              <Trash2 class="size-4" /> {{ t('platform.tenant.hardDelete.button.confirm') }}
            </button>
          </template>
        </div>
      </aside>
    </Transition>

    <!-- ── Dialogs ───────────────────────────────────────────────────── -->
    <TenantCreate v-model:open="showCreate" @saved="refreshAll" />
    <TenantEdit :row="editTarget" @close="editTarget = null" @saved="() => { editTarget = null; refreshAll() }" />
    <TenantSupportSession :row="supportTarget" @close="supportTarget = null" @start="handleSupportSession" />
    <TenantHardDelete :row="hardDeleteTarget" @close="hardDeleteTarget = null"
                      @deleted="() => { hardDeleteTarget = null; drawerTenant = null; refreshAll() }" />
    <TenantResendInvite :row="resendTarget" @close="resendTarget = null" @resend="handleResendInvite" />
  </div>
</template>

<style scoped>
/* Suspended rows: subtle diagonal hatch so they read as "parked" at a glance. */
.tenant-suspended {
  background-image: repeating-linear-gradient(135deg, transparent, transparent 9px,
    color-mix(in srgb, var(--signal-yellow) 7%, transparent) 9px,
    color-mix(in srgb, var(--signal-yellow) 7%, transparent) 10px);
}

.pgbtn {
  min-width: 2.125rem;
  height: 2.125rem;
  border-radius: 0.5rem;
  border: 1px solid var(--border);
  background: var(--card);
  color: var(--muted-foreground);
  font-size: 13px;
  font-weight: 600;
  display: inline-grid;
  place-items: center;
  cursor: pointer;
  transition: background .13s, color .13s, border-color .13s;
}
.pgbtn:hover:not(:disabled) { color: var(--foreground); border-color: var(--muted-foreground); }
.pgbtn:disabled { opacity: .4; cursor: not-allowed; }
.pgbtn-active, .pgbtn-active:hover {
  background: var(--primary);
  border-color: var(--primary);
  color: var(--primary-foreground);
}

.drawer-btn {
  width: 100%;
  height: 2.75rem;
  border-radius: 0.625rem;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  gap: 0.5rem;
  font-size: 14px;
  font-weight: 600;
  cursor: pointer;
  transition: background .15s, opacity .15s;
}
.drawer-btn:disabled { cursor: not-allowed; }

/* Drawer enter/leave */
.drawer-overlay-enter-active, .drawer-overlay-leave-active { transition: opacity .25s; }
.drawer-overlay-enter-from, .drawer-overlay-leave-to { opacity: 0; }
.drawer-panel-enter-active, .drawer-panel-leave-active { transition: transform .3s cubic-bezier(.22,.61,.36,1); }
.drawer-panel-enter-from, .drawer-panel-leave-to { transform: translateX(100%); }
</style>
