<script setup>
import { useRoute } from 'vue-router'
import { useI18n } from 'vue-i18n'

const route = useRoute()
const { t } = useI18n()
const src = route.meta?.iframeSrc || ''
</script>

<template>
  <iframe v-if="src" :src="src" class="w-full h-full border-0" />
  <div v-else class="p-6 text-gray-400">{{ t('common.message.iframeLoadFailed') }}</div>
</template>
