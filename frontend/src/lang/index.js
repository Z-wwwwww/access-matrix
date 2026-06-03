/**
 * 国際化設定（vue-i18n@9 / Composition API モード）
 *
 * 使用方法：
 *   <script setup>
 *     import { useI18n } from 'vue-i18n'
 *     const { t } = useI18n()
 *     t('user.company')   // → "所属会社"
 *
 *   <template>
 *     {{ t('user.company') }}
 *     <Input :placeholder="t('user.keywordPlaceholder')" />
 *
 * 注：legacy: false により Composition API モード有効。
 *     globalInjection: true でテンプレート内 $t() も使用可（旧コード互換）。
 */
import { watch } from 'vue'
import { createI18n } from 'vue-i18n'
import enLocale from './en'
import zhCNLocale from './zh_CN'
import zhTWLocale from './zh_TW'
import jaJPLocale from './ja_JP'
import koKRLocale from './ko_KR'

const messages = {
  en: enLocale,
  zh_CN: zhCNLocale,
  zh_TW: zhTWLocale,
  ja_JP: jaJPLocale,
  ko_KR: koKRLocale
}

const i18n = createI18n({
  legacy: false,
  globalInjection: true,
  locale: localStorage.getItem('i18n-lang') || 'ja_JP',
  fallbackLocale: 'ja_JP',
  messages,
  missingWarn: false,
  fallbackWarn: false,
  silentTranslationWarn: true
})

/**
 * Keep <html lang> in sync with the active locale so CSS `:lang()` rules
 * (locale-aware font stack in main.css) take effect. BCP-47 script subtags
 * for Chinese (zh-Hans / zh-Hant) drive the Noto Sans SC / TC selection;
 * the others map to their plain language tag.
 */
const HTML_LANG = {
  en: 'en',
  zh_CN: 'zh-Hans',
  zh_TW: 'zh-Hant',
  ja_JP: 'ja',
  ko_KR: 'ko'
}

function applyHtmlLang(locale) {
  document.documentElement.lang = HTML_LANG[locale] || 'ja'
}

applyHtmlLang(i18n.global.locale.value)
watch(i18n.global.locale, applyHtmlLang)

export default i18n
