import { defineStore } from 'pinia'
import { ref } from 'vue'
import { getMenuListApi } from '../../services/menu'
import { useAuthStore } from './auth'
import { useTabsStore } from './tabs'
import { formatMenus, menuToRoutes } from '@/utils/menu-to-routes'
import i18n from '@/lang'

export const useMenuStore = defineStore('menu', () => {
  const menus = ref(null)
  const home = ref('')
  /** menuToRoutes 转换后的路由配置，供 router.addRoute 使用 */
  const routeChildren = ref([])

  async function fetchMenus() {
    const res = await getMenuListApi()
    const resData = res.data

    if (resData.code !== 0) {
      // i18n.global.t is the imperative escape hatch outside of components.
      // Store-thrown errors must still respect the current locale.
      return Promise.reject(new Error(resData.msg || i18n.global.t('menu.message.fetchFailed')))
    }

    // 同时拉取用户信息（roles / authorities / permissionList）
    // 后端 /index/getMenuList 不返回 user 字段，需单独调用 /index/getUserInfo
    // 如果 login 流程已经预加载过 authorities，则跳过，避免 token 轮换竞态导致覆盖为空
    const authStore = useAuthStore()
    if (!authStore.authorities || authStore.authorities.length === 0) {
      try {
        await authStore.fetchUserInfo()
      } catch (e) {
        console.warn('[menu-store] fetchUserInfo failed:', e)
      }
    }

    // formatMenus: 标准化菜单树（构建 meta、推断父级 path/redirect、提取 homePath）
    const { menus: formattedMenus, homePath, homeTitle } = formatMenus(resData.data)
    menus.value = formattedMenus
    home.value = homePath

    // menuToRoutes: 菜单 → 路由配置（由路由守卫整体注册到 layout）
    routeChildren.value = menuToRoutes(formattedMenus)

    // 同步首页路径到 tabs store
    if (homePath) {
      const tabsStore = useTabsStore()
      tabsStore.setHome(homePath, homeTitle)
    }

    return { menus: formattedMenus, home: homePath }
  }

  function clearMenus() {
    menus.value = null
    home.value = ''
    routeChildren.value = []
  }

  return {
    menus,
    home,
    routeChildren,
    fetchMenus,
    clearMenus
  }
})
