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

export default i18n
