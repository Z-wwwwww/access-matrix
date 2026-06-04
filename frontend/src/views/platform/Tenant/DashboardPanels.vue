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
  Clock, MailWarning, Inbox, AlarmClockOff, LifeBuoy, KeyRound, Flame
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
      data.activation = d.activation
      data.engagement = d.engagement
      data.reliability = d.reliability
      data.security = d.security
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
        <div class="text-center">
          <div class="text-xl font-semibold" :class="alertClass(data.activation.pendingTenants)">{{ data.activation.pendingTenants }}</div>
          <div class="text-[11px] text-muted-foreground">{{ t('platform.tenant.ops.activation.pending') }}</div>
        </div>
        <div class="text-center">
          <div class="text-xl font-semibold" :class="alertClass(data.activation.expiredUnactivated)">{{ data.activation.expiredUnactivated }}</div>
          <div class="text-[11px] text-muted-foreground">{{ t('platform.tenant.ops.activation.expired') }}</div>
        </div>
        <div class="text-center">
          <div class="text-xl font-semibold">{{ pct(data.activation.activationRate) }}</div>
          <div class="text-[11px] text-muted-foreground">{{ t('platform.tenant.ops.activation.rate') }}</div>
        </div>
        <div class="text-center">
          <div class="text-xl font-semibold">{{ fmtHours(data.activation.medianOnboardingHours) }}</div>
          <div class="text-[11px] text-muted-foreground">{{ t('platform.tenant.ops.activation.median') }}</div>
        </div>
      </div>
      <div class="text-[11px] text-muted-foreground mb-1 flex items-center gap-1">
        <MailWarning class="size-3.5" /> {{ t('platform.tenant.ops.activation.listTitle') }}
      </div>
      <div v-if="data.activation.pending.length" class="divide-y divide-border/60">
        <div v-for="p in data.activation.pending" :key="p.tenantId"
             class="py-1.5 flex items-center gap-2 text-sm">
          <span class="font-mono text-xs shrink-0">{{ p.tenantCode }}</span>
          <span class="truncate text-muted-foreground">{{ p.contactEmail || '—' }}</span>
          <span class="ml-auto shrink-0 text-xs" :class="p.expired ? 'text-destructive' : 'text-muted-foreground'">
            <Clock class="size-3 inline -mt-0.5" /> {{ fmtDate(p.expiresAt) }}
            <Badge v-if="p.expired" variant="outline" class="ml-1 text-[10px] border-destructive/40 text-destructive">
              {{ t('platform.tenant.ops.activation.expiredBadge') }}
            </Badge>
          </span>
        </div>
      </div>
      <div v-else class="py-3 text-center text-xs text-muted-foreground">{{ t('platform.tenant.ops.empty') }}</div>
    </Card>

    <!-- ── 3. Engagement ────────────────────────────────────────────── -->
    <Card class="p-4">
      <div class="flex items-center gap-2 mb-3">
        <Activity class="size-4 text-primary" />
        <span class="text-sm font-medium">{{ t('platform.tenant.ops.engagement.title') }}</span>
      </div>
      <div class="grid grid-cols-5 gap-2 mb-3">
        <div class="text-center">
          <div class="text-xl font-semibold">{{ data.engagement.activeTenants7d }}</div>
          <div class="text-[11px] text-muted-foreground">{{ t('platform.tenant.ops.engagement.active7d') }}</div>
        </div>
        <div class="text-center">
          <div class="text-xl font-semibold">{{ data.engagement.activeTenants30d }}</div>
          <div class="text-[11px] text-muted-foreground">{{ t('platform.tenant.ops.engagement.active30d') }}</div>
        </div>
        <div class="text-center">
          <div class="text-xl font-semibold">{{ data.engagement.dau }}</div>
          <div class="text-[11px] text-muted-foreground">{{ t('platform.tenant.ops.engagement.dau') }}</div>
        </div>
        <div class="text-center">
          <div class="text-xl font-semibold">{{ data.engagement.mau }}</div>
          <div class="text-[11px] text-muted-foreground">{{ t('platform.tenant.ops.engagement.mau') }}</div>
        </div>
        <div class="text-center">
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
        <div class="text-center">
          <div class="text-xl font-semibold" :class="alertClass(data.reliability.jobFailures24h)">{{ data.reliability.jobFailures24h }}</div>
          <div class="text-[11px] text-muted-foreground">{{ t('platform.tenant.ops.reliability.jobFailures') }}</div>
        </div>
        <div class="text-center">
          <div class="text-xl font-semibold" :class="alertClass(data.reliability.eventPending + data.reliability.eventFailed)">
            {{ data.reliability.eventPending + data.reliability.eventFailed }}
          </div>
          <div class="text-[11px] text-muted-foreground">{{ t('platform.tenant.ops.reliability.eventBacklog') }}</div>
        </div>
        <div class="text-center">
          <div class="text-xl font-semibold">{{ fmtMinutes(data.reliability.eventBacklogOldestMin) }}</div>
          <div class="text-[11px] text-muted-foreground">{{ t('platform.tenant.ops.reliability.backlogAge') }}</div>
        </div>
        <div class="text-center">
          <div class="text-xl font-semibold" :class="alertClass(data.reliability.oplogErrors24h)">{{ data.reliability.oplogErrors24h }}</div>
          <div class="text-[11px] text-muted-foreground">{{ t('platform.tenant.ops.reliability.oplogErrors') }}</div>
        </div>
      </div>
      <div class="text-[11px] text-muted-foreground mb-1 flex items-center gap-1">
        <Inbox class="size-3.5" /> {{ t('platform.tenant.ops.reliability.failuresListTitle') }}
      </div>
      <div v-if="data.reliability.recentJobFailures.length" class="divide-y divide-border/60">
        <div v-for="(f, i) in data.reliability.recentJobFailures" :key="i"
             class="py-1.5 flex items-center gap-2 text-sm">
          <span class="font-mono text-xs shrink-0">{{ f.jobCode }}</span>
          <span class="truncate text-destructive/80" :title="f.error">{{ f.error || '—' }}</span>
          <span class="ml-auto shrink-0 text-xs text-muted-foreground">{{ fmtDate(f.startTime) }}</span>
        </div>
      </div>
      <div v-else class="py-3 text-center text-xs text-muted-foreground">{{ t('platform.tenant.ops.empty') }}</div>
    </Card>

    <!-- ── 5. Security & privileged access ──────────────────────────── -->
    <Card class="p-4">
      <div class="flex items-center gap-2 mb-3">
        <ShieldAlert class="size-4 text-primary" />
        <span class="text-sm font-medium">{{ t('platform.tenant.ops.security.title') }}</span>
      </div>
      <div class="grid grid-cols-5 gap-2 mb-3">
        <div class="text-center">
          <div class="text-xl font-semibold" :class="data.security.activeSupportSessions ? 'text-amber-600' : 'text-foreground'">
            {{ data.security.activeSupportSessions }}
          </div>
          <div class="text-[11px] text-muted-foreground">{{ t('platform.tenant.ops.security.activeSupport') }}</div>
        </div>
        <div class="text-center">
          <div class="text-xl font-semibold">{{ data.security.supportSessions7d }}</div>
          <div class="text-[11px] text-muted-foreground">{{ t('platform.tenant.ops.security.support7d') }}</div>
        </div>
        <div class="text-center">
          <div class="text-xl font-semibold" :class="alertClass(data.security.breakGlass7d)">{{ data.security.breakGlass7d }}</div>
          <div class="text-[11px] text-muted-foreground">{{ t('platform.tenant.ops.security.breakGlass') }}</div>
        </div>
        <div class="text-center">
          <div class="text-xl font-semibold" :class="alertClass(data.security.loginFailures24h, 10)">{{ data.security.loginFailures24h }}</div>
          <div class="text-[11px] text-muted-foreground">{{ t('platform.tenant.ops.security.loginFailures') }}</div>
        </div>
        <div class="text-center">
          <div class="text-xl font-semibold">{{ data.security.passwordResets7d }}</div>
          <div class="text-[11px] text-muted-foreground">{{ t('platform.tenant.ops.security.passwordResets') }}</div>
        </div>
      </div>
      <div class="text-[11px] text-muted-foreground mb-1 flex items-center gap-1">
        <LifeBuoy class="size-3.5" /> {{ t('platform.tenant.ops.security.supportListTitle') }}
      </div>
      <div v-if="data.security.recentSupportSessions.length" class="divide-y divide-border/60">
        <div v-for="(s, i) in data.security.recentSupportSessions" :key="i"
             class="py-1.5 flex items-center gap-2 text-sm">
          <KeyRound class="size-3.5 text-amber-600 shrink-0" />
          <span class="shrink-0 font-medium">{{ s.operator }}</span>
          <span class="text-muted-foreground shrink-0">→</span>
          <span class="font-mono text-xs shrink-0">{{ s.targetTenantCode || '—' }}</span>
          <span class="truncate text-muted-foreground italic">{{ reasonOf(s.reason) }}</span>
          <span class="ml-auto shrink-0 text-xs text-muted-foreground">{{ fmtDate(s.startedAt) }}</span>
        </div>
      </div>
      <div v-else class="py-3 text-center text-xs text-muted-foreground">{{ t('platform.tenant.ops.empty') }}</div>

      <div class="text-[11px] text-muted-foreground mt-3 mb-1 flex items-center gap-1">
        <Flame class="size-3.5 text-destructive" /> {{ t('platform.tenant.ops.security.breakGlassListTitle') }}
      </div>
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
    </Card>
  </div>

  <!-- loading skeleton -->
  <div v-else class="grid grid-cols-1 xl:grid-cols-2 gap-3">
    <div v-for="i in 4" :key="i" class="h-56 animate-pulse bg-muted/40 rounded-xl" />
  </div>
</template>
