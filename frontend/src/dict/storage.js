/**
 * Dict storage — i18n-aware option lists consumed by `useDict` / `useDicts`.
 *
 * Reduced to a couple of generic common entries when the business code was
 * removed from this baseline. Business modules that come back later should
 * re-introduce their own dict keys here (or load them dynamically from a
 * backend endpoint) — see the legacy production frontend for the previous
 * shape if reference is needed.
 *
 * Shape (per key):
 *   <key>: {
 *     data: [
 *       { value, zh_CN_label, zh_TW_label, en_label, ja_JP_label, ko_KR_label },
 *       ...
 *     ]
 *   }
 */
const dicts = {
  common_flag: {
    data: [
      { value: '0', zh_CN_label: 'Off', zh_TW_label: 'オフ', en_label: 'Off', ja_JP_label: 'オフ', ko_KR_label: 'Off' },
      { value: '1', zh_CN_label: 'On',  zh_TW_label: 'オン', en_label: 'On',  ja_JP_label: 'オン', ko_KR_label: 'On'  }
    ]
  },
  common_enable: {
    data: [
      { value: '0', zh_CN_label: 'NO',  zh_TW_label: 'NO',  en_label: 'NO',  ja_JP_label: 'NO',  ko_KR_label: 'NO'  },
      { value: '1', zh_CN_label: 'YES', zh_TW_label: 'YES', en_label: 'YES', ja_JP_label: 'YES', ko_KR_label: 'YES' }
    ]
  },
  common_status_list: {
    data: [
      { value: 1, zh_CN_label: '有效', zh_TW_label: '有効', en_label: 'Valid',   ja_JP_label: '有効', ko_KR_label: 'Valid'   },
      { value: 0, zh_CN_label: '无效', zh_TW_label: '無効', en_label: 'Invalid', ja_JP_label: '無効', ko_KR_label: 'Invalid' }
    ]
  }
}

export default {
  dicts
}
