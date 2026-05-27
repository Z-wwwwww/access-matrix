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
import i18n from '@/lang'

NProgress.configure({ showSpinner: false })

const APP_TITLE = 'Access Matrix'

/** 免登录白名单 */
const WHITE_LIST = ['/login', '/sso/callback']
/**
 * /invite/<token> 也是公开页面，但 token 是动态段，不能写进上面的精确匹配
 * 数组里。下面的守卫额外允许任意以 /invite/ 开头的路径，与 WHITE_LIST 形成
 * "exact match | prefix match" 两路覆盖。
 */
const PUBLIC_PREFIXES = ['/invite/']

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
    // OIDC redirect target — Keycloak sends ?code=...&state=... here
    // after a successful login. See utils/oidc.js + SsoCallback.vue.
    path: '/sso/callback',
    component: () => import('@/views/login/SsoCallback.vue'),
    meta: { title: 'Signing in…' }
  },
  {
    // Invite-acceptance landing page — the email link lands here with a
    // single-use token in the path. No session required.
    path: '/invite/:token',
    component: () => import('@/views/login/InviteAccept.vue'),
    meta: { title: 'アカウント設定' }
  },
  {
    // Password-reset landing page — used by the SSO → password reverse
    // migration. Email lands here with a single-use reset token. No
    // session required.
    path: '/reset-password/:token',
    component: () => import('@/views/login/ResetPasswordAccept.vue'),
    meta: { title: 'パスワード設定' }
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
        // Fail-loud: if /menu/me errored, the session is broken (often
        // permission / token issue). Silently next()-ing here lands the
        // user on a 404 catch-all with no clue why. Clear auth and
        // bounce back to login with the reason so the next round of
        // debugging doesn't need DevTools.
        console.error('[router] Failed to load menus:', e)
        authStore.clearAuth()
        const detail = encodeURIComponent((e && e.message) || 'menu-load-failed')
        next({ path: '/login', query: { err: 'menu', detail } })
      }
    } else {
      // 访问 / 时重定向到后端指定的首页
      if (to.path === '/' && menuStore.home) {
        next({ path: menuStore.home, replace: true })
        return
      }
      next()
    }
  } else if (WHITE_LIST.includes(to.path) || PUBLIC_PREFIXES.some((p) => to.path.startsWith(p))) {
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
  // 更新页面标题：titleI18n[currentLocale] が最優先、なければ meta.title（fallback）。
  if (!to.path.startsWith('/redirect/')) {
    const names = []
    const titleI18n = to.meta?.titleI18n
    const localized = titleI18n && typeof titleI18n === 'object'
      ? titleI18n[i18n.global.locale.value]
      : null
    const name = localized || to.meta?.title
    if (name) names.push(name)
    if (APP_TITLE) names.push(APP_TITLE)
    document.title = names.join(' - ')
  }
})

export default router
