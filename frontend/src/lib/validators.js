/**
 * 統一バリデータ — フォーム検証用ヘルパー
 *
 * 必須チェックは validate() 内で `!val` を直接書き、書式チェック類はここで集約。
 * 空値は許容（必須は別途チェック）— 「空でなく、かつ書式が違う」場合のみ false を返す。
 */

/**
 * メールアドレス書式チェック
 * RFC 5322 完全準拠ではなく、実用範囲を抑えた簡易版（典型的なフォーム入力ミスを捕捉）
 */
export function isValidEmail(val) {
  if (val === '' || val === null || val === undefined) return true
  const s = String(val).trim()
  if (s.length === 0) return true
  // local@domain.tld：ローカル部に空白・@ 不可、ドメイン部にドット必須
  return /^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(s)
}
