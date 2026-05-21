// Phase 2 / 2.4-2.6 検証: 聚合表 (impl=agg) vs 直査 (impl=direct) の API 出力 byte-equal 検査。
// 使い方:
//   $env:DASHBOARD_TOKEN="..."
//   node scripts/verify-agg-vs-direct.mjs
//
// テスト対象:
//   - mgmtKpi   : 10 KPI + 10 YoY フィールド (dataMeta は無視)
//   - occupancyChart × 4 type : categories + 3 年 × 12 月 の data series
//   - bookingAnalytics.paceCurve : 3 年バケット系列
//
// 複数フィルター組合せでクロス検証。

const BASE = process.env.DASHBOARD_API_BASE || 'http://127.0.0.1:9034/api'
const TOKEN = process.env.DASHBOARD_TOKEN
if (!TOKEN) { console.error('DASHBOARD_TOKEN required'); process.exit(2) }

// 比較対象フィルター集合 (代表的な組合せ)
const SCENARIOS = [
  { label: 'デフォルト (全件)',         params: { yearMonth: '2026-04' } },
  { label: '前年同月',                  params: { yearMonth: '2025-04' } },
  { label: '2 年前',                    params: { yearMonth: '2024-04' } },
  { label: '当年 (5 月)',               params: { yearMonth: '2026-05' } },
  // フィルター系: locationType / buildingType / propertyId / applyIndexFlg
  // (実データ次第。propertyId は agg にある実物件を 1 件指定したい場合は要追加)
  { label: 'locationType=1 (都市)',     params: { yearMonth: '2026-04', locationType: '1' } },
  { label: 'applyIndexFlg=true',        params: { yearMonth: '2026-04', applyIndexFlg: 'true' } },
]

async function fetchJson(path, params) {
  const url = new URL(BASE + path)
  for (const [k, v] of Object.entries(params)) if (v != null) url.searchParams.set(k, v)
  const res = await fetch(url, { headers: { Authorization: TOKEN } })
  if (!res.ok) throw new Error(`HTTP ${res.status}: ${url}`)
  const json = await res.json()
  if (json.code !== 0) throw new Error(`API code=${json.code} ${json.msg}`)
  return json.data
}

// --- Diff helpers ---

const KPI_FIELDS = [
  'revpar', 'nrevpar', 'abv', 'directRate', 'rn', 'gpb',
  'cancelRate', 'cancelRateExcl24h', 'leadtime', 'alos',
  'revparYoy', 'nrevparYoy', 'abvYoy', 'directRateYoy', 'rnYoy', 'gpbYoy',
  'cancelRateYoy', 'cancelRateExcl24hYoy', 'leadtimeYoy', 'alosYoy',
]

function diffMgmtKpi(direct, agg) {
  const out = []
  for (const f of KPI_FIELDS) {
    const a = direct?.[f]
    const b = agg?.[f]
    if (String(a) !== String(b)) out.push({ f, direct: a, agg: b })
  }
  return out
}

function diffSeries(direct, agg, label) {
  const out = []
  if (JSON.stringify(direct.categories) !== JSON.stringify(agg.categories)) {
    out.push({ kind: 'categories', direct: direct.categories, agg: agg.categories })
  }
  const dMap = new Map((direct.series || []).map(s => [s.year, s.data]))
  const aMap = new Map((agg.series || []).map(s => [s.year, s.data]))
  const years = new Set([...dMap.keys(), ...aMap.keys()])
  for (const y of [...years].sort()) {
    const d = dMap.get(y) || []
    const a = aMap.get(y) || []
    const len = Math.max(d.length, a.length)
    for (let i = 0; i < len; i++) {
      if (String(d[i]) !== String(a[i])) {
        out.push({
          kind: 'cell', label, year: y, idx: i,
          direct: d[i], agg: a[i],
        })
      }
    }
  }
  return out
}

// --- Run ---

const t0 = Date.now()
let totalDiffs = 0
const summary = []

for (const sc of SCENARIOS) {
  console.log(`\n=== ${sc.label} ===`)

  // mgmtKpi
  try {
    const [direct, agg] = await Promise.all([
      fetchJson('/dashboard/mgmtKpi', { ...sc.params, impl: 'direct' }),
      fetchJson('/dashboard/mgmtKpi', { ...sc.params, forceRecompute: 'true' }),
    ])
    const d = diffMgmtKpi(direct, agg)
    console.log(`  mgmtKpi: ${d.length} diff(s)`)
    if (d.length) {
      for (const x of d) console.log(`    ${x.f}: direct=${x.direct} agg=${x.agg}`)
    }
    totalDiffs += d.length
    summary.push({ scenario: sc.label, api: 'mgmtKpi', diffs: d.length })
  } catch (e) {
    console.log(`  mgmtKpi: ERROR ${e.message}`)
    summary.push({ scenario: sc.label, api: 'mgmtKpi', error: e.message })
  }

  // occupancyChart × 4 type
  for (const type of ['occ', 'adr', 'revpar', 'alos']) {
    try {
      const [direct, agg] = await Promise.all([
        fetchJson('/dashboard/occupancyChart', { ...sc.params, type, impl: 'direct' }),
        fetchJson('/dashboard/occupancyChart', { ...sc.params, type, forceRecompute: 'true' }),
      ])
      const d = diffSeries(direct, agg, `occChart-${type}`)
      console.log(`  occupancyChart/${type}: ${d.length} diff(s)`)
      if (d.length) {
        for (const x of d.slice(0, 5)) {
          if (x.kind === 'cell') console.log(`    ${x.year}/idx${x.idx}: direct=${x.direct} agg=${x.agg}`)
          else console.log(`    [${x.kind}]`, JSON.stringify(x))
        }
        if (d.length > 5) console.log(`    ... +${d.length - 5} more`)
      }
      totalDiffs += d.length
      summary.push({ scenario: sc.label, api: `occChart-${type}`, diffs: d.length })
    } catch (e) {
      console.log(`  occupancyChart/${type}: ERROR ${e.message}`)
      summary.push({ scenario: sc.label, api: `occChart-${type}`, error: e.message })
    }
  }

  // paceCurve (bookingAnalytics)
  try {
    const [direct, agg] = await Promise.all([
      fetchJson('/dashboard/bookingAnalytics', { ...sc.params, impl: 'direct' }),
      fetchJson('/dashboard/bookingAnalytics', { ...sc.params, forceRecompute: 'true' }),
    ])
    const dPace = direct.paceCurve || { categories: [], series: [] }
    const aPace = agg.paceCurve || { categories: [], series: [] }
    // paceCurve.days は categories と同じ役割
    const synth = (p) => ({ categories: p.days, series: p.series })
    const d = diffSeries(synth(dPace), synth(aPace), 'paceCurve')
    console.log(`  paceCurve: ${d.length} diff(s)`)
    if (d.length) {
      for (const x of d.slice(0, 5)) {
        if (x.kind === 'cell') console.log(`    ${x.year}/idx${x.idx}: direct=${x.direct} agg=${x.agg}`)
        else console.log(`    [${x.kind}]`, JSON.stringify(x))
      }
      if (d.length > 5) console.log(`    ... +${d.length - 5} more`)
    }
    totalDiffs += d.length
    summary.push({ scenario: sc.label, api: 'paceCurve', diffs: d.length })
  } catch (e) {
    console.log(`  paceCurve: ERROR ${e.message}`)
    summary.push({ scenario: sc.label, api: 'paceCurve', error: e.message })
  }
}

console.log('\n========== Summary ==========')
console.log('scenario'.padEnd(30) + '| api'.padEnd(20) + '| diffs')
console.log('-'.repeat(60))
for (const s of summary) {
  const status = s.error ? `ERROR: ${s.error}` : `${s.diffs}`
  console.log(s.scenario.padEnd(30) + '| ' + s.api.padEnd(18) + '| ' + status)
}
console.log(`\nTotal diffs: ${totalDiffs}`)
console.log(`Total time:  ${Date.now() - t0}ms`)
process.exit(totalDiffs === 0 ? 0 : 1)
