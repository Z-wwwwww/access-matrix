/**
 * 統一日期処理 — 強制使用 Asia/Tokyo (JST/UTC+9)
 *
 * 本系統為日本酒店管理系統，所有日期顯示和儲存均按 JST 解讀。
 * - DatePicker 內部用 YYYY-MM-DD 字符串
 * - 後端要求 yyyy-MM-dd HH:mm:ssZZ 格式（如 2026-04-11 00:00:00+0900）
 * - 後端可能返回 UTC 表示（如 2026-04-10 15:00:00+0000）— 需轉換為 JST 顯示
 */
import dayjs from 'dayjs'
import utc from 'dayjs/plugin/utc'
import timezone from 'dayjs/plugin/timezone'

dayjs.extend(utc)
dayjs.extend(timezone)

export const TZ = import.meta.env.VITE_DEFAULT_TIMEZONE || 'Asia/Tokyo'
dayjs.tz.setDefault(TZ)

/** 任意日期字符串 → JST 的 YYYY-MM-DD（用於 DatePicker 顯示） */
export function toJSTDateStr(val) {
  if (!val || typeof val !== 'string') return ''
  // 純日期 YYYY-MM-DD → 直接返回（不做 TZ 轉換）
  if (val.length === 10) return val
  // 含時間/時區的字符串 → 解析後轉 JST
  const d = dayjs(val.replace(' ', 'T'))
  if (!d.isValid()) return ''
  return d.tz(TZ).format('YYYY-MM-DD')
}

/** 任意日期字符串 → JST 的 YYYY-MM-DD HH:mm（用於 DateTimePicker 顯示） */
export function toJSTDateTimeStr(val) {
  if (!val || typeof val !== 'string') return ''
  const d = dayjs(val.replace(' ', 'T'))
  if (!d.isValid()) return ''
  return d.tz(TZ).format('YYYY-MM-DD HH:mm')
}

export function toJSTDateTimeDisp(val) {
  return toJSTDateTimeStr(val).replace(/-/g, '/')
}

/** 任意日期字符串 → JST 的 YYYY-MM-DD HH:mm:ss（含秒，用於更新履历等列表展示） */
export function toJSTDateTimeFullStr(val) {
  if (!val || typeof val !== 'string') return ''
  const d = dayjs(val.replace(' ', 'T'))
  if (!d.isValid()) return ''
  return d.tz(TZ).format('YYYY-MM-DD HH:mm:ss')
}

/** 表示専用: YYYY/MM/DD HH:mm:ss（含秒、スラッシュ区切り。監査ログ / 所要時間計測等、秒精度が必要な画面で使う） */
export function toJSTDateTimeFullDisp(val) {
  return toJSTDateTimeFullStr(val).replace(/-/g, '/')
}

/** JST 当日 YYYY-MM-DD（避开 new Date().toISOString() 的 UTC 陷阱） */
export function todayJSTStr() {
  return dayjs().tz(TZ).format('YYYY-MM-DD')
}

/** 現在時刻を JST (Asia/Tokyo) の dayjs オブジェクトとして返す。
 *  ブラウザ ローカル TZ に依存しない年/月/日/時/分 演算用。
 *  例: nowJST().year() / .month() (0-based) / .add(1,'month').daysInMonth() */
export function nowJST() {
  return dayjs().tz(TZ)
}

/** epoch ms または日付文字列 → dayjs JST オブジェクト。
 *  純日付 'YYYY-MM-DD' は JST 午夜として解釈する（ブラウザ TZ 非依存）。 */
export function toJST(val) {
  if (typeof val === 'string') {
    // 純日付 → JST 午夜（ブラウザ TZ で解釈すると非 JST 環境で 1 日ずれる）
    if (/^\d{4}-\d{2}-\d{2}$/.test(val)) {
      return dayjs.tz(val + ' 00:00:00', TZ)
    }
    return dayjs(val.replace(' ', 'T')).tz(TZ)
  }
  return dayjs(val).tz(TZ)
}

/** 任意日期字符串 → 後端要求的 yyyy-MM-dd HH:mm:ssZZ 格式（JST） */
export function toBackendDate(val) {
  if (!val || typeof val !== 'string') return val
  // 純日期 → 當作 JST 0 時
  if (val.length === 10) {
    return dayjs.tz(val + ' 00:00:00', TZ).format('YYYY-MM-DD HH:mm:ssZZ')
  }
  // YYYY-MM-DD HH:mm[:ss] → 當作 JST 時刻
  if (/^\d{4}-\d{2}-\d{2}[ T]\d{2}:\d{2}(:\d{2})?$/.test(val)) {
    const normalized = val.replace('T', ' ')
    const withSec = normalized.length === 16 ? normalized + ':00' : normalized
    return dayjs.tz(withSec, TZ).format('YYYY-MM-DD HH:mm:ssZZ')
  }
  // 含時區的完整格式（後端返回的）→ 轉為 JST 重新格式化
  const d = dayjs(val.replace(' ', 'T'))
  if (!d.isValid()) return val
  return d.tz(TZ).format('YYYY-MM-DD HH:mm:ssZZ')
}
