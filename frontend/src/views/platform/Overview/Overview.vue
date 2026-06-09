<script setup>
import { reactive, ref, computed, onMounted } from 'vue'
import { useI18n } from 'vue-i18n'
import { toast } from '@/composables/useToast'
import { toJSTDateTimeDisp, nowJST } from '@/lib/date'
import { getTenantStatsApi, getPlatformDashboardApi } from '@/services/tenant'
import AreaChart from './AreaChart.vue'
import Drawer from '@/components/ui/Drawer.vue'
import {
  Building2, ShieldCheck, PauseCircle, TrendingUp, PieChart, UserCheck,
  Activity, ServerCog, ShieldAlert, RotateCcw, KeyRound, Flame, CheckCircle2, AlarmClockOff, Eye
} from 'lucide-vue-next'

const { t, locale } = useI18n()

const loaded = ref(false)
const updatedAt = ref('')
const stats = reactive({ total: 0, active: 0, suspended: 0, newThisMonth: 0, monthly: [] })
const dash = reactive({ activation: null, engagement: null, reliability: null, security: null })

// Which detail list each selectable panel shows. Defaults to the first metric
// with a value (set on fetch) so the most relevant detail shows without a click.
const sel = reactive({ act: 'pending', rel: 'jobs', sec: 'support' })
function pick(group, key) { sel[group] = key }
function firstWithValue(cands) {
  const hit = cands.find(([, v]) => (v || 0) > 0)
  return (hit || cands[0])[0]
}

// ── helpers ────────────────────────────────────────────────────────────────
const pct = (n) => `${Math.round((n || 0) * 100)}%`
function fmtHours(h) {
  if (h == null) return '—'
  if (h < 1) return `${Math.round(h * 60)}m`
  if (h < 48) return `${Math.round(h)}h`
  return `${Math.round(h / 24)}d`
}
function fmtMinutes(m) {
  if (m == null) return '—'
  if (m < 60) return `${m}m`
  if (m < 1440) return `${Math.round(m / 60)}h`
  return `${Math.round(m / 1440)}d`
}
const fmtDate = (s) => (s ? toJSTDateTimeDisp(s) : '—')
function reasonOf(raw) {
  if (!raw) return ''
  try { return JSON.parse(raw).reason || raw } catch { return raw }
}

const LOCALE_MAP = { zh_CN: 'zh-CN', zh_TW: 'zh-TW', ja_JP: 'ja', ko_KR: 'ko', en: 'en' }
function relTime(iso) {
  if (!iso) return ''
  const diffSec = Math.round((Date.now() - new Date(iso).getTime()) / 1000)
  const rtf = new Intl.RelativeTimeFormat(LOCALE_MAP[locale.value] || 'en', { numeric: 'auto' })
  const min = diffSec / 60, hr = min / 60, day = hr / 24
  if (Math.abs(diffSec) < 60) return rtf.format(-diffSec, 'second')
  if (Math.abs(min) < 60) return rtf.format(-Math.round(min), 'minute')
  if (Math.abs(hr) < 24) return rtf.format(-Math.round(hr), 'hour')
  return rtf.format(-Math.round(day), 'day')
}

// Deterministic theme-aware letter badge (same approach as the tenant monogram).
function monoStyle(code) {
  let h = 0
  const s = code || '?'
  for (let i = 0; i < s.length; i++) h = (h * 31 + s.charCodeAt(i)) >>> 0
  const hue = h % 360
  return {
    background: `color-mix(in oklab, hsl(${hue} 60% 50%) 16%, var(--card))`,
    color: `color-mix(in oklab, hsl(${hue} 65% 45%) 90%, var(--foreground))`
  }
}
const initial = (s) => (s || '?').charAt(0).toUpperCase()

// ── derived view models ──────────────────────────────────────────────────
const kpis = computed(() => {
  const total = stats.total || 0
  const uptime = total ? pct(stats.active / total) : '0%'
  return [
    { icon: Building2, num: stats.total, label: t('platform.tenant.dashboard.total'), tone: 'var(--primary)', delta: t('platform.tenant.overview.allRealms'), dc: 'var(--muted-foreground)' },
    { icon: ShieldCheck, num: stats.active, label: t('platform.tenant.status.active'), tone: 'var(--signal-green)', delta: `${uptime} ${t('platform.tenant.overview.uptime')}`, dc: 'var(--signal-green)' },
    { icon: PauseCircle, num: stats.suspended, label: t('platform.tenant.status.suspended'), tone: 'var(--signal-yellow)', delta: t('platform.tenant.overview.recycleBin'), dc: 'var(--muted-foreground)' },
    { icon: TrendingUp, num: stats.newThisMonth, label: t('platform.tenant.dashboard.newThisMonth'), tone: 'var(--signal-blue)', delta: `+${stats.newThisMonth || 0} ${t('platform.tenant.overview.thisMonth')}`, dc: 'var(--signal-blue)' }
  ]
})

const donut = computed(() => {
  const a = stats.active || 0, s = stats.suspended || 0, sum = a + s || 1
  const size = 132, stroke = 16, r = (size - stroke) / 2, C = 2 * Math.PI * r
  let acc = 0
  const segs = [
    { v: a, color: 'var(--signal-green)' },
    { v: s, color: 'var(--signal-yellow)' }
  ].filter((x) => x.v > 0).map((x) => {
    const frac = x.v / sum, len = frac * C, off = acc * C; acc += frac
    return { color: x.color, dash: `${len.toFixed(2)} ${(C - len).toFixed(2)}`, offset: -off }
  })
  return { size, stroke, r, c: C, cx: size / 2, segs, total: stats.total }
})
const donutLegend = computed(() => {
  const a = stats.active || 0, s = stats.suspended || 0, sum = a + s || 1
  return [
    { color: 'var(--signal-green)', name: t('platform.tenant.status.active'), val: a, pct: pct(a / sum) },
    { color: 'var(--signal-yellow)', name: t('platform.tenant.status.suspended'), val: s, pct: pct(s / sum) }
  ]
})

const monthTrend = computed(() => ({
  data: (stats.monthly || []).map((m) => m.count),
  labels: (stats.monthly || []).map((m) => m.month.slice(2))
}))
const loginTrend = computed(() => {
  const series = dash.engagement?.loginTrend || []
  return { data: series.map((d) => d.count), labels: series.map((d) => d.day.slice(5)) }
})

const funnelBar = computed(() => Math.round((dash.activation?.activationRate || 0) * 100))
const healthOk = computed(() => {
  const r = dash.reliability || {}
  return (r.jobFailures24h || 0) === 0 && (r.eventPending || 0) + (r.eventFailed || 0) === 0 && (r.oplogErrors24h || 0) === 0
})

// ── selectable detail lists (preserved from the original panels) ───────────
const actList = computed(() => {
  const a = dash.activation; if (!a) return []
  return sel.act === 'pending' ? (a.pending || []) : (a.expired || [])
})
const relList = computed(() => {
  const r = dash.reliability; if (!r) return []
  if (sel.rel === 'jobs') return r.recentJobFailures || []
  if (sel.rel === 'errors') return r.recentOplogErrors || []
  return r.backlogEvents || [] // backlog | oldest share the undispatched-event list
})
// Platform-health row drill-down: the eye on each reliability row opens a
// drawer with every field, untruncated. `kind` collapses backlog/oldest (both
// the undispatched-event list) into one 'event' renderer.
const healthDetail = ref(null)  // { kind: 'jobs'|'event'|'errors', row }
function openHealthDetail(row) {
  const kind = sel.rel === 'jobs' ? 'jobs' : sel.rel === 'errors' ? 'errors' : 'event'
  healthDetail.value = { kind, row }
}
const healthDetailFields = computed(() => {
  const d = healthDetail.value
  if (!d) return []
  const r = d.row
  const L = (k) => t('platform.tenant.ops.reliability.detail.' + k)
  if (d.kind === 'jobs') return [
    { label: L('jobCode'), value: r.jobCode, mono: true },
    { label: L('startTime'), value: fmtDate(r.startTime) },
    { label: L('duration'), value: r.durationMs != null ? `${r.durationMs} ms` : '—' },
    { label: L('error'), value: r.error || '—', pre: true }
  ]
  if (d.kind === 'errors') return [
    { label: L('api'), value: `${r.module}.${r.action}`, mono: true },
    { label: L('username'), value: r.username || '—' },
    { label: L('time'), value: fmtDate(r.time) },
    { label: L('errorMsg'), value: r.errorMsg || '—', pre: true }
  ]
  return [
    { label: L('eventType'), value: r.eventType, mono: true },
    { label: L('aggregateType'), value: r.aggregateType, mono: true },
    { label: L('state'), value: r.dispatchState === 2 ? t('platform.tenant.ops.reliability.stateFailed') : t('platform.tenant.ops.reliability.statePending') },
    { label: L('attempts'), value: `×${r.attempts}` },
    { label: L('occurredAt'), value: fmtDate(r.occurredAt) }
  ]
})

const secIsBreakGlass = computed(() => sel.sec === 'breakglass')
const secList = computed(() => {
  const s = dash.security; if (!s) return []
  if (secIsBreakGlass.value) return s.recentBreakGlass || []
  const sessions = s.recentSupportSessions || []
  // "会话进行中" (active) shows only live sessions; "支持会话(7天)" shows all recent.
  return sel.sec === 'support' ? sessions.filter((x) => x.active) : sessions
})

// ── data ───────────────────────────────────────────────────────────────────
async function fetchAll() {
  try {
    const [sRes, dRes] = await Promise.all([getTenantStatsApi(), getPlatformDashboardApi()])
    if (sRes.data.code === 0) {
      const d = sRes.data.data || {}
      stats.total = d.total || 0
      stats.active = d.active || 0
      stats.suspended = d.suspended || 0
      stats.newThisMonth = d.newThisMonth || 0
      stats.monthly = d.monthly || []
    }
    if (dRes.data.code === 0) {
      const d = dRes.data.data || {}
      const A = d.activation || {}; A.pending ||= []; A.expired ||= []
      const E = d.engagement || {}; E.silentTenants ||= []; E.loginTrend ||= []
      const R = d.reliability || {}; R.recentJobFailures ||= []; R.recentOplogErrors ||= []; R.backlogEvents ||= []
      const S = d.security || {}; S.recentSupportSessions ||= []; S.recentBreakGlass ||= []
      dash.activation = A; dash.engagement = E; dash.reliability = R; dash.security = S
      sel.act = firstWithValue([['pending', A.pendingTenants], ['expired', A.expiredUnactivated]])
      sel.rel = firstWithValue([
        ['jobs', R.jobFailures24h],
        ['backlog', (R.eventPending || 0) + (R.eventFailed || 0)],
        ['oldest', R.eventBacklogOldestMin],
        ['errors', R.oplogErrors24h]
      ])
      sel.sec = firstWithValue([
        ['support', S.activeSupportSessions],
        ['support7d', S.supportSessions7d],
        ['breakglass', S.breakGlass7d]
      ])
    }
    updatedAt.value = nowJST().format('YYYY/MM/DD HH:mm')
    loaded.value = true
  } catch (e) {
    toast.error(e.message)
  }
}
onMounted(fetchAll)
</script>

<template>
  <div class="dash">
    <!-- header: last-updated + refresh -->
    <div class="dh">
      <div class="updated">{{ t('platform.tenant.overview.updated') }} {{ updatedAt || '—' }}</div>
      <button type="button" class="refresh-btn" @click="fetchAll">
        <RotateCcw class="size-3.5" /> {{ t('platform.tenant.overview.refresh') }}
      </button>
    </div>

    <div v-if="!loaded" class="bento">
      <div v-for="i in 7" :key="i" :class="['skeleton', i <= 4 ? 'c3' : (i === 5 ? 'c4' : 'c8')]" />
    </div>

    <div v-else class="bento">
      <!-- KPI row -->
      <div v-for="k in kpis" :key="k.label" class="dcard kpi c3">
        <span class="kpi-ico" :style="{ background: `color-mix(in oklab, ${k.tone} 14%, var(--card))`, color: k.tone }">
          <component :is="k.icon" class="size-[22px]" />
        </span>
        <div class="kpi-body">
          <span class="kpi-label">{{ k.label }}</span>
          <div class="kpi-num">{{ k.num }}</div>
          <div class="kpi-delta" :style="{ color: k.dc }">{{ k.delta }}</div>
        </div>
      </div>

      <!-- status donut -->
      <div class="dcard c4">
        <div class="dcard-h"><span class="hi"><PieChart class="size-[17px]" /></span><h3>{{ t('platform.tenant.dashboard.statusTitle') }}</h3></div>
        <div class="donut-wrap">
          <div class="donut" :style="{ width: donut.size + 'px', height: donut.size + 'px' }">
            <svg :width="donut.size" :height="donut.size">
              <circle :cx="donut.cx" :cy="donut.cx" :r="donut.r" fill="none" stroke="var(--muted)" :stroke-width="donut.stroke" />
              <circle v-for="(g, i) in donut.segs" :key="i" :cx="donut.cx" :cy="donut.cx" :r="donut.r" fill="none"
                      :style="{ stroke: g.color }" :stroke-width="donut.stroke" :stroke-dasharray="g.dash"
                      :stroke-dashoffset="g.offset" stroke-linecap="butt" :transform="`rotate(-90 ${donut.cx} ${donut.cx})`" />
            </svg>
            <div class="donut-center"><div class="ct-num">{{ donut.total }}</div><div class="ct-lbl">{{ t('platform.tenant.dashboard.total') }}</div></div>
          </div>
          <div class="legend">
            <div v-for="(l, i) in donutLegend" :key="i" class="lrow">
              <span class="ld" :style="{ background: l.color }"></span>
              <span class="lname">{{ l.name }}</span><span class="lval">{{ l.val }}</span><span class="lpct">{{ l.pct }}</span>
            </div>
          </div>
        </div>
      </div>

      <!-- new-tenant trend -->
      <div class="dcard c8">
        <div class="dcard-h"><span class="hi"><TrendingUp class="size-[17px]" /></span><h3>{{ t('platform.tenant.dashboard.trendTitle') }}</h3><span class="h-aux">12M</span></div>
        <AreaChart :data="monthTrend.data" :labels="monthTrend.labels" :height="188" :every-x="1" />
      </div>

      <!-- onboarding funnel (pending / expired tiles switch the detail list) -->
      <div class="dcard c6">
        <div class="dcard-h"><span class="hi"><UserCheck class="size-[17px]" /></span><h3>{{ t('platform.tenant.ops.activation.title') }}</h3></div>
        <div class="tiles t4">
          <button type="button" class="tile tile-btn" :class="{ sel: sel.act === 'pending' }" @click="pick('act', 'pending')" :title="t('platform.tenant.ops.activation.tip.pending')">
            <div class="tv">{{ dash.activation.pendingTenants ?? 0 }}</div><div class="tl">{{ t('platform.tenant.ops.activation.pending') }}</div>
          </button>
          <button type="button" class="tile tile-btn" :class="{ sel: sel.act === 'expired' }" @click="pick('act', 'expired')" :title="t('platform.tenant.ops.activation.tip.expired')">
            <div class="tv">{{ dash.activation.expiredUnactivated ?? 0 }}</div><div class="tl">{{ t('platform.tenant.ops.activation.expired') }}</div>
          </button>
          <div class="tile accent" :title="t('platform.tenant.ops.activation.tip.rate')"><div class="tv">{{ pct(dash.activation.activationRate) }}</div><div class="tl">{{ t('platform.tenant.ops.activation.rate') }}</div></div>
          <div class="tile" :title="t('platform.tenant.ops.activation.tip.median')"><div class="tv">{{ fmtHours(dash.activation.medianOnboardingHours) }}</div><div class="tl">{{ t('platform.tenant.ops.activation.median') }}</div></div>
        </div>
        <div class="funnel">
          <div class="fbar"><i :style="{ width: funnelBar + '%' }"></i></div>
          <div class="fcap"><span>{{ t('platform.tenant.ops.activation.rate') }}</span><span>{{ funnelBar }}%</span></div>
        </div>
        <div class="dlist">
          <template v-if="actList.length">
            <div v-for="p in actList" :key="p.tenantId" class="dlrow">
              <span class="dl-code">{{ p.tenantCode }}</span>
              <span class="dl-mut trunc">{{ p.contactEmail || '—' }}</span>
              <span class="dl-time" :style="p.expired ? { color: 'var(--destructive)' } : null">{{ fmtDate(p.expiresAt) }}</span>
            </div>
          </template>
          <div v-else class="dl-empty">{{ t('platform.tenant.ops.empty') }}</div>
        </div>
      </div>

      <!-- activity & engagement (tiles + login trend + silent list) -->
      <div class="dcard c6">
        <div class="dcard-h"><span class="hi"><Activity class="size-[17px]" /></span><h3>{{ t('platform.tenant.ops.engagement.title') }}</h3></div>
        <div class="tiles t5">
          <div class="tile" :title="t('platform.tenant.ops.engagement.tip.active7d')"><div class="tv">{{ dash.engagement.activeTenants7d ?? 0 }}</div><div class="tl">{{ t('platform.tenant.ops.engagement.active7d') }}</div></div>
          <div class="tile" :title="t('platform.tenant.ops.engagement.tip.active30d')"><div class="tv">{{ dash.engagement.activeTenants30d ?? 0 }}</div><div class="tl">{{ t('platform.tenant.ops.engagement.active30d') }}</div></div>
          <div class="tile" :title="t('platform.tenant.ops.engagement.tip.dau')"><div class="tv">{{ dash.engagement.dau ?? 0 }}</div><div class="tl">{{ t('platform.tenant.ops.engagement.dau') }}</div></div>
          <div class="tile" :title="t('platform.tenant.ops.engagement.tip.mau')"><div class="tv">{{ dash.engagement.mau ?? 0 }}</div><div class="tl">{{ t('platform.tenant.ops.engagement.mau') }}</div></div>
          <div class="tile" :title="t('platform.tenant.ops.engagement.tip.silent')"><div class="tv">{{ dash.engagement.silentTenantsCount ?? 0 }}</div><div class="tl">{{ t('platform.tenant.ops.engagement.silent') }}</div></div>
        </div>
        <div class="chart-cap">{{ t('platform.tenant.ops.engagement.trendTitle') }}</div>
        <AreaChart :data="loginTrend.data" :labels="loginTrend.labels" :height="130" :every-x="2" />
        <div class="chart-cap" style="display:flex;align-items:center;gap:6px"><AlarmClockOff class="size-3.5" /> {{ t('platform.tenant.ops.engagement.silentListTitle') }}</div>
        <div class="dlist">
          <template v-if="dash.engagement.silentTenants.length">
            <div v-for="s in dash.engagement.silentTenants" :key="s.tenantId" class="dlrow">
              <span class="dl-code">{{ s.tenantCode }}</span>
              <span class="dl-mut trunc">{{ s.displayName }}</span>
              <span class="dl-time">{{ s.lastLoginAt ? fmtDate(s.lastLoginAt) : t('platform.tenant.ops.engagement.never') }}</span>
            </div>
          </template>
          <div v-else class="dl-empty">{{ t('platform.tenant.ops.empty') }}</div>
        </div>
      </div>

      <!-- platform health (tiles switch the detail list) -->
      <div class="dcard c5">
        <div class="dcard-h"><span class="hi"><ServerCog class="size-[17px]" /></span><h3>{{ t('platform.tenant.ops.reliability.title') }}</h3></div>
        <div class="tiles t4">
          <button type="button" class="tile tile-btn" :class="{ sel: sel.rel === 'jobs', danger: (dash.reliability.jobFailures24h || 0) > 0 }" @click="pick('rel', 'jobs')" :title="t('platform.tenant.ops.reliability.tip.jobFailures')">
            <div class="tv">{{ dash.reliability.jobFailures24h ?? 0 }}</div><div class="tl">{{ t('platform.tenant.ops.reliability.jobFailures') }}</div>
          </button>
          <button type="button" class="tile tile-btn" :class="{ sel: sel.rel === 'backlog', warn: ((dash.reliability.eventPending || 0) + (dash.reliability.eventFailed || 0)) > 0 }" @click="pick('rel', 'backlog')" :title="t('platform.tenant.ops.reliability.tip.eventBacklog')">
            <div class="tv">{{ (dash.reliability.eventPending || 0) + (dash.reliability.eventFailed || 0) }}</div><div class="tl">{{ t('platform.tenant.ops.reliability.eventBacklog') }}</div>
          </button>
          <button type="button" class="tile tile-btn" :class="{ sel: sel.rel === 'oldest' }" @click="pick('rel', 'oldest')" :title="t('platform.tenant.ops.reliability.tip.backlogAge')">
            <div class="tv">{{ fmtMinutes(dash.reliability.eventBacklogOldestMin) }}</div><div class="tl">{{ t('platform.tenant.ops.reliability.backlogAge') }}</div>
          </button>
          <button type="button" class="tile tile-btn" :class="{ sel: sel.rel === 'errors', danger: (dash.reliability.oplogErrors24h || 0) > 0 }" @click="pick('rel', 'errors')" :title="t('platform.tenant.ops.reliability.tip.oplogErrors')">
            <div class="tv">{{ dash.reliability.oplogErrors24h ?? 0 }}</div><div class="tl">{{ t('platform.tenant.ops.reliability.oplogErrors') }}</div>
          </button>
        </div>
        <div class="health-foot">
          <span v-if="healthOk" class="ok-pill"><CheckCircle2 class="size-3.5" /> {{ t('platform.tenant.overview.healthOk') }}</span>
          <span v-else class="warn-pill"><ShieldAlert class="size-3.5" /> {{ t('platform.tenant.overview.healthIssues') }}</span>
        </div>
        <div class="dlist">
          <template v-if="relList.length">
            <!-- job failures -->
            <template v-if="sel.rel === 'jobs'">
              <div v-for="(f, i) in relList" :key="i" class="dlrow">
                <span class="dl-code">{{ f.jobCode }}</span>
                <span class="trunc" style="color: color-mix(in oklab, var(--destructive) 80%, var(--foreground))" :title="f.error">{{ f.error || '—' }}</span>
                <span class="dl-time">{{ fmtDate(f.startTime) }}</span>
                <button type="button" class="dl-eye" :title="t('common.button.detail')" @click="openHealthDetail(f)"><Eye class="size-3.5" /></button>
              </div>
            </template>
            <!-- undispatched events (backlog / oldest) -->
            <template v-else-if="sel.rel === 'backlog' || sel.rel === 'oldest'">
              <div v-for="(b, i) in relList" :key="i" class="dlrow">
                <span class="dl-code trunc">{{ b.eventType }}</span>
                <span class="state-pill" :class="b.dispatchState === 2 ? 'failed' : 'pending'">{{ b.dispatchState === 2 ? t('platform.tenant.ops.reliability.stateFailed') : t('platform.tenant.ops.reliability.statePending') }}</span>
                <span class="dl-mut">×{{ b.attempts }}</span>
                <span class="dl-time">{{ fmtDate(b.occurredAt) }}</span>
                <button type="button" class="dl-eye" :title="t('common.button.detail')" @click="openHealthDetail(b)"><Eye class="size-3.5" /></button>
              </div>
            </template>
            <!-- API errors -->
            <template v-else>
              <div v-for="(e, i) in relList" :key="i" class="dlrow">
                <span class="dl-code">{{ e.module }}.{{ e.action }}</span>
                <span class="trunc" style="color: color-mix(in oklab, var(--destructive) 80%, var(--foreground))" :title="e.errorMsg">{{ e.errorMsg || '—' }}</span>
                <span class="dl-mut">{{ e.username || '—' }}</span>
                <span class="dl-time">{{ fmtDate(e.time) }}</span>
                <button type="button" class="dl-eye" :title="t('common.button.detail')" @click="openHealthDetail(e)"><Eye class="size-3.5" /></button>
              </div>
            </template>
          </template>
          <div v-else class="dl-empty">{{ t('platform.tenant.ops.empty') }}</div>
        </div>
      </div>

      <!-- security & privileged-access (tiles switch support / break-glass list) -->
      <div class="dcard c7">
        <div class="dcard-h"><span class="hi"><ShieldAlert class="size-[17px]" /></span><h3>{{ t('platform.tenant.ops.security.title') }}</h3></div>
        <div class="tiles t5">
          <button type="button" class="tile tile-btn" :class="{ sel: sel.sec === 'support', warn: (dash.security.activeSupportSessions || 0) > 0 }" @click="pick('sec', 'support')">
            <div class="tv">{{ dash.security.activeSupportSessions ?? 0 }}</div><div class="tl">{{ t('platform.tenant.ops.security.activeSupport') }}</div>
          </button>
          <button type="button" class="tile tile-btn" :class="{ sel: sel.sec === 'support7d' }" @click="pick('sec', 'support7d')">
            <div class="tv">{{ dash.security.supportSessions7d ?? 0 }}</div><div class="tl">{{ t('platform.tenant.ops.security.support7d') }}</div>
          </button>
          <button type="button" class="tile tile-btn" :class="{ sel: sel.sec === 'breakglass', warn: (dash.security.breakGlass7d || 0) > 0 }" @click="pick('sec', 'breakglass')">
            <div class="tv">{{ dash.security.breakGlass7d ?? 0 }}</div><div class="tl">{{ t('platform.tenant.ops.security.breakGlass') }}</div>
          </button>
          <div class="tile" :class="{ danger: (dash.security.loginFailures24h || 0) >= 10 }"><div class="tv">{{ dash.security.loginFailures24h ?? 0 }}</div><div class="tl">{{ t('platform.tenant.ops.security.loginFailures') }}</div></div>
          <div class="tile"><div class="tv">{{ dash.security.passwordResets7d ?? 0 }}</div><div class="tl">{{ t('platform.tenant.ops.security.passwordResets') }}</div></div>
        </div>
        <div class="aud" style="margin-top: 16px">
          <template v-if="secList.length">
            <!-- break-glass -->
            <template v-if="secIsBreakGlass">
              <div v-for="(b, i) in secList" :key="i" class="aud-row">
                <span class="aud-sev high"></span>
                <span class="aud-badge" :style="monoStyle(b.operator)">{{ initial(b.operator) }}</span>
                <div class="aud-main">
                  <span class="aud-actor">{{ b.operator }}</span>
                  <Flame class="size-3 shrink-0" :style="{ color: 'var(--destructive)' }" />
                  <span class="aud-arrow">→</span>
                  <span class="aud-target">{{ b.tenantCode }}</span>
                  <span v-if="b.clientIp" class="aud-code">{{ b.clientIp }}</span>
                </div>
                <span class="aud-time">{{ relTime(b.usedAt) }}</span>
              </div>
            </template>
            <!-- support sessions -->
            <template v-else>
              <div v-for="(s, i) in secList" :key="i" class="aud-row">
                <span class="aud-sev" :class="s.active ? 'high' : 'mid'"></span>
                <span class="aud-badge" :style="monoStyle(s.operator)">{{ initial(s.operator) }}</span>
                <div class="aud-main">
                  <span class="aud-actor">{{ s.operator }}</span>
                  <KeyRound class="size-3 shrink-0" :style="{ color: 'var(--signal-yellow)' }" />
                  <span class="aud-arrow">→</span>
                  <span class="aud-target">{{ s.targetTenantCode || '—' }}</span>
                  <span v-if="reasonOf(s.reason)" class="aud-code">{{ reasonOf(s.reason) }}</span>
                </div>
                <span class="aud-time">{{ relTime(s.startedAt) }}</span>
              </div>
            </template>
          </template>
          <div v-else class="dl-empty">{{ t('platform.tenant.ops.empty') }}</div>
        </div>
      </div>
    </div>

    <!-- platform-health row detail (full, untruncated fields) -->
    <Drawer :open="!!healthDetail" :title="t('platform.tenant.ops.reliability.detail.title')" width="max-w-md" @close="healthDetail = null">
      <div v-if="healthDetail" class="hd-grid">
        <template v-for="(f, i) in healthDetailFields" :key="i">
          <div class="hd-label">{{ f.label }}</div>
          <pre v-if="f.pre" class="hd-pre">{{ f.value }}</pre>
          <div v-else class="hd-val" :class="{ mono: f.mono }">{{ f.value }}</div>
        </template>
      </div>
      <template #footer>
        <button type="button" class="refresh-btn" @click="healthDetail = null">{{ t('common.button.close') }}</button>
      </template>
    </Drawer>
  </div>
</template>

<style scoped>
.dash { padding-top: 0.5rem; }

/* header */
.dh { display: flex; align-items: center; justify-content: space-between; gap: 20px; margin-bottom: 16px; flex-wrap: wrap; }
.dh .updated { font-family: var(--font-mono, ui-monospace, monospace); font-size: 11.5px; color: var(--muted-foreground); }
.refresh-btn { display: inline-flex; align-items: center; gap: 6px; height: 30px; padding: 0 11px; font-size: 12px; font-weight: 600;
  border-radius: 8px; border: 1px solid var(--border); background: var(--card); color: var(--foreground); cursor: pointer; transition: background .15s; }
.refresh-btn:hover { background: var(--muted); }

/* bento grid — cards in the same row stretch to equal height; each card's
   trailing block (detail list / donut / audit) absorbs the extra space. */
.bento { display: grid; grid-template-columns: repeat(12, 1fr); gap: 14px; align-items: stretch; }
.c3 { grid-column: span 3; } .c4 { grid-column: span 4; } .c5 { grid-column: span 5; }
.c6 { grid-column: span 6; } .c7 { grid-column: span 7; } .c8 { grid-column: span 8; }
@media (max-width: 1100px) {
  .c3 { grid-column: span 6; } .c4, .c5 { grid-column: span 6; }
  .c6, .c7, .c8 { grid-column: span 12; }
}
@media (max-width: 640px) { .c3, .c4, .c5, .c6 { grid-column: span 12; } }

.skeleton { height: 132px; border-radius: 16px; background: var(--muted); opacity: .5; animation: pulse 1.4s ease-in-out infinite; }
@keyframes pulse { 50% { opacity: .25; } }

/* card */
.dcard { background: var(--card); border: 1px solid var(--border); border-radius: 16px; box-shadow: var(--shadow-sm, 0 1px 2px rgba(0,0,0,.04));
  padding: 18px 20px; display: flex; flex-direction: column; }
.dcard-h { display: flex; align-items: center; gap: 9px; margin-bottom: 16px; }
.dcard-h .hi { color: var(--primary); display: inline-flex; }
.dcard-h h3 { font-size: 14px; font-weight: 700; color: var(--foreground); margin: 0; letter-spacing: .01em; white-space: nowrap; }
.dcard-h .h-aux { margin-left: auto; font-size: 12px; color: var(--muted-foreground); font-family: var(--font-mono, ui-monospace, monospace); }

/* KPI */
.kpi { flex-direction: row; align-items: center; gap: 15px; }
.kpi-ico { width: 48px; height: 48px; border-radius: 13px; display: grid; place-items: center; flex: none; }
.kpi-body { min-width: 0; display: flex; flex-direction: column; justify-content: center; }
.kpi-label { font-size: 12.5px; color: var(--muted-foreground); font-weight: 600; white-space: nowrap; }
.kpi-num { font-size: 32px; font-weight: 650; line-height: 1; letter-spacing: -.02em; margin-top: 7px; color: var(--foreground); }
.kpi-delta { font-family: var(--font-mono, ui-monospace, monospace); font-size: 11.5px; margin-top: 8px; white-space: nowrap; }

/* tiles */
.tiles { display: grid; gap: 10px; }
.tiles.t4 { grid-template-columns: repeat(4, 1fr); }
.tiles.t5 { grid-template-columns: repeat(5, 1fr); }
.tile { background: var(--muted); border: 1px solid var(--border); border-radius: 11px; padding: 12px 13px; }
.tile .tv { font-size: 22px; font-weight: 650; line-height: 1; letter-spacing: -.01em; color: var(--foreground); }
.tile .tl { font-size: 11.5px; color: var(--muted-foreground); margin-top: 6px; font-weight: 500; white-space: nowrap; overflow: hidden; text-overflow: ellipsis; }
.tile-btn { cursor: pointer; text-align: left; font: inherit; transition: background .13s, border-color .13s; }
.tile-btn:hover { background: var(--secondary); }
/* Selected tile: a 2px primary ring via inset shadow (no border-width change →
   no layout shift), plus the tinted background. */
.tile.sel { border-color: var(--primary); box-shadow: inset 0 0 0 1px var(--primary); background: color-mix(in oklab, var(--primary) 10%, var(--card)); }
.tile.accent { background: color-mix(in oklab, var(--primary) 12%, var(--card)); border-color: color-mix(in oklab, var(--primary) 26%, transparent); }
.tile.accent .tv { color: var(--primary); }
.tile.warn { background: color-mix(in oklab, var(--signal-yellow) 14%, var(--card)); border-color: color-mix(in oklab, var(--signal-yellow) 30%, transparent); }
.tile.warn .tv { color: color-mix(in oklab, var(--signal-yellow) 70%, var(--foreground)); }
.tile.danger { background: color-mix(in oklab, var(--destructive) 12%, var(--card)); border-color: color-mix(in oklab, var(--destructive) 28%, transparent); }
.tile.danger .tv { color: var(--destructive); }
.tile.sel.warn, .tile.sel.danger { border-color: var(--primary); }

/* health pill */
.health-foot { padding-top: 16px; }
.ok-pill, .warn-pill { display: inline-flex; align-items: center; gap: 7px; font-size: 12.5px; font-weight: 600; border-radius: 999px; padding: 5px 11px; white-space: nowrap; }
.ok-pill { color: var(--signal-green); background: color-mix(in oklab, var(--signal-green) 14%, var(--card)); }
.warn-pill { color: var(--signal-yellow); background: color-mix(in oklab, var(--signal-yellow) 16%, var(--card)); }

/* donut */
.donut-wrap { display: flex; align-items: center; gap: 22px; flex: 1; }
.donut { position: relative; flex: none; }
.donut-center { position: absolute; inset: 0; display: grid; place-content: center; text-align: center; }
.donut-center .ct-num { font-size: 28px; font-weight: 650; line-height: 1; color: var(--foreground); }
.donut-center .ct-lbl { font-size: 11px; color: var(--muted-foreground); margin-top: 3px; }
.legend { display: flex; flex-direction: column; gap: 12px; flex: 1; }
.legend .lrow { display: flex; align-items: center; gap: 10px; }
.legend .ld { width: 10px; height: 10px; border-radius: 3px; flex: none; }
.legend .lname { font-size: 13.5px; color: var(--muted-foreground); white-space: nowrap; }
.legend .lval { margin-left: auto; font-family: var(--font-mono, ui-monospace, monospace); font-size: 13.5px; color: var(--foreground); font-weight: 600; }
.legend .lpct { font-family: var(--font-mono, ui-monospace, monospace); font-size: 11.5px; color: var(--muted-foreground); width: 42px; text-align: right; }

/* funnel */
.funnel { padding-top: 16px; }
.funnel .fbar { height: 8px; border-radius: 4px; background: var(--muted); overflow: hidden; }
.funnel .fbar > i { display: block; height: 100%; background: var(--primary); border-radius: 4px; }
.funnel .fcap { display: flex; justify-content: space-between; font-size: 11.5px; color: var(--muted-foreground); margin-top: 7px; font-family: var(--font-mono, ui-monospace, monospace); }

.chart-cap { font-size: 11.5px; color: var(--muted-foreground); margin: 14px 0 6px; font-weight: 600; }

/* generic detail list */
.dlist { display: flex; flex-direction: column; margin-top: 14px; flex: 1; }
.dlrow { display: flex; align-items: center; gap: 8px; padding: 7px 2px; border-bottom: 1px solid var(--border); font-size: 13px; }
.dlrow:last-child { border-bottom: 0; }
.dl-code { font-family: var(--font-mono, ui-monospace, monospace); font-size: 12px; color: var(--foreground); flex: none; }
.dl-mut { color: var(--muted-foreground); }
.dl-time { margin-left: auto; font-family: var(--font-mono, ui-monospace, monospace); font-size: 11.5px; color: var(--muted-foreground); white-space: nowrap; flex: none; }
.dl-empty { padding: 16px; text-align: center; color: var(--muted-foreground); font-size: 13px; }
.dl-eye { flex: none; display: inline-flex; align-items: center; justify-content: center; height: 22px; width: 22px;
  border-radius: 6px; border: 0; background: transparent; color: var(--muted-foreground); cursor: pointer; transition: background .13s, color .13s; }
.dl-eye:hover { background: var(--muted); color: var(--foreground); }

/* health-row detail drawer */
.hd-grid { display: grid; grid-template-columns: max-content 1fr; gap: 10px 16px; align-items: baseline; font-size: 13px; }
.hd-label { color: var(--muted-foreground); white-space: nowrap; }
.hd-val { color: var(--foreground); word-break: break-word; }
.hd-val.mono { font-family: var(--font-mono, ui-monospace, monospace); font-size: 12.5px; }
.hd-pre { grid-column: 1 / -1; margin: 0; padding: 10px 12px; border-radius: 8px; background: var(--muted);
  font-family: var(--font-mono, ui-monospace, monospace); font-size: 12px; white-space: pre-wrap; word-break: break-word;
  max-height: 40vh; overflow: auto; color: color-mix(in oklab, var(--destructive) 80%, var(--foreground)); }
.trunc { min-width: 0; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
.state-pill { font-size: 10px; font-weight: 700; padding: 1px 6px; border-radius: 999px; border: 1px solid; white-space: nowrap; flex: none; }
.state-pill.failed { color: var(--destructive); border-color: color-mix(in oklab, var(--destructive) 40%, transparent); }
.state-pill.pending { color: var(--signal-yellow); border-color: color-mix(in oklab, var(--signal-yellow) 45%, transparent); }

/* audit list */
.aud { display: flex; flex-direction: column; flex: 1; }
.aud-row { display: flex; align-items: center; gap: 11px; padding: 9px 2px; border-bottom: 1px solid var(--border); }
.aud-row:last-child { border-bottom: 0; }
.aud-sev { width: 7px; height: 7px; border-radius: 50%; flex: none; background: var(--muted-foreground); }
.aud-sev.high { background: var(--destructive); }
.aud-sev.mid { background: var(--signal-yellow); }
.aud-badge { width: 28px; height: 28px; border-radius: 8px; display: grid; place-items: center; flex: none; font-weight: 600; font-size: 13px; }
.aud-main { min-width: 0; flex: 1; font-size: 13px; display: flex; align-items: center; gap: 7px; flex-wrap: wrap; }
.aud-actor { font-family: var(--font-mono, ui-monospace, monospace); font-weight: 600; color: var(--foreground); white-space: nowrap; }
.aud-arrow { color: var(--muted-foreground); }
.aud-target { font-family: var(--font-mono, ui-monospace, monospace); color: var(--muted-foreground); }
.aud-code { font-family: var(--font-mono, ui-monospace, monospace); font-style: italic; color: var(--muted-foreground); font-size: 12px; opacity: .85; max-width: 16ch; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
.aud-time { margin-left: auto; font-family: var(--font-mono, ui-monospace, monospace); font-size: 11.5px; color: var(--muted-foreground); white-space: nowrap; }
</style>
