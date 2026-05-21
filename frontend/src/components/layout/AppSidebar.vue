<script setup>
import { computed, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { useI18n } from 'vue-i18n'
import { useMenuStore } from '@/stores/menu'
import { useFavoriteMenus } from '@/composables/useFavoriteMenus'
import { useMenuTitle } from '@/composables/useMenuTitle'
import { Star } from 'lucide-vue-next'
import AppSidebarItem from './AppSidebarItem.vue'

const props = defineProps({
  collapsed: {
    type: Boolean,
    default: false
  },
  mobileOpen: {
    type: Boolean,
    default: false
  }
})

const { t } = useI18n()
const route = useRoute()
const router = useRouter()
const menuStore = useMenuStore()

const menus = computed(() => menuStore.menus || [])
const expandedKeys = ref(new Set())

const { favoriteSet } = useFavoriteMenus()
const { translate: translateMenu } = useMenuTitle()

/**
 * 扁平遍历菜单，返回所有被标记为"常用"的节点（保留原菜单对象引用）
 * 排除隐藏项
 */
const favoriteMenus = computed(() => {
  const result = []
  function walk(items) {
    for (const item of items) {
      if (item.hide) continue
      if (item.id != null && favoriteSet.value.has(String(item.id))) {
        result.push(item)
      }
      if (item.children && item.children.length) walk(item.children)
    }
  }
  walk(menus.value)
  return result
})

/**
 * Sort 阈值划分：
 * - sort <= 9000：常规一级菜单（顶部区域）
 * - 9000 < sort <= 10000：管理者設定 分组
 * - sort > 10000：固定底部区域
 */
const ADMIN_GROUP_MIN_SORT = 9000
const FOOTER_MIN_SORT = 10000

/**
 * 被提升到侧边栏顶部的叶子按钮：
 * 一级菜单中非隐藏、无子项、sort <= 9000、且未被收藏的项
 */
const topLevelLeafs = computed(() =>
  menus.value.filter(
    (m) =>
      !m.hide &&
      (!m.children || m.children.length === 0) &&
      (m.sort ?? 0) <= ADMIN_GROUP_MIN_SORT &&
      !favoriteSet.value.has(String(m.id))
  )
)

/**
 * 顶部区域中的分组（带子项）一级菜单
 */
const remainingTopLevel = computed(() =>
  menus.value.filter((m) => {
    if (m.hide) return false
    if ((m.sort ?? 0) > ADMIN_GROUP_MIN_SORT) return false
    return m.children && m.children.length > 0
  })
)

/**
 * 管理者設定 分组：9000 < sort <= 10000 的一级菜单
 */
const adminMenus = computed(() =>
  menus.value.filter((m) => {
    if (m.hide) return false
    const s = m.sort ?? 0
    return s > ADMIN_GROUP_MIN_SORT && s <= FOOTER_MIN_SORT
  })
)

/**
 * 固定底部菜单：sort > 10000 的一级菜单
 */
const footerMenus = computed(() =>
  menus.value.filter((m) => !m.hide && (m.sort ?? 0) > FOOTER_MIN_SORT)
)

/**
 * 根据当前路由路径，找到所有需要展开的父级菜单路径
 */
function findParentPaths(items, targetPath, parents = []) {
  for (const item of items) {
    if (item.path === targetPath) {
      return [...parents]
    }
    if (item.children && item.children.length) {
      const result = findParentPaths(item.children, targetPath, [...parents, item.path])
      if (result) return result
    }
  }
  // 前缀匹配兜底：当前路径以某个菜单 path 开头
  for (const item of items) {
    if (targetPath.startsWith(item.path + '/')) {
      return [...parents, item.path]
    }
    if (item.children && item.children.length) {
      const result = findParentPaths(item.children, targetPath, [...parents, item.path])
      if (result) return result
    }
  }
  return null
}

function syncExpandedKeys() {
  if (!menus.value.length) return
  const parents = findParentPaths(menus.value, route.path)
  if (parents && parents.length) {
    const keys = new Set(expandedKeys.value)
    parents.forEach((p) => keys.add(p))
    expandedKeys.value = keys
  }
}

// 路由变化时自动展开对应父菜单
watch(() => route.path, syncExpandedKeys, { immediate: true })
// 菜单数据加载完成时也同步一次
watch(menus, syncExpandedKeys)

function toggleExpand(path) {
  if (expandedKeys.value.has(path)) {
    expandedKeys.value.delete(path)
  } else {
    expandedKeys.value.add(path)
  }
}

function isActive(path) {
  return route.path === path || route.path.startsWith(path + '/')
}

/**
 * 叶子菜单点击：target 为 '_blank' / 2 时新标签打开外部リンク，否则路由跳转
 * （後端は target を '_blank' / '_self' 文字列で返す）
 */
function navigate(item) {
  const t = item.target
  if (t === '_blank' || t === 2 || t === '2') {
    window.open(item.path, '_blank', 'noopener,noreferrer')
  } else {
    router.push(item.path)
  }
}
</script>

<template>
  <aside
    class="sticky top-14 self-start h-[calc(100vh-3.5rem)] bg-card border-r border-border flex flex-col transition-all duration-200 overflow-hidden shrink-0"
    :class="[
      props.collapsed ? 'w-14' : 'w-56',
      props.mobileOpen
        ? 'fixed left-0 top-14 z-50 w-56 shadow-xl lg:relative lg:shadow-none'
        : 'hidden lg:flex'
    ]"
  >
    <!-- Nav items -->
    <nav class="flex-1 overflow-y-auto scrollbar-none p-2 space-y-0.5">
      <!-- Favorites (一级菜单样式，不展开子级) -->
      <template v-if="favoriteMenus.length">
        <button
          v-for="fav in favoriteMenus"
          :key="'fav-' + fav.id"
          :title="props.collapsed ? translateMenu(fav) : ''"
          class="w-full flex items-center gap-2 px-3 py-2 rounded-lg text-sm font-medium transition-colors cursor-pointer"
          :class="
            isActive(fav.path)
              ? 'bg-brand-orange text-white shadow-sm'
              : 'text-foreground hover:bg-muted hover:text-foreground'
          "
          @click="navigate(fav)"
        >
          <Star :size="14" class="shrink-0 text-amber-400 fill-amber-400" />
          <span class="flex-1 truncate text-left" :class="props.collapsed ? 'sr-only' : ''">
            {{ translateMenu(fav) }}
          </span>
        </button>
        <div class="my-2 border-t border-border" />
      </template>

      <!-- Top-level leaf items (一级菜单中无子项の按钮、AppSidebarItem に委譲して collapsed 時 flyout を共通化) -->
      <template v-if="topLevelLeafs.length">
        <AppSidebarItem
          v-for="leaf in topLevelLeafs"
          :key="'leaf-' + leaf.path"
          :item="leaf"
          :depth="0"
          :collapsed="props.collapsed"
          :expanded-keys="expandedKeys"
          @toggle="toggleExpand"
        />
        <div v-if="remainingTopLevel.length" class="my-2 border-t border-border" />
      </template>

      <AppSidebarItem
        v-for="item in remainingTopLevel"
        :key="item.path"
        :item="item"
        :depth="0"
        :collapsed="props.collapsed"
        :expanded-keys="expandedKeys"
        @toggle="toggleExpand"
      />

      <!-- 管理者設定 分组 (9000 < sort <= 10000) -->
      <template v-if="adminMenus.length">
        <div class="my-2 border-t border-border" />
        <div
          v-if="!props.collapsed"
          class="px-3 pt-1 pb-1.5 text-xs font-medium text-muted-foreground"
        >
          {{ t('layout.sidebar.adminGroup') }}
        </div>
        <AppSidebarItem
          v-for="item in adminMenus"
          :key="'admin-' + item.path"
          :item="item"
          :depth="0"
          :collapsed="props.collapsed"
          :expanded-keys="expandedKeys"
          @toggle="toggleExpand"
        />
      </template>
    </nav>

    <!-- 固定底部菜单 (sort > 10000) -->
    <div
      v-if="footerMenus.length"
      class="shrink-0 border-t border-border p-2 space-y-0.5"
    >
      <AppSidebarItem
        v-for="item in footerMenus"
        :key="'footer-' + item.path"
        :item="item"
        :depth="0"
        :collapsed="props.collapsed"
        :expanded-keys="expandedKeys"
        @toggle="toggleExpand"
      />
    </div>
  </aside>
</template>
