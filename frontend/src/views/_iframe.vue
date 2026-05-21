<script setup>
import { useRoute } from 'vue-router'

const route = useRoute()
const src = route.meta?.iframeSrc || ''
</script>

<template>
  <iframe v-if="src" :src="src" class="w-full h-full border-0" />
  <div v-else class="p-6 text-gray-400">无法加载外部页面</div>
</template>
