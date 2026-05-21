import { useI18n } from 'vue-i18n'

/**
 * メニュータイトルの国際化フック（legacy 互換）
 *
 * ルール：
 *  - メニューの path（例: `/system/user`）を小文字 & ドット区切りに変換し、
 *    `route.{...}._name` キーとして i18n から検索。
 *  - 翻訳キーが登録されていない場合は backend 提供の `title` を返す。
 */
export function useMenuTitle() {
  const { t, te } = useI18n()

  function pathToKey(path) {
    if (!path) return ''
    return String(path).replace(/^\/+/, '').replace(/\/+/g, '.').toLowerCase()
  }

  function translate(menu) {
    if (!menu) return ''
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
