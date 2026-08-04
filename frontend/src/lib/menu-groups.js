/**
 * Sidebar 一級メニューの区域分け（`AppSidebar` から切り出した純関数）。
 *
 * 閾値は **`sortOrder`**（バックエンド `MenuNode` の実フィールド名。`/menu/me` の
 * ペイロード・`MenuPicker`・メニュー管理画面すべてこの名前）で判定する。
 * ここが `sort` になっていた時期があり、`undefined ?? 0 === 0` で全メニューが
 * 「常規」区域に落ち、`管理者設定` グループと固定フッター区域が**永久に空**だった
 * ——UI 側のマークアップと i18n はあるのに一度も出せない、という壊れ方をする。
 * 純関数として切り出したのは、その契約を単体テストで固定できるようにするため。
 *
 *   sortOrder <= 9000            → 常規（トップ区域）
 *   9000 < sortOrder <= 10000    → 管理者設定 グループ
 *   sortOrder > 10000            → 固定フッター
 */

export const ADMIN_GROUP_MIN_SORT = 9000
export const FOOTER_MIN_SORT = 10000

/** バックエンドが返す並び順。未設定は 0 扱い。 */
export function sortOf(menu) {
  return menu?.sortOrder ?? 0
}

const visible = (m) => !m?.hide
const isLeaf = (m) => !m?.children || m.children.length === 0
const isGroup = (m) => !!m?.children && m.children.length > 0

/** トップ区域に昇格する葉ボタン：非表示でなく、子を持たず、常規レンジ。 */
export function topLevelLeafsOf(menus) {
  return (menus || []).filter(
    (m) => visible(m) && isLeaf(m) && sortOf(m) <= ADMIN_GROUP_MIN_SORT
  )
}

/** トップ区域の中の、子を持つ一級メニュー（折りたたみグループ）。 */
export function topLevelGroupsOf(menus) {
  return (menus || []).filter(
    (m) => visible(m) && isGroup(m) && sortOf(m) <= ADMIN_GROUP_MIN_SORT
  )
}

/** 管理者設定 グループ：9000 < sortOrder <= 10000。 */
export function adminMenusOf(menus) {
  return (menus || []).filter((m) => {
    if (!visible(m)) return false
    const s = sortOf(m)
    return s > ADMIN_GROUP_MIN_SORT && s <= FOOTER_MIN_SORT
  })
}

/** 固定フッター区域：sortOrder > 10000。 */
export function footerMenusOf(menus) {
  return (menus || []).filter((m) => visible(m) && sortOf(m) > FOOTER_MIN_SORT)
}
