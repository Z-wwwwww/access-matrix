<script setup>
import { useRouter } from 'vue-router'

const router = useRouter()
</script>

<template>
  <div class="min-h-screen flex flex-col items-center justify-center bg-gray-50">
    <h1 class="text-6xl font-bold text-gray-300">404</h1>
    <p class="mt-4 text-gray-500">页面不存在</p>
    <button
      class="mt-6 px-6 py-2 bg-primary text-white rounded-lg hover:bg-primary-hover transition"
      @click="router.push('/')"
    >
      返回首页
    </button>
  </div>
</template>
