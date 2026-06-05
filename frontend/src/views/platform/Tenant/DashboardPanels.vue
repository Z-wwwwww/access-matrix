<script setup>
import { onMounted, reactive, ref, computed } from 'vue'
import { useI18n } from 'vue-i18n'
import VChart from 'vue-echarts'
import { use } from 'echarts/core'
import { CanvasRenderer } from 'echarts/renderers'
import { LineChart } from 'echarts/charts'
import { TooltipComponent, GridComponent } from 'echarts/components'
import Card from '@/components/ui/Card.vue'
import Badge from '@/components/ui/Badge.vue'
import { toast } from '@/composables/useToast'
import { toJSTDateTimeDisp } from '@/lib/date'
import { getPlatformDashboardApi } from '@/services/tenant'
import {
  UserCheck, Activity, ServerCog, ShieldAlert,
  Clock, AlarmClockOff, KeyRound, Flame
} from 'lucide-vue-next'

use([CanvasRenderer, LineChart, TooltipComponent, GridComponent])

const { t } = useI18n()

const loaded = ref(false)
const data = reactive({
  activation: null,
  engagement: null,
  reliability: null,
  security: null
})

// Selectable detail cards: which metric's detail list is shown, per panel.
// Defaults (set in fetchDashboard) to the first card that has a value, so the
// panel always shows the most relevant detail without a click. Clicking just
// switches — there's always exactly one selected.
const sel = reactive({ act: 'pending', rel: 'jobs', sec: 'support' })
function pick(group, key) {
  sel[group] = key
}
// First key whose value is > 0, else the first key — used to pick the default card.
function firstWithValue(candidates) {
  const hit = candidates.find(([, v]) => (v || 0) > 0)
  return (hit || candidates[0])[0]
}
function cardCls(group, key) {
  // All cards have a background; the selected one gets a stronger tint + a
  // primary border. Unselected keeps a transparent border so selecting never
  // shifts layout by a pixel.
  return sel[group] === key
    ? 'bg-primary/10 border-primary text-foreground'
    : 'bg-muted/40 border-transparent hover:bg-muted/70'
}
// Activation detail list for the selected card.
const actList = computed(() => {
  if (!data.activation) return null
  if (sel.act === 'pending') return data.activation.pending
  if (sel.act === 'expired') return data.activation.expired
  return null
})

function cssVar(name) {
  return getComputedStyle(document.documentElement).getPropertyValue(name).trim()
}

// ── helpers ────────────────────────────────────────────────────────────────
function pct(n) {
  return `${Math.round((n || 0) * 100)}%`
}

// Median onboarding lead-time: backend sends hours; show the most natural unit.
function fmtHours(h) {
  if (h == null) return '—'
  if (h < 1) return `${Math.round(h * 60)}m`
  if (h < 48) return `${Math.round(h)}h`
  return `${Math.round(h / 24)}d`
}

// Outbox backlog age: backend sends minutes.
function fmtMinutes(m) {
  if (m == null) return '—'
  if (m < 60) return `${m}m`
  if (m < 1440) return `${Math.round(m / 60)}h`
  return `${Math.round(m / 1440)}d`
}

function fmtDate(s) {
  return s ? toJSTDateTimeDisp(s) : '—'
}

// Support-session reason rides in oplog.request_body as JSON ({"reason":"..."}).
// Parse defensively; fall back to the raw string.
function reasonOf(raw) {
  if (!raw) return '—'
  try {
    const o = JSON.parse(raw)
    return o.reason || raw
  } catch {
    return raw
  }
}

// Tint a metric red/amber when it represents something that needs attention.
function alertClass(n, warn = 1) {
  if (!n) return 'text-foreground'
  return n >= warn ? 'text-destructive' : 'text-amber-600'
}

const loginTrendOption = computed(() => {
  const series = data.engagement?.loginTrend || []
  const textColor = cssVar('--muted-foreground')
  const gridColor = cssVar('--border')
  const accent = cssVar('--primary')
  return {
    tooltip: { trigger: 'axis' },
    grid: { left: 8, right: 12, top: 12, bottom: 4, containLabel: true },
    xAxis: {
      type: 'category',
      boundaryGap: false,
      data: series.map((d) => d.day.slice(5)),   // 'MM-DD'
      axisLabel: { color: textColor, fontSize: 10 },
      axisLine: { lineStyle: { color: gridColor } }
    },
    yAxis: {
      type: 'value', minInterval: 1,
      axisLabel: { color: textColor, fontSize: 10 },
      splitLine: { lineStyle: { color: gridColor } }
    },
    series: [{
      type: 'line', smooth: true, showSymbol: false,
      name: t('platform.tenant.ops.engagement.trendTitle'),
      data: series.map((d) => d.count),
      itemStyle: { color: accent },
      lineStyle: { color: accent },
      areaStyle: { color: accent, opacity: 0.12 }
    }]
  }
})

async function fetchDashboard() {
  try {
    const res = await getPlatformDashboardApi()
    if (res.data.code === 0) {
      const d = res.data.data || {}
      // Be resilient to backend version skew: default every rendered list to []
      // so a not-yet-deployed field (e.g. backlogEvents) can never throw on
      // `.length` and silently break card selection.
      const A = d.activation || {};  A.pending ||= [];           A.expired ||= []
      const E = d.engagement || {};  E.silentTenants ||= [];     E.loginTrend ||= []
      const R = d.reliability || {}; R.recentJobFailures ||= []; R.recentOplogErrors ||= []; R.backlogEvents ||= []
      const S = d.security || {};    S.recentSupportSessions ||= []; S.recentBreakGlass ||= []
      data.activation = A
      data.engagement = E
      data.reliability = R
      data.security = S
      // Default-select the first card that has a value (else the first card).
      sel.act = firstWithValue([
        ['pending', d.activation?.pendingTenants],
        ['expired', d.activation?.expiredUnactivated]
      ])
      sel.rel = firstWithValue([
        ['jobs', d.reliability?.jobFailures24h],
        ['backlog', (d.reliability?.eventPending || 0) + (d.reliability?.eventFailed || 0)],
        ['oldest', d.reliability?.eventBacklogOldestMin],
        ['errors', d.reliability?.oplogErrors24h]
      ])
      sel.sec = firstWithValue([
        ['support', d.security?.activeSupportSessions],
        ['support7d', d.security?.supportSessions7d],
        ['breakglass', d.security?.breakGlass7d]
      ])
      loaded.value = true
    } else {
      toast.error(res.data.msg)
    }
  } catch (e) {
    toast.error(e.message)
  }
}

onMounted(fetchDashboard)
</script>

<template>
  <div v-if="loaded" class="grid grid-cols-1 xl:grid-cols-2 gap-3">
    <!-- ── 2. Activation funnel ─────────────────────────────────────── -->
    <Card class="p-4">
      <div class="flex items-center gap-2 mb-3">
        <UserCheck class="size-4 text-primary" />
        <span class="text-sm font-medium">{{ t('platform.tenant.ops.activation.title') }}</span>
      </div>
      <div class="grid grid-cols-4 gap-2 mb-3">
        <button type="button" class="rounded-lg border p-2 text-center cursor-pointer transition-colors"
                :class="cardCls('act','pending')" @click="pick('act','pending')"
                :title="t('platform.tenant.ops.activation.tip.pending')">
          <div class="text-xl font-semibold" :class="alertClass(data.activation.pendingTenants)">{{ data.activation.pendingTenants }}</div>
          <div class="text-[11px] text-muted-foreground">{{ t('platform.tenant.ops.activation.pending') }}</div>
        </button>
        <button type="button" class="rounded-lg border p-2 text-center cursor-pointer transition-colors"
                :class="cardCls('act','expired')" @click="pick('act','expired')"
                :title="t('platform.tenant.ops.activation.tip.expired')">
          <div class="text-xl font-semibold" :class="alertClass(data.activation.expiredUnactivated)">{{ data.activation.expiredUnactivated }}</div>
          <div class="text-[11px] text-muted-foreground">{{ t('platform.tenant.ops.activation.expired') }}</div>
        </button>
        <div class="p-2 text-center" :title="t('platform.tenant.ops.activation.tip.rate')">
          <div class="text-xl font-semibold">{{ pct(data.activation.activationRate) }}</div>
          <div class="text-[11px] text-muted-foreground">{{ t('platform.tenant.ops.activation.rate') }}</div>
        </div>
        <div class="p-2 text-center" :title="t('platform.tenant.ops.activation.tip.median')">
          <div class="text-xl font-semibold">{{ fmtHours(data.activation.medianOnboardingHours) }}</div>
          <div class="text-[11px] text-muted-foreground">{{ t('platform.tenant.ops.activation.median') }}</div>
        </div>
      </div>

      <!-- detail for the selected card -->
      <template v-if="actList">
        <div v-if="actList.length" class="divide-y divide-border/60">
          <div v-for="p in actList" :key="p.tenantId"
               class="py-1.5 flex items-center gap-2 text-sm">
            <span class="font-mono text-xs shrink-0">{{ p.tenantCode }}</span>
            <span class="truncate text-muted-foreground">{{ p.contactEmail || '—' }}</span>
            <span class="ml-auto shrink-0 text-xs" :class="p.expired ? 'text-destructive' : 'text-muted-foreground'">
              <Clock class="size-3 inline -mt-0.5" /> {{ fmtDate(p.expiresAt) }}
            </span>
          </div>
        </div>
        <div v-else class="py-3 text-center text-xs text-muted-foreground">{{ t('platform.tenant.ops.empty') }}</div>
      </template>
    </Card>

    <!-- ── 3. Engagement ────────────────────────────────────────────── -->
    <Card class="p-4">
      <div class="flex items-center gap-2 mb-3">
        <Activity class="size-4 text-primary" />
        <span class="text-sm font-medium">{{ t('platform.tenant.ops.engagement.title') }}</span>
      </div>
      <div class="grid grid-cols-5 gap-2 mb-3">
        <div class="text-center" :title="t('platform.tenant.ops.engagement.tip.active7d')">
          <div class="text-xl font-semibold">{{ data.engagement.activeTenants7d }}</div>
          <div class="text-[11px] text-muted-foreground">{{ t('platform.tenant.ops.engagement.active7d') }}</div>
        </div>
        <div class="text-center" :title="t('platform.tenant.ops.engagement.tip.active30d')">
          <div class="text-xl font-semibold">{{ data.engagement.activeTenants30d }}</div>
          <div class="text-[11px] text-muted-foreground">{{ t('platform.tenant.ops.engagement.active30d') }}</div>
        </div>
        <div class="text-center" :title="t('platform.tenant.ops.engagement.tip.dau')">
          <div class="text-xl font-semibold">{{ data.engagement.dau }}</div>
          <div class="text-[11px] text-muted-foreground">{{ t('platform.tenant.ops.engagement.dau') }}</div>
        </div>
        <div class="text-center" :title="t('platform.tenant.ops.engagement.tip.mau')">
          <div class="text-xl font-semibold">{{ data.engagement.mau }}</div>
          <div class="text-[11px] text-muted-foreground">{{ t('platform.tenant.ops.engagement.mau') }}</div>
        </div>
        <div class="text-center" :title="t('platform.tenant.ops.engagement.tip.silent')">
          <div class="text-xl font-semibold" :class="alertClass(data.engagement.silentTenantsCount)">{{ data.engagement.silentTenantsCount }}</div>
          <div class="text-[11px] text-muted-foreground">{{ t('platform.tenant.ops.engagement.silent') }}</div>
        </div>
      </div>
      <VChart :option="loginTrendOption" autoresize style="height: 120px" class="mb-2" />
      <div class="text-[11px] text-muted-foreground mb-1 flex items-center gap-1">
        <AlarmClockOff class="size-3.5" /> {{ t('platform.tenant.ops.engagement.silentListTitle') }}
      </div>
      <div v-if="data.engagement.silentTenants.length" class="divide-y divide-border/60">
        <div v-for="s in data.engagement.silentTenants" :key="s.tenantId"
             class="py-1.5 flex items-center gap-2 text-sm">
          <span class="font-mono text-xs shrink-0">{{ s.tenantCode }}</span>
          <span class="truncate text-muted-foreground">{{ s.displayName }}</span>
          <span class="ml-auto shrink-0 text-xs text-muted-foreground">
            {{ s.lastLoginAt ? fmtDate(s.lastLoginAt) : t('platform.tenant.ops.engagement.never') }}
          </span>
        </div>
      </div>
      <div v-else class="py-3 text-center text-xs text-muted-foreground">{{ t('platform.tenant.ops.empty') }}</div>
    </Card>

    <!-- ── 4. Reliability ───────────────────────────────────────────── -->
    <Card class="p-4">
      <div class="flex items-center gap-2 mb-3">
        <ServerCog class="size-4 text-primary" />
        <span class="text-sm font-medium">{{ t('platform.tenant.ops.reliability.title') }}</span>
      </div>
      <div class="grid grid-cols-4 gap-2 mb-3">
        <button type="button" class="rounded-lg border p-2 text-center cursor-pointer transition-colors"
                :class="cardCls('rel','jobs')" @click="pick('rel','jobs')"
                :title="t('platform.tenant.ops.reliability.tip.jobFailures')">
          <div class="text-xl font-semibold" :class="alertClass(data.reliability.jobFailures24h)">{{ data.reliability.jobFailures24h }}</div>
          <div class="text-[11px] text-muted-foreground">{{ t('platform.tenant.ops.reliability.jobFailures') }}</div>
        </button>
        <button type="button" class="rounded-lg border p-2 text-center cursor-pointer transition-colors"
                :class="cardCls('rel','backlog')" @click="pick('rel','backlog')"
                :title="t('platform.tenant.ops.reliability.tip.eventBacklog')">
          <div class="text-xl font-semibold" :class="alertClass(data.reliability.eventPending + data.reliability.eventFailed)">
            {{ data.reliability.eventPending + data.reliability.eventFailed }}
          </div>
          <div class="text-[11px] text-muted-foreground">{{ t('platform.tenant.ops.reliability.eventBacklog') }}</div>
        </button>
        <button type="button" class="rounded-lg border p-2 text-center cursor-pointer transition-colors"
                :class="cardCls('rel','oldest')" @click="pick('rel','oldest')"
                :title="t('platform.tenant.ops.reliability.tip.backlogAge')">
          <div class="text-xl font-semibold">{{ fmtMinutes(data.reliability.eventBacklogOldestMin) }}</div>
          <div class="text-[11px] text-muted-foreground">{{ t('platform.tenant.ops.reliability.backlogAge') }}</div>
        </button>
        <button type="button" class="rounded-lg border p-2 text-center cursor-pointer transition-colors"
                :class="cardCls('rel','errors')" @click="pick('rel','errors')"
                :title="t('platform.tenant.ops.reliability.tip.oplogErrors')">
          <div class="text-xl font-semibold" :class="alertClass(data.reliability.oplogErrors24h)">{{ data.reliability.oplogErrors24h }}</div>
          <div class="text-[11px] text-muted-foreground">{{ t('platform.tenant.ops.reliability.oplogErrors') }}</div>
        </button>
      </div>

      <!-- job failures -->
      <template v-if="sel.rel === 'jobs'">
        <div v-if="data.reliability.recentJobFailures.length" class="divide-y divide-border/60">
          <div v-for="(f, i) in data.reliability.recentJobFailures" :key="i"
               class="py-1.5 flex items-center gap-2 text-sm">
            <span class="font-mono text-xs shrink-0">{{ f.jobCode }}</span>
            <span class="truncate text-destructive/80" :title="f.error">{{ f.error || '—' }}</span>
            <span class="ml-auto shrink-0 text-xs text-muted-foreground">{{ fmtDate(f.startTime) }}</span>
          </div>
        </div>
        <div v-else class="py-3 text-center text-xs text-muted-foreground">{{ t('platform.tenant.ops.empty') }}</div>
      </template>
      <!-- event backlog + oldest backlog share the same undispatched-event list -->
      <template v-else-if="sel.rel === 'backlog' || sel.rel === 'oldest'">
        <div v-if="data.reliability.backlogEvents.length" class="divide-y divide-border/60">
          <div v-for="(b, i) in data.reliability.backlogEvents" :key="i"
               class="py-1.5 flex items-center gap-2 text-sm">
            <span class="font-mono text-xs shrink-0 truncate">{{ b.eventType }}</span>
            <Badge variant="outline" class="text-[10px] shrink-0"
                   :class="b.dispatchState === 2 ? 'border-destructive/40 text-destructive' : 'border-amber-500/40 text-amber-600'">
              {{ b.dispatchState === 2 ? t('platform.tenant.ops.reliability.stateFailed') : t('platform.tenant.ops.reliability.statePending') }}
            </Badge>
            <span class="shrink-0 text-xs text-muted-foreground">×{{ b.attempts }}</span>
            <span class="ml-auto shrink-0 text-xs text-muted-foreground">{{ fmtDate(b.occurredAt) }}</span>
          </div>
        </div>
        <div v-else class="py-3 text-center text-xs text-muted-foreground">{{ t('platform.tenant.ops.empty') }}</div>
      </template>
      <!-- API errors -->
      <template v-else-if="sel.rel === 'errors'">
        <div v-if="data.reliability.recentOplogErrors.length" class="divide-y divide-border/60">
          <div v-for="(e, i) in data.reliability.recentOplogErrors" :key="i"
               class="py-1.5 flex items-center gap-2 text-sm">
            <span class="font-mono text-xs shrink-0">{{ e.module }}.{{ e.action }}</span>
            <span class="truncate text-destructive/80" :title="e.errorMsg">{{ e.errorMsg || '—' }}</span>
            <span class="shrink-0 text-xs text-muted-foreground">{{ e.username || '—' }}</span>
            <span class="ml-auto shrink-0 text-xs text-muted-foreground">{{ fmtDate(e.time) }}</span>
          </div>
        </div>
        <div v-else class="py-3 text-center text-xs text-muted-foreground">{{ t('platform.tenant.ops.empty') }}</div>
      </template>
    </Card>

    <!-- ── 5. Security & privileged access ──────────────────────────── -->
    <Card class="p-4">
      <div class="flex items-center gap-2 mb-3">
        <ShieldAlert class="size-4 text-primary" />
        <span class="text-sm font-medium">{{ t('platform.tenant.ops.security.title') }}</span>
      </div>
      <div class="grid grid-cols-5 gap-2 mb-3">
        <button type="button" class="rounded-lg border p-2 text-center cursor-pointer transition-colors"
                :class="cardCls('sec','support')" @click="pick('sec','support')">
          <div class="text-xl font-semibold" :class="data.security.activeSupportSessions ? 'text-amber-600' : 'text-foreground'">
            {{ data.security.activeSupportSessions }}
          </div>
          <div class="text-[11px] text-muted-foreground">{{ t('platform.tenant.ops.security.activeSupport') }}</div>
        </button>
        <button type="button" class="rounded-lg border p-2 text-center cursor-pointer transition-colors"
                :class="cardCls('sec','support7d')" @click="pick('sec','support7d')">
          <div class="text-xl font-semibold">{{ data.security.supportSessions7d }}</div>
          <div class="text-[11px] text-muted-foreground">{{ t('platform.tenant.ops.security.support7d') }}</div>
        </button>
        <button type="button" class="rounded-lg border p-2 text-center cursor-pointer transition-colors"
                :class="cardCls('sec','breakglass')" @click="pick('sec','breakglass')">
          <div class="text-xl font-semibold" :class="alertClass(data.security.breakGlass7d)">{{ data.security.breakGlass7d }}</div>
          <div class="text-[11px] text-muted-foreground">{{ t('platform.tenant.ops.security.breakGlass') }}</div>
        </button>
        <div class="p-2 text-center">
          <div class="text-xl font-semibold" :class="alertClass(data.security.loginFailures24h, 10)">{{ data.security.loginFailures24h }}</div>
          <div class="text-[11px] text-muted-foreground">{{ t('platform.tenant.ops.security.loginFailures') }}</div>
        </div>
        <div class="p-2 text-center">
          <div class="text-xl font-semibold">{{ data.security.passwordResets7d }}</div>
          <div class="text-[11px] text-muted-foreground">{{ t('platform.tenant.ops.security.passwordResets') }}</div>
        </div>
      </div>

      <!-- support sessions (active / 7d share this list) -->
      <template v-if="sel.sec === 'support' || sel.sec === 'support7d'">
        <div v-if="data.security.recentSupportSessions.length" class="divide-y divide-border/60">
          <div v-for="(s, i) in data.security.recentSupportSessions" :key="i"
               class="py-1.5 flex items-center gap-2 text-sm">
            <span class="size-2 rounded-full shrink-0"
                  :class="s.active ? 'bg-emerald-500' : 'bg-muted-foreground/30'" />
            <KeyRound class="size-3.5 text-amber-600 shrink-0" />
            <span class="shrink-0 font-medium">{{ s.operator }}</span>
            <span class="text-muted-foreground shrink-0">→</span>
            <span class="font-mono text-xs shrink-0">{{ s.targetTenantCode || '—' }}</span>
            <span class="truncate text-muted-foreground italic">{{ reasonOf(s.reason) }}</span>
            <span class="ml-auto shrink-0 text-xs text-muted-foreground">{{ fmtDate(s.startedAt) }}</span>
          </div>
        </div>
        <div v-else class="py-3 text-center text-xs text-muted-foreground">{{ t('platform.tenant.ops.empty') }}</div>
      </template>
      <!-- break-glass -->
      <template v-else-if="sel.sec === 'breakglass'">
        <div v-if="data.security.recentBreakGlass.length" class="divide-y divide-border/60">
          <div v-for="(b, i) in data.security.recentBreakGlass" :key="i"
               class="py-1.5 flex items-center gap-2 text-sm">
            <Flame class="size-3.5 text-destructive shrink-0" />
            <span class="shrink-0 font-medium">{{ b.operator }}</span>
            <span class="font-mono text-xs shrink-0">{{ b.tenantCode }}</span>
            <span class="truncate text-muted-foreground">{{ b.clientIp || '—' }}</span>
            <span class="ml-auto shrink-0 text-xs text-muted-foreground">{{ fmtDate(b.usedAt) }}</span>
          </div>
        </div>
        <div v-else class="py-3 text-center text-xs text-muted-foreground">{{ t('platform.tenant.ops.empty') }}</div>
      </template>
    </Card>
  </div>

  <!-- loading skeleton -->
  <div v-else class="grid grid-cols-1 xl:grid-cols-2 gap-3">
    <div v-for="i in 4" :key="i" class="h-56 animate-pulse bg-muted/40 rounded-xl" />
  </div>
</template>
