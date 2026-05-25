import { useI18n } from 'vue-i18n'

/**
 * メニュータイトルの国際化フック
 *
 * 優先順位：
 *  1. `menu.titleI18n[currentLocale]`  — DB 側（管理者がメニュー編集画面で入力した値）
 *  2. `route.{path-dot-lower}._name`   — フロント側 i18n（静的画面や未来の上書き用）
 *  3. `menu.title`                       — DB の単一カラム fallback（旧データ／未翻訳時）
 *
 * (1) を最優先にした理由：メニューは「データ」なので運用編集が一次ソース。
 *   コード側翻訳（(2)）は静的画面用の保険として残す。
 */
export function useMenuTitle() {
  const { t, te, locale } = useI18n()

  function pathToKey(path) {
    if (!path) return ''
    return String(path).replace(/^\/+/, '').replace(/\/+/g, '.').toLowerCase()
  }

  function translate(menu) {
    if (!menu) return ''
    const i18nMap = menu.titleI18n
    if (i18nMap && typeof i18nMap === 'object') {
      const val = i18nMap[locale.value]
      if (val) return val
    }
    const key = pathToKey(menu.path)
    if (key) {
      const fullKey = `route.${key}._name`
      if (te(fullKey)) {
        const val = t(fullKey)
        if (val && val !== fullKey) return val
      }
    }
    return menu.title || ''
  }

  return { translate, pathToKey }
}
