import { createApp } from 'vue'
import { createPinia } from 'pinia'
import { VueQueryPlugin, QueryClient, QueryCache } from '@tanstack/vue-query'
import NProgress from 'nprogress'
import App from './App.vue'
import router from './router'
import i18n from './lang'
import { registerPermissionDirectives } from './directives/permission'
import { toast } from '@/composables/useToast'
import '@/lib/date'
import './styles/main.css'

// ─── TanStack Query client ───
// staleTime 30s で filter 切替直後の連打を抑え、再マウント時に余計な再取得を防ぐ。
// エラー時は QueryCache.onError で集中ハンドリングし、meta.label でラベル付け。
const queryClient = new QueryClient({
  queryCache: new QueryCache({
    onError: (error, query) => {
      const label = query.meta?.label || 'データ'
      toast.error(`${label}の取得に失敗しました`)
    },
  }),
  defaultOptions: {
    queries: {
      staleTime: 30_000,
      gcTime: 5 * 60_000,
      // 他タブから戻ってきた時に自動再取得（長時間 tab 開きっぱなしの stale data 対策）。
      // `staleTime: 30s` を超えている query のみ fetch するので不必要なリクエストは起きない。
      refetchOnWindowFocus: true,
      retry: 1,
    },
  },
})

// 画面上部に非ブロッキングな細い進捗バー（既存 router と共有）。
// いずれかの query が fetching 中なら start、全部終われば done。
queryClient.getQueryCache().subscribe(() => {
  const n = queryClient.isFetching()
  if (n > 0) {
    if (!NProgress.isStarted()) NProgress.start()
  } else {
    NProgress.done()
  }
})

const app = createApp(App)

app.use(createPinia())
app.use(router)
app.use(i18n)
app.use(VueQueryPlugin, { queryClient })

// Register v-role, v-any-role, v-permission, v-any-permission
registerPermissionDirectives(app)

app.mount('#app')
