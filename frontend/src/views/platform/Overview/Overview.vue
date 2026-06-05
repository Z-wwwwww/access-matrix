<script setup>
import { onMounted, onBeforeUnmount, reactive, ref, computed } from 'vue'
import { useI18n } from 'vue-i18n'
import VChart from 'vue-echarts'
import { use } from 'echarts/core'
import { CanvasRenderer } from 'echarts/renderers'
import { PieChart, LineChart } from 'echarts/charts'
import { TooltipComponent, LegendComponent, GridComponent } from 'echarts/components'
import Card from '@/components/ui/Card.vue'
import { toast } from '@/composables/useToast'
import { Building2, TrendingUp, CheckCircle2, PauseCircle } from 'lucide-vue-next'
import { getTenantStatsApi } from '@/services/tenant'
import DashboardPanels from '../Tenant/DashboardPanels.vue'

// First echarts use in the codebase — register only the pieces these two charts
// need (tree-shaken canvas renderer + pie/line + tooltip/legend/grid).
use([CanvasRenderer, PieChart, LineChart, TooltipComponent, LegendComponent, GridComponent])

const { t } = useI18n()

// ── Dashboard stats ──────────────────────────────────────────────────────
const stats = reactive({ total: 0, active: 0, suspended: 0, newThisMonth: 0, monthly: [] })
const statsLoaded = ref(false)

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
    toast.error(e.message)
  }
}

onMounted(() => {
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
  </div>
</template>
