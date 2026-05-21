<script setup>
import { ref, computed, provide, watch } from 'vue'
import { useRoute } from 'vue-router'
import { useI18n } from 'vue-i18n'
import { useTabsStore } from '@/stores/tabs'
import AppSidebar from './AppSidebar.vue'
import AppHeader from './AppHeader.vue'
import AppTabBar from './AppTabBar.vue'
import ToastContainer from '@/components/shared/ToastContainer.vue'
import ConfirmDialog from '@/components/shared/ConfirmDialog.vue'

const { t } = useI18n()
const route = useRoute()
const tabsStore = useTabsStore()

const collapsed = ref(false)
const mobileOpen = ref(false)

function toggleSidebar() {
  if (window.innerWidth >= 1024) {
    collapsed.value = !collapsed.value
  } else {
    mobileOpen.value = !mobileOpen.value
  }
}

function closeMobile() {
  mobileOpen.value = false
}

provide('sidebar', { collapsed, mobileOpen, toggleSidebar, closeMobile })

// Track opened pages — 监听 fullPath 而非 path，
// 这样同 path 不同 query（如 detail?id=1 / detail?id=2）也会触发 addTab
watch(
  () => route.fullPath,
  () => {
    if (route.path && route.matched.length) {
      tabsStore.addTab(route)
    }
  },
  { immediate: true }
)

// 顶层 keep-alive cache key —— 后端菜单大量使用扁平路由（叶子直接挂 AppLayout，
// 不走 EmptyLayout），所以 EmptyLayout 内层的 cacheKey 机制对这些路由失效。
// 在此处再做一层同样的版本号 key，关 tab 时 evictCache 让 key 变化 →
// keep-alive 视为新 vnode 重新挂载，避免再次打开 tab 时搜索表单等残留旧输入。
const cacheKey = computed(() => {
  const fp = route.fullPath
  const v = tabsStore.refreshVersions[fp] || 0
  return v > 0 ? `${fp}#${v}` : fp
})

// 路由 meta 中的 hideSidebar / hideFooter 标志（菜单管理里配置，
// 经 utils/menu-to-routes.js 写入 route.meta），允许某些页面（打印预览、
// 全屏向导等）以更干净的形态展示。
const hideSidebar = computed(() => route.meta?.hideSidebar === true)
const hideFooter = computed(() => route.meta?.hideFooter === true)
</script>

<template>
  <div class="min-h-screen flex flex-col bg-background">
    <!-- Header -->
    <AppHeader :collapsed="collapsed" @toggle-sidebar="toggleSidebar" />

    <div class="flex flex-1 relative">
      <!-- Mobile overlay -->
      <div
        v-if="mobileOpen && !hideSidebar"
        class="fixed inset-0 z-40 bg-black/50 lg:hidden"
        @click="closeMobile"
      />

      <!-- Sidebar -->
      <AppSidebar v-if="!hideSidebar" :collapsed="collapsed" :mobile-open="mobileOpen" />

      <!-- Right: tabs + content -->
      <div class="flex-1 flex flex-col min-w-0">
        <!-- Tab bar -->
        <AppTabBar />

        <!-- Main content (window 滚动: main 不再是独立滚动容器) -->
        <main class="flex-1 min-w-0 px-3 pb-3 md:px-4 md:pb-4 lg:px-6 lg:pb-6 pt-2">
          <router-view v-slot="{ Component }">
            <keep-alive :max="50">
              <component :is="Component" :key="cacheKey" />
            </keep-alive>
          </router-view>
        </main>

        <!-- Footer (仅在主内容区下方，不延伸到 sidebar) -->
        <footer
          v-if="!hideFooter"
          class="text-center text-[11px] text-muted-foreground py-2 border-t border-border bg-background shrink-0"
        >
          {{ t('layout.footer.copyright') }}
        </footer>
      </div>
    </div>
    <!-- Global toast notifications -->
    <ToastContainer />
    <!-- Global confirm dialog -->
    <ConfirmDialog />
  </div>
</template>
