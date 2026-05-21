import vue from 'eslint-plugin-vue'
import globals from 'globals'

const TZ_RULES = [
  {
    selector: "NewExpression[callee.name='Date'][arguments.length=0]",
    message: '禁止 new Date() — ブラウザ TZ 依存。@/lib/date の nowJST() / todayJSTStr() を使用してください'
  },
  {
    selector: "CallExpression[callee.property.name='toISOString']",
    message: '禁止 .toISOString() — UTC 返却で混乱しやすい。@/lib/date の toBackendDate() / toJSTDateTimeStr() を使用してください'
  },
  {
    selector: "CallExpression[callee.property.name='toLocaleDateString']",
    message: '禁止 .toLocaleDateString() — ブラウザ TZ 依存。@/lib/date の toJSTDateStr() を使用してください'
  },
  {
    selector: "CallExpression[callee.property.name='toLocaleTimeString']",
    message: '禁止 .toLocaleTimeString() — ブラウザ TZ 依存。@/lib/date の toJSTDateTimeStr() を使用してください'
  },
  {
    selector: "CallExpression[callee.name='dayjs'][arguments.length=0]",
    message: '禁止 dayjs() — ブラウザ TZ 依存の現在時刻。@/lib/date の nowJST() を使用してください'
  },
]

export default [
  {
    ignores: ['dist/**', 'node_modules/**', 'ai/specs/**', 'docs/**', 'scripts/**'],
  },
  ...vue.configs['flat/recommended'],
  {
    languageOptions: {
      ecmaVersion: 'latest',
      sourceType: 'module',
      globals: {
        ...globals.browser,
        ...globals.node,
      },
    },
    rules: {
      'no-restricted-syntax': ['error', ...TZ_RULES],
      'no-restricted-imports': ['error', {
        paths: [{
          name: 'dayjs',
          message: '禁止直接 import dayjs — @/lib/date の nowJST/toJST/todayJSTStr/toBackendDate 等を経由してください（TZ 一元管理のため）'
        }]
      }],
      'vue/multi-word-component-names': 'off',
      'vue/no-mutating-props': 'warn',
      'vue/html-self-closing': 'off',
      'vue/max-attributes-per-line': 'off',
      'vue/singleline-html-element-content-newline': 'off',
      'vue/html-indent': 'off',
      'vue/html-closing-bracket-newline': 'off',
      'vue/attributes-order': 'off',
      'vue/attribute-hyphenation': 'off',
      'vue/v-on-event-hyphenation': 'off',
      'vue/first-attribute-linebreak': 'off',
    },
  },
  // lib/date.js は時区ヘルパー本体なので豁免
  {
    files: ['src/lib/date.js'],
    rules: {
      'no-restricted-syntax': 'off',
      'no-restricted-imports': 'off',
    },
  },
]
