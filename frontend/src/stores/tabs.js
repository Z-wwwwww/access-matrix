import { defineStore } from 'pinia'
import { ref, reactive, computed, watch } from 'vue'
import router from '@/router'

const STORAGE_KEY = 'access_matrix_tabs'

function loadFromStorage() {
  try {
    const raw = sessionStorage.getItem(STORAGE_KEY)
    if (!raw) return null
    const parsed = JSON.parse(raw)
    // 兼容旧版 tab 数据结构（只有 path，没有 key）→ 补齐 key 字段
    if (parsed.tabs && Array.isArray(parsed.tabs)) {
      parsed.tabs = parsed.tabs.map((t) => (t.key ? t : { ...t, key: t.fullPath || t.path }))
    }
    return parsed
  } catch {
    return null
  }
}

function saveToStorage(state) {
  try {
    sessionStorage.setItem(STORAGE_KEY, JSON.stringify(state))
  } catch {
    /* ignore quota errors */
  }
}

export const useTabsStore = defineStore('tabs', () => {
  const persisted = loadFromStorage()

  /** 首页路径，由 menuStore 加载菜单后设置 */
  const homePath = ref(persisted?.homePath || '')
  /** 首页标题 */
  const homeTitle = ref(persisted?.homeTitle || 'ホーム')

  /**
   * Tab 项: { key, path, fullPath, title }
   * - key      : 唯一标识。默认取 fullPath（含 query/hash），保证同 path 不同 query 各成一 tab
   *              首页固定用 homePath，避免不同 query 访问首页时分裂
   *              meta.tabUnique === true 的路由用 path，支持列表页按 path 归并
   * - path     : route path，保留用于兜底导航
   * - fullPath : 完整 URL，switchTab 时用来 router.push 还原含 query 的真实地址
   * - title    : 显示名。有 query.id/no/code 时追加 #id 以区分多个同类 tab
   */
  const tabs = ref(persisted?.tabs || [])
  /** 当前激活 tab 的 key */
  const activeTab = ref(persisted?.activeTab || '')

  /**
   * 刷新版本号: { fullPath: number }
   * 用于 EmptyLayout 的 keep-alive cache key —— 版本号变化会让 :key 改变,
   * keep-alive 视为新 vnode 重新挂载组件，触发 setup + onMounted 走完整的数据加载流程。
   * 不持久化（刷新页面后归零）。
   */
  const refreshVersions = reactive({})

  const cachedViews = computed(() =>
    tabs.value.map((t) => (t.path || t.key || '').replace(/\//g, '-').replace(/^-/, ''))
  )

  // 持久化
  watch(
    [tabs, activeTab, homePath, homeTitle],
    () => {
      saveToStorage({
        tabs: tabs.value,
        activeTab: activeTab.value,
        homePath: homePath.value,
        homeTitle: homeTitle.value
      })
    },
    { deep: true }
  )

  /** 根据 route 计算 tab 唯一标识 */
  function buildKey(route) {
    if (route.path === homePath.value) return homePath.value
    if (route.meta?.tabUnique) return route.path
    return route.fullPath || route.path
  }

  /** 根据 route 构造 tab 标题 */
  function buildTitle(route) {
    const base = route.meta?.title || route.path
    // 详细/编辑页面（path 以 /detail 结尾）追加「詳細 / 新規」后缀：
    //   有 query.id  → 详细（编辑已有记录）
    //   无 query.id  → 新规
    if (typeof route.path === 'string' && route.path.endsWith('/detail')) {
      const suffix = route.query?.id ? '詳細' : '新規'
      // 菜单名本身可能就带「詳細/新規」后缀，先剥掉再按当前模式追加
      const stripped = typeof base === 'string'
        ? base.replace(/[\s-]*(詳細|新規)$/, '').trim()
        : base
      return `${stripped} - ${suffix}`
    }
    // Dashboard カード遷移時の quickFilter 后缀は AppTabBar 側で
    // i18n 経由 (getQuickFilterLabelFromFullPath) で付与する。
    // ここで storage に書く title は base のみ。
    return base
  }

  function setHome(path, title) {
    homePath.value = path
    if (title) homeTitle.value = title
    // 确保首页 tab 始终存在并位于第一位
    const idx = tabs.value.findIndex((t) => t.key === path)
    if (idx === -1) {
      tabs.value.unshift({ key: path, path, fullPath: path, title: homeTitle.value })
    } else if (idx > 0) {
      const [home] = tabs.value.splice(idx, 1)
      tabs.value.unshift(home)
    }
  }

  function addTab(route) {
    if (!route.path || route.path === '/login') return

    const key = buildKey(route)
    const fullPath = route.fullPath || route.path
    const title = buildTitle(route)
    // titleI18n はタブにも保存：locale 切替時に AppTabBar が tab 自身から拾えるように。
    const titleI18n = route.meta?.titleI18n

    activeTab.value = key

    const exists = tabs.value.find((t) => t.key === key)
    if (exists) {
      exists.title = title
      exists.fullPath = fullPath
      exists.titleI18n = titleI18n
      return
    }

    tabs.value.push({
      key,
      path: route.path,
      fullPath,
      title,
      titleI18n
    })
  }

  /**
   * 淘汰指定 fullPath 的 keep-alive 缓存条目。
   * 通过 +1 refreshVersions 让 EmptyLayout 的 cacheKey 变化 → keep-alive 视为新 vnode
   * 下次访问该 URL 时会创建全新 instance（旧 instance 在 LRU 中等待自然淘汰）。
   *
   * 用于「关闭 tab 后再次打开同 URL 应该是干净状态」的场景，避免新規页表单残留旧输入。
   */
  function evictCache(fullPath) {
    if (!fullPath) return
    refreshVersions[fullPath] = (refreshVersions[fullPath] || 0) + 1
  }

  function removeTab(key) {
    // 首页 tab 不可关闭
    if (key === homePath.value) return

    const idx = tabs.value.findIndex((t) => t.key === key)
    if (idx === -1) return

    // 关闭前先记下 fullPath，便于淘汰对应的 keep-alive 缓存
    const removed = tabs.value[idx]

    tabs.value.splice(idx, 1)

    // 关 tab 时同步淘汰它的缓存：再开同 URL 不会复用旧 instance
    evictCache(removed?.fullPath)

    // 如果关闭的是当前激活 tab，切到相邻 tab
    if (activeTab.value === key) {
      const next = tabs.value[Math.min(idx, tabs.value.length - 1)]
      if (next) {
        activeTab.value = next.key
        router.push(next.fullPath || next.path)
      }
    }
  }

  function switchTab(key) {
    activeTab.value = key
    const tab = tabs.value.find((t) => t.key === key)
    router.push(tab?.fullPath || key)
  }

  function closeOthers(key) {
    // 先把要关闭的 tab 列表抓出来，逐个淘汰缓存
    const toClose = tabs.value.filter((t) => t.key !== homePath.value && t.key !== key)
    toClose.forEach((t) => evictCache(t.fullPath))
    tabs.value = tabs.value.filter((t) => t.key === homePath.value || t.key === key)
    activeTab.value = key
    const tab = tabs.value.find((t) => t.key === key)
    router.push(tab?.fullPath || key)
  }

  function closeAll() {
    // 同样，先抓出要关闭的，再淘汰缓存
    const toClose = tabs.value.filter((t) => t.key !== homePath.value)
    toClose.forEach((t) => evictCache(t.fullPath))
    tabs.value = tabs.value.filter((t) => t.key === homePath.value)
    if (homePath.value) {
      activeTab.value = homePath.value
      router.push(homePath.value)
    }
  }

  /**
   * 拖拽排序：把 fromKey 移动到 toKey 的前面或后面
   * - home tab 永远锁在第一位，不可被拖动也不可被拖入
   * - side: 'before' | 'after' 决定插入位置
   * - 已经在目标位置则直接返回，避免 dragover 高频触发时重复写入
   */
  function moveTab(fromKey, toKey, side = 'before') {
    if (!fromKey || !toKey || fromKey === toKey) return
    if (fromKey === homePath.value || toKey === homePath.value) return

    const fromIdx = tabs.value.findIndex((t) => t.key === fromKey)
    const toIdx = tabs.value.findIndex((t) => t.key === toKey)
    if (fromIdx === -1 || toIdx === -1) return

    const rawInsertIdx = side === 'after' ? toIdx + 1 : toIdx
    const effectiveInsertIdx = rawInsertIdx > fromIdx ? rawInsertIdx - 1 : rawInsertIdx

    if (effectiveInsertIdx === fromIdx) return

    const [moved] = tabs.value.splice(fromIdx, 1)
    tabs.value.splice(effectiveInsertIdx, 0, moved)
  }

  function clearTabs() {
    tabs.value = []
    activeTab.value = ''
    homePath.value = ''
    sessionStorage.removeItem(STORAGE_KEY)
  }

  /**
   * 刷新指定 fullPath 的 tab —— 让 keep-alive 缓存失效，下次渲染时强制重新挂载组件
   * （触发 setup + onMounted 重新执行业务页面的初始化逻辑）。
   * 通常由 tab 上的双击事件触发。
   */
  function refreshTab(fullPath) {
    if (!fullPath) return
    refreshVersions[fullPath] = (refreshVersions[fullPath] || 0) + 1
  }

  return {
    tabs,
    activeTab,
    homePath,
    homeTitle,
    cachedViews,
    refreshVersions,
    setHome,
    addTab,
    removeTab,
    switchTab,
    closeOthers,
    closeAll,
    clearTabs,
    moveTab,
    refreshTab
  }
})
