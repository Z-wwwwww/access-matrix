<script setup>
import { useRouter } from 'vue-router'
import { useI18n } from 'vue-i18n'

const router = useRouter()
const { t } = useI18n()
</script>

<template>
  <div class="min-h-screen flex flex-col items-center justify-center bg-gray-50">
    <h1 class="text-6xl font-bold text-gray-300">{{ t('notFound.title') }}</h1>
    <p class="mt-4 text-gray-500">{{ t('notFound.message') }}</p>
    <button
      class="mt-6 px-6 py-2 bg-primary text-white rounded-lg hover:bg-primary-hover transition"
      @click="router.push('/')"
    >
      {{ t('notFound.backHome') }}
    </button>
  </div>
</template>
