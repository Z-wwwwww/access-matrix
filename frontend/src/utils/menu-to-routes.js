/**
 * 菜单数据 → vue-router 路由配置
 *
 * 复刻 ele-admin-pro 的 formatMenus + menuToRoutes 逻辑：
 * 1. formatMenus: 标准化菜单树（构建 meta、推断父级 path/redirect、提取 homePath）
 * 2. menuToRoutes: 递归转换为路由配置（目录→EmptyLayout、叶子→动态import、URL→iframe）
 */
import EmptyLayout from '@/components/layout/EmptyLayout.vue'

const viewModules = import.meta.glob('/src/views/**/*.vue')

// 路径规范化：小写 + 移除连字符（兼容后端 kebab-case 和前端 PascalCase 命名差异）
// 例えば: "/system/user-edit" → "/src/views/system/useredit.vue"
// マッチ可能ファイル: "/src/views/system/User/UserEdit.vue"
function normalizePath(p) {
  return p.toLowerCase().replace(/-/g, '')
}

const viewModulesLower = {}
const viewModulesNormalized = {}
for (const key of Object.keys(viewModules)) {
  viewModulesLower[key.toLowerCase()] = key
  viewModulesNormalized[normalizePath(key)] = key
}

// ─── formatMenus ────────────────────────────────────────────────────

/**
 * 标准化菜单树，返回 { menus, homePath }
 * - 把 title/icon/hide 等扁平字段聚合到 meta
 * - 父级无 path 时从第一个子菜单推断
 * - 父级无 redirect 时默认指向第一个子菜单
 * - homePath = 第一个叶子菜单的 path
 */
export function formatMenus(data) {
  let homePath = ''
  let homeTitle = ''

  function walk(items) {
    if (!items || !items.length) return []

    return items.map((item) => {
      const menu = { ...item }

      // 构建 meta（titleI18n を含めて TabBar / document.title 側でも locale 切替に追従できるようにする）
      menu.meta = {
        title: menu.title,
        titleI18n: menu.titleI18n,
        icon: menu.icon,
        hide: menu.hide === 1,
        hideFooter: menu.hideFooter === 1,
        hideSidebar: menu.hideSidebar === 1,
        pinned: menu.pinned === 1,
        tabUnique: menu.tabUnique
      }

      // 递归处理子菜单
      if (menu.children && menu.children.length) {
        menu.children = walk(menu.children)

        // 父级无 path → 从第一个子菜单路径截取
        if (!menu.path && menu.children.length) {
          const firstChildPath = menu.children[0].path || ''
          const parts = firstChildPath.split('/')
          if (parts.length > 2) {
            menu.path = parts.slice(0, parts.length - 1).join('/')
          }
        }

        // 父级无 redirect → 默认重定向到第一个子菜单
        if (!menu.redirect && menu.children.length) {
          menu.redirect = menu.children[0].path
        }
      } else {
        menu.children = []
      }

      // homePath = 第一个叶子节点
      if (!homePath && menu.component && menu.component.trim() && menu.path) {
        homePath = menu.path
        homeTitle = menu.title || ''
      }

      return menu
    })
  }

  const menus = walk(data)
  return { menus, homePath, homeTitle }
}

// ─── menuToRoutes ───────────────────────────────────────────────────

function isUrl(str) {
  return /^https?:\/\//.test(str)
}

function resolveComponent(component) {
  if (!component) return null

  // 後端 menu の component に `.vue` 拡張子が付いている場合を正規化（揺れ吸収）
  // 例: "/system/User/UserEdit.vue" → "/system/User/UserEdit"
  const normalized = component.replace(/\.vue$/i, '')

  // 取末段目录名，用于 Component/Component.vue 模式匹配
  const baseName = normalized.split('/').filter(Boolean).pop() || ''

  // 候选路径（按优先级排列）
  const candidates = [
    `/src/views${normalized}.vue`,
    `/src/views${normalized}/index.vue`,
    `/src/views${normalized}/${baseName}.vue`
  ]

  for (const path of candidates) {
    // 1. 精确匹配
    if (viewModules[path]) return viewModules[path]
    // 2. 大小写不敏感匹配（后端存小写，前端目录可能是 PascalCase）
    const lowerHit = viewModulesLower[path.toLowerCase()]
    if (lowerHit) return viewModules[lowerHit]
    // 3. 规范化匹配（去连字符 + 小写，兼容 kebab-case ↔ PascalCase）
    const normHit = viewModulesNormalized[normalizePath(path)]
    if (normHit) return viewModules[normHit]
  }

  return null
}

/**
 * 递归将标准化菜单树转为 vue-router 路由配置
 * - component 有值且非URL → 动态 import Vue 组件
 * - component 为 URL → iframe（预留）
 * - component 为 null → 目录节点，使用 EmptyLayout
 */
export function menuToRoutes(menus, isRoot = true) {
  if (!menus || !menus.length) return []

  const routes = []

  // 根级别注入 /redirect 路由（用于 tab 刷新）
  if (isRoot) {
    routes.push({
      path: '/redirect',
      component: EmptyLayout,
      children: [
        {
          path: '/redirect/:path(.*)',
          component: () => import('@/views/_redirect.vue')
        }
      ]
    })
  }

  menus.forEach((item) => {
    const meta = item.meta || {
      title: item.title,
      titleI18n: item.titleI18n,
      icon: item.icon,
      hide: item.hide === 1
    }

    const route = {
      path: item.path,
      name: item.path,
      meta,
      redirect: item.redirect
    }

    if (item.component && item.component.trim()) {
      if (isUrl(item.component)) {
        // 外部URL → iframe 布局（预留）
        route.component = EmptyLayout
        route.meta.iframe = item.component
      } else {
        // 组件路径 → 动态 import
        const comp = resolveComponent(item.component)
        if (comp) {
          route.component = comp
        } else {
          route.component = EmptyLayout
        }
      }
    } else {
      // 无 component → 目录节点，EmptyLayout 作为 <router-view> 容器
      route.component = EmptyLayout
    }

    // 递归子路由
    if (item.children && item.children.length) {
      route.children = menuToRoutes(item.children, false)
    }

    routes.push(route)
  })

  return routes
}
