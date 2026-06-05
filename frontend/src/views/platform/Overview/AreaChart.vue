<script setup>
/**
 * Hand-built SVG area-line for the platform overview. Straight segments (no
 * spline) so near-zero integer series never overshoot into a fake hump.
 *
 * The SVG measures its own pixel width (ResizeObserver) and uses it as the
 * viewBox width, so the mapping is 1:1 — no non-uniform scaling, so axis text
 * is never stretched. Line/area inherit `currentColor` (from the `color` prop)
 * to track the active accent; grid + labels use theme tokens. Hovering shows a
 * guide line + a tooltip with the point's label and value.
 */
import { ref, computed, onMounted, onBeforeUnmount } from 'vue'

const props = defineProps({
  data: { type: Array, default: () => [] },
  labels: { type: Array, default: () => [] },
  height: { type: Number, default: 180 },
  everyX: { type: Number, default: 1 },
  color: { type: String, default: 'var(--primary)' }
})

let uidN = 0
const gid = `oac-${(uidN += 1)}`

const wrap = ref(null)
const w = ref(640)
let ro = null
onMounted(() => {
  ro = new ResizeObserver((entries) => {
    const cw = entries[0]?.contentRect?.width
    if (cw && cw > 0) w.value = cw
  })
  if (wrap.value) ro.observe(wrap.value)
})
onBeforeUnmount(() => ro?.disconnect())

const PAD = { l: 26, r: 14, t: 14, b: 22 }
const W = computed(() => w.value || 640)
const n = computed(() => props.data.length)
const max = computed(() => Math.max(...props.data, 1))
const ticks = computed(() => (max.value <= 3 ? max.value : 4))
const x = (i) => PAD.l + (i * (W.value - PAD.l - PAD.r)) / Math.max(1, n.value - 1)
const y = (v) => PAD.t + (1 - v / max.value) * (props.height - PAD.t - PAD.b)

const linePath = computed(() =>
  props.data.map((v, i) => `${i ? 'L' : 'M'} ${x(i).toFixed(1)} ${y(v).toFixed(1)}`).join(' ')
)
const areaPath = computed(() =>
  `${linePath.value} L ${x(n.value - 1).toFixed(1)} ${y(0)} L ${x(0).toFixed(1)} ${y(0)} Z`
)
const gridRows = computed(() =>
  Array.from({ length: ticks.value + 1 }, (_, i) => {
    const v = (max.value / ticks.value) * i
    return { y: y(v), label: Math.round(v) }
  })
)
const points = computed(() =>
  props.data.map((v, i) => ({ x: x(i), y: y(v), show: v > 0 || i === n.value - 1 }))
)
const xLabels = computed(() =>
  props.labels.map((l, i) => ({ x: x(i), label: l, show: i % props.everyX === 0 || i === n.value - 1 }))
)

// ── hover ────────────────────────────────────────────────────────────────
const hover = ref(-1)
function onMove(e) {
  if (!n.value) return
  const rect = e.currentTarget.getBoundingClientRect()
  const mx = ((e.clientX - rect.left) / rect.width) * W.value
  let best = 0, bd = Infinity
  for (let i = 0; i < n.value; i++) {
    const d = Math.abs(x(i) - mx)
    if (d < bd) { bd = d; best = i }
  }
  hover.value = best
}
function onLeave() { hover.value = -1 }
const tip = computed(() => {
  if (hover.value < 0) return null
  const i = hover.value
  return { x: x(i), y: y(props.data[i]), label: props.labels[i] ?? '', value: props.data[i] }
})
</script>

<template>
  <div ref="wrap" class="ac-wrap" :style="{ height: height + 'px', color }">
    <svg class="area-chart" :viewBox="`0 0 ${W} ${height}`" :width="W" :height="height"
         @mousemove="onMove" @mouseleave="onLeave">
      <defs>
        <linearGradient :id="gid" x1="0" y1="0" x2="0" y2="1">
          <stop offset="0%" stop-color="currentColor" stop-opacity="0.18" />
          <stop offset="100%" stop-color="currentColor" stop-opacity="0" />
        </linearGradient>
      </defs>
      <g v-for="(r, i) in gridRows" :key="'g' + i">
        <line class="grid-line" :x1="PAD.l" :x2="W - PAD.r" :y1="r.y" :y2="r.y" />
        <text class="yl" :x="PAD.l - 6" :y="r.y + 3" text-anchor="end">{{ r.label }}</text>
      </g>
      <path :d="areaPath" :fill="`url(#${gid})`" />
      <path :d="linePath" fill="none" stroke="currentColor" stroke-width="2.2"
            stroke-linejoin="round" stroke-linecap="round" />
      <!-- hover guide -->
      <line v-if="tip" class="guide" :x1="tip.x" :x2="tip.x" :y1="PAD.t" :y2="height - PAD.b" />
      <template v-for="(p, i) in points" :key="'p' + i">
        <circle v-if="p.show" :cx="p.x" :cy="p.y" r="3" fill="var(--card)" stroke="currentColor" stroke-width="2" />
      </template>
      <circle v-if="tip" :cx="tip.x" :cy="tip.y" r="4" fill="currentColor" stroke="var(--card)" stroke-width="2" />
      <template v-for="(l, i) in xLabels" :key="'x' + i">
        <text v-if="l.show" class="axis-lbl" :x="l.x" :y="height - 6" text-anchor="middle">{{ l.label }}</text>
      </template>
    </svg>
    <div v-if="tip" class="ac-tip" :style="{ left: tip.x + 'px', top: tip.y + 'px' }">
      <span class="ac-tip-l">{{ tip.label }}</span>
      <span class="ac-tip-v">{{ tip.value }}</span>
    </div>
  </div>
</template>

<style scoped>
.ac-wrap { position: relative; width: 100%; }
.area-chart { width: 100%; display: block; }
.area-chart .grid-line { stroke: var(--border); stroke-width: 1; }
.area-chart .guide { stroke: currentColor; stroke-width: 1; stroke-dasharray: 3 3; opacity: 0.5; }
.area-chart .axis-lbl,
.area-chart .yl { fill: var(--muted-foreground); font-family: var(--font-mono, ui-monospace, monospace); font-size: 9px; opacity: 0.85; }

.ac-tip {
  position: absolute; transform: translate(-50%, calc(-100% - 9px)); pointer-events: none;
  background: var(--popover, var(--card)); color: var(--foreground);
  border: 1px solid var(--border); border-radius: 7px; padding: 4px 8px;
  font-size: 11px; line-height: 1.3; white-space: nowrap; box-shadow: 0 4px 14px -6px rgba(0,0,0,.3);
  display: flex; flex-direction: column; align-items: center; gap: 1px; z-index: 2;
}
.ac-tip-l { font-family: var(--font-mono, ui-monospace, monospace); color: var(--muted-foreground); }
.ac-tip-v { font-weight: 700; font-size: 13px; }
</style>
