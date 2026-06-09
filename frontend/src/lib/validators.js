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

/**
 * ユーザー名書式チェック — 3〜64 文字、小文字英数字で始まり、以降は
 * 小文字英数字・ハイフン・アンダースコアのみ。バックエンド
 * （PlatformUserDto の @Pattern + @Size(min=3) / Keycloak の username
 * 長さポリシー 3..255）と一致させ、サーバー往復前にフロントで弾く。
 * 空値は許容（必須チェックは別途）。
 */
export function isValidUsername(val) {
  if (val === '' || val === null || val === undefined) return true
  return /^[a-z0-9][a-z0-9_-]{2,63}$/.test(String(val).trim())
}
