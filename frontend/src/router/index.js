/**
 * 路由配置
 *
 * 静态路由：不需要登录即可访问的公开页面
 * 动态路由：在 beforeEach 守卫中根据后端菜单数据动态注册
 */
import { createRouter, createWebHistory } from 'vue-router'
import { toRaw } from 'vue'
import NProgress from 'nprogress'
import 'nprogress/nprogress.css'
import { useAuthStore } from '@/stores/auth'
import { useMenuStore } from '@/stores/menu'

NProgress.configure({ showSpinner: false })

const APP_TITLE = 'Access Matrix'

/** 免登录白名单 */
const WHITE_LIST = ['/login', '/forget']

/** 登录后可访问但不走后端菜单的静态页面（ヘッダー・ユーザーメニュー等から遷移） */
const STATIC_LAYOUT_CHILDREN = [
  {
    path: 'profile',
    component: () => import('@/views/system/Profile/Profile.vue'),
    meta: { title: 'プロフィール' }
  }
]

/** 静态路由 — 仅包含不需要登录的公开页面 */
const routes = [
  {
    path: '/login',
    component: () => import('@/views/login/index.vue'),
    meta: { title: 'ログイン' }
  },
  {
    path: '/forget',
    component: () => import('@/views/login/Forget.vue'),
    meta: { title: 'パスワード忘れ' }
  },
  {
    path: '/:pathMatch(.*)*',
    component: () => import('@/views/404.vue'),
    meta: { title: '404' }
  }
]

const router = createRouter({
  history: createWebHistory(),
  routes
})

/**
 * 路由守卫
 *
 * beforeEach:
 *   ├─ 有 token?
 *   │   ├─ 菜单已加载? → next()
 *   │   └─ 菜单未加载? → 请求菜单 → addRoute 整个 layout → next(to.fullPath)
 *   ├─ 在白名单? → next()
 *   └─ 其他 → 重定向到 /login?from=原路径
 */
router.beforeEach(async (to, from, next) => {
  NProgress.start()

  const authStore = useAuthStore()
  const menuStore = useMenuStore()

  if (authStore.isAuthenticated) {
    // 已登录 — 判断是否需要加载动态路由
    if (!menuStore.menus) {
      try {
        const { home } = await menuStore.fetchMenus()
        // 整体注册 layout 路由（与原项目一致）
        // toRaw 确保传给 router 的是纯对象，非响应式代理
        router.addRoute({
          name: 'AppLayout',
          path: '/',
          component: () => import('@/components/layout/AppLayout.vue'),
          redirect: home,
          children: [...toRaw(menuStore.routeChildren), ...STATIC_LAYOUT_CHILDREN]
        })
        // 重新导航 — 必须显式传 query/hash，path key 不会从 fullPath 解析它们
        next({ path: to.path, query: to.query, hash: to.hash, replace: true })
      } catch (e) {
        console.error('[router] Failed to load menus:', e)
        next()
      }
    } else {
      // 访问 / 时重定向到后端指定的首页
      if (to.path === '/' && menuStore.home) {
        next({ path: menuStore.home, replace: true })
        return
      }
      next()
    }
  } else if (WHITE_LIST.includes(to.path)) {
    next()
  } else {
    next({ path: '/login', query: to.path === '/' ? {} : { from: to.path } })
  }
})

router.afterEach((to) => {
  window.scrollTo(0, 0)
  setTimeout(() => {
    NProgress.done(true)
  }, 300)
  // 更新页面标题
  if (!to.path.startsWith('/redirect/')) {
    const names = []
    if (to.meta?.title) {
      names.push(to.meta.title)
    }
    if (APP_TITLE) {
      names.push(APP_TITLE)
    }
    document.title = names.join(' - ')
  }
})

export default router
