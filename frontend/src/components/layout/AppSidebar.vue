<script setup>
import { computed, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { useI18n } from 'vue-i18n'
import { useMenuStore } from '@/stores/menu'
import { useFavoriteMenus } from '@/composables/useFavoriteMenus'
import { useMenuTitle } from '@/composables/useMenuTitle'
import { Star, Pin } from 'lucide-vue-next'
import LucideIcon from '@/components/shared/LucideIcon.vue'
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

const { favoriteSet, toggleFavorite } = useFavoriteMenus()
const { translate: translateMenu } = useMenuTitle()

/**
 * 置顶菜单：管理員在菜单管理页面勾选了 pinned 的项
 * 递归遍历整棵树，深层节点也可以被置顶
 */
const pinnedMenus = computed(() => {
  const result = []
  function walk(items) {
    for (const item of items) {
      if (item.hide) continue
      if (item.pinned === 1 || item.pinned === true) result.push(item)
      if (item.children && item.children.length) walk(item.children)
    }
  }
  walk(menus.value)
  return result
})

/**
 * 收藏菜单：递归遍历整棵树，找到所有用户收藏的项。
 * 排除已置顶的菜单（置顶优先级高于收藏，避免同一菜单在置顶区和收藏区重复）。
 */
const favoriteMenus = computed(() => {
  const result = []
  function walk(items) {
    for (const item of items) {
      if (item.hide) continue
      if (item.pinned === 1 || item.pinned === true) {
        // 置顶项跳过自身（已在置顶区展示），但仍递归找其子孙中的收藏
        if (item.children && item.children.length) walk(item.children)
        continue
      }
      if (item.id != null && favoriteSet.has(String(item.id))) {
        result.push(item)
      }
      if (item.children && item.children.length) walk(item.children)
    }
  }
  walk(menus.value)
  return result
})

/**
 * 渲染用菜单树：递归剔除所有 pinned 和已收藏的节点，并删除变空的目录。
 * 下游 topLevelLeafs / remainingTopLevel / adminMenus / footerMenus 和
 * AppSidebarItem 都基于这棵树渲染 —— 这样置顶/收藏的菜单不会同时出现
 * 在顶部独立区域和它原所在目录里。
 */
const displayMenus = computed(() => {
  function prune(items) {
    const out = []
    for (const item of items) {
      if (item.pinned === 1 || item.pinned === true) continue
      const idStr = item.id != null ? String(item.id) : null
      if (idStr && favoriteSet.has(idStr)) continue
      const hadChildren = item.children && item.children.length > 0
      if (hadChildren) {
        const prunedChildren = prune(item.children)
        // 原本是目录，但所有子项都被剪除后变成空目录 → 整个跳过，
        // 避免父目录被 topLevelLeafs 误识别为叶子按钮再渲染一次。
        if (prunedChildren.length === 0) continue
        out.push({ ...item, children: prunedChildren })
      } else {
        out.push({ ...item })
      }
    }
    return out
  }
  return prune(menus.value)
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
  displayMenus.value.filter(
    (m) =>
      !m.hide &&
      (!m.children || m.children.length === 0) &&
      (m.sort ?? 0) <= ADMIN_GROUP_MIN_SORT
  )
)

/**
 * 顶部区域中的分组（带子项）一级菜单
 */
const remainingTopLevel = computed(() =>
  displayMenus.value.filter((m) => {
    if (m.hide) return false
    if ((m.sort ?? 0) > ADMIN_GROUP_MIN_SORT) return false
    return m.children && m.children.length > 0
  })
)

/**
 * 管理者設定 分组：9000 < sort <= 10000 的一级菜单
 */
const adminMenus = computed(() =>
  displayMenus.value.filter((m) => {
    if (m.hide) return false
    const s = m.sort ?? 0
    return s > ADMIN_GROUP_MIN_SORT && s <= FOOTER_MIN_SORT
  })
)

/**
 * 固定底部菜单：sort > 10000 的一级菜单
 */
const footerMenus = computed(() =>
  displayMenus.value.filter(
    (m) => !m.hide && (m.sort ?? 0) > FOOTER_MIN_SORT
  )
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
      <!-- Favorites (用户收藏，置于最顶部；右侧 Star 默认淡色，hover 显示完全) -->
      <template v-if="favoriteMenus.length">
        <button
          v-for="fav in favoriteMenus"
          :key="'fav-' + fav.id"
          :title="props.collapsed ? translateMenu(fav) : ''"
          class="group w-full flex items-center gap-2 px-3 py-2 rounded-lg text-sm font-medium transition-colors cursor-pointer"
          :class="
            isActive(fav.path)
              ? 'bg-brand-orange text-white shadow-sm'
              : 'text-foreground hover:bg-muted hover:text-foreground'
          "
          @click="navigate(fav)"
        >
          <LucideIcon
            v-if="fav.icon"
            :name="fav.icon"
            :size="16"
            class="shrink-0"
          />
          <Star
            v-else
            :size="14"
            class="shrink-0 text-amber-400 fill-amber-400"
          />
          <span class="flex-1 truncate text-left" :class="props.collapsed ? 'sr-only' : ''">
            {{ translateMenu(fav) }}
          </span>
          <span
            v-if="!props.collapsed"
            role="button"
            :aria-label="t('layout.sidebar.unfavorite')"
            :title="t('layout.sidebar.unfavorite')"
            class="shrink-0 -mr-1 p-0.5 rounded inline-flex opacity-40 group-hover:opacity-100 transition-opacity hover:bg-foreground/10"
            @click.stop="toggleFavorite(fav.id)"
          >
            <Star :size="14" class="fill-amber-400 text-amber-400" />
          </span>
        </button>
        <div class="my-2 border-t-2 border-border/60" />
      </template>

      <!-- 置顶菜单 (admin 配置 pinned=1，放在收藏区下面、常规菜单上面) -->
      <template v-if="pinnedMenus.length">
        <button
          v-for="p in pinnedMenus"
          :key="'pin-' + p.id"
          :title="props.collapsed ? translateMenu(p) : ''"
          class="w-full flex items-center gap-2 px-3 py-2 rounded-lg text-sm font-medium transition-colors cursor-pointer"
          :class="
            isActive(p.path)
              ? 'bg-brand-orange text-white shadow-sm'
              : 'text-foreground hover:bg-muted hover:text-foreground'
          "
          @click="navigate(p)"
        >
          <LucideIcon
            v-if="p.icon"
            :name="p.icon"
            :size="16"
            class="shrink-0"
          />
          <Pin
            v-else
            :size="14"
            class="shrink-0"
            :class="
              isActive(p.path)
                ? 'text-white fill-white'
                : 'text-brand-orange fill-brand-orange'
            "
          />
          <span class="flex-1 truncate text-left" :class="props.collapsed ? 'sr-only' : ''">
            {{ translateMenu(p) }}
          </span>
        </button>
        <div class="my-2 border-t-2 border-border/60" />
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
        <div v-if="remainingTopLevel.length" class="my-2 border-t-2 border-border/60" />
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
        <div class="my-2 border-t-2 border-border/60" />
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
      class="shrink-0 border-t-2 border-border/60 p-2 space-y-0.5"
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
