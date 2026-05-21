import { ref } from 'vue'
import storage from '@/dict/storage'

/**
 * 从 storage.js 读取字典数据，根据当前语言返回 { label, value } 列表
 * 与原项目 $dict.getDictDropdownList / getDictLabel 功能一致
 */
function getLang() {
  return localStorage.getItem('i18n-lang') || 'ja_JP'
}

function getDictData(dictCode) {
  const dict = storage.dicts?.[dictCode]
  if (!dict || !dict.data) return []
  const lang = getLang()
  return dict.data.map((item) => ({
    label: item[lang + '_label'] || item.ja_JP_label || String(item.value),
    value: String(item.value)
  }))
}

function getDictLabelByValue(dictCode, value) {
  const dict = storage.dicts?.[dictCode]
  if (!dict || !dict.data) return value
  const lang = getLang()
  const item = dict.data.find((d) => String(d.value) === String(value))
  if (!item) return value
  return item[lang + '_label'] || item.ja_JP_label || String(value)
}

/**
 * 辞書データ取得 composable
 * @param {string} dictCode - 辞書コード（storage.js の key）
 * @returns {{ options: Ref, getLabel: Function, loading: Ref }}
 */
export function useDict(dictCode) {
  const options = ref(getDictData(dictCode))
  const loading = ref(false)

  function getLabel(value) {
    return getDictLabelByValue(dictCode, value)
  }

  function reload() {
    options.value = getDictData(dictCode)
  }

  return { options, getLabel, loading, reload }
}

/**
 * 複数辞書を一括ロード
 * @param {string[]} codes
 * @returns {Object<string, { options, getLabel, loading }>}
 */
export function useDicts(codes) {
  const dicts = {}
  for (const code of codes) {
    dicts[code] = useDict(code)
  }
  return dicts
}
