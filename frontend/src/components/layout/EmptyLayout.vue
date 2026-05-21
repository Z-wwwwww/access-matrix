<script>
export default { name: 'EmptyLayout' }
</script>

<script setup>
import { computed } from 'vue'
import { useRoute } from 'vue-router'
import { useTabsStore } from '@/stores/tabs'

const route = useRoute()
const tabsStore = useTabsStore()

// cacheKey = fullPath 加上 tab 的刷新版本号。
// 双击 tab 触发 tabsStore.refreshTab(fullPath) 时版本号 +1，cacheKey 变化，
// keep-alive 视为新 vnode 重新挂载组件，业务页面 setup + onMounted 重新执行。
const cacheKey = computed(() => {
  const v = tabsStore.refreshVersions[route.fullPath] || 0
  return v > 0 ? `${route.fullPath}#${v}` : route.fullPath
})
</script>

<template>
  <!-- EmptyLayout 是后端菜单里目录节点的占位组件（menu-to-routes.js 里所有
       无 component 的菜单节点都被赋予它），路由层级越深嵌套越多。
       关键：每一层目录都必须自带 keep-alive，否则 AppLayout 顶层的 keep-alive
       只能缓存最外层的 EmptyLayout 自身，无法缓存任何业务叶子组件。
       :key 含 fullPath + 刷新版本号：detail?id=1 / detail?id=2 各成独立缓存,
       双击 tab 改变版本号即可强制重新挂载。
       :max="50" 防止长时间运行后无限增长。 -->
  <router-view v-slot="{ Component }">
    <keep-alive :max="50">
      <component :is="Component" :key="cacheKey" />
    </keep-alive>
  </router-view>
</template>
