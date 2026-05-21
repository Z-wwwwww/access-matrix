<script setup>
import { onMounted, ref } from 'vue'
import { useI18n } from 'vue-i18n'
import Card from '@/components/ui/Card.vue'
import Badge from '@/components/ui/Badge.vue'
import { useAuthStore } from '@/stores/auth'
import { getMeApi } from '../../../../services/auth'

const { t } = useI18n()
const authStore = useAuthStore()
const me = ref(null)
const loading = ref(true)

async function load() {
  loading.value = true
  try {
    const res = await getMeApi()
    if (res.data.code === 0) me.value = res.data.data
  } finally {
    loading.value = false
  }
}

onMounted(load)
</script>

<template>
  <Card class="p-6">
    <div class="flex items-center justify-between mb-4">
      <h1 class="text-lg font-semibold">{{ t('profile.title') }}</h1>
      <Badge v-if="me?.deptId" variant="secondary">{{ t('profile.label.deptId') }}: {{ me.deptId }}</Badge>
    </div>

    <div v-if="loading" class="text-sm text-muted-foreground">{{ t('common.message.loading') }}...</div>

    <div v-else-if="me" class="grid grid-cols-1 md:grid-cols-2 gap-x-8 gap-y-3 text-sm">
      <div class="flex"><span class="w-32 text-muted-foreground">{{ t('profile.label.userId') }}</span><span class="font-mono">{{ me.userId }}</span></div>
      <div class="flex"><span class="w-32 text-muted-foreground">{{ t('profile.label.username') }}</span><span>{{ me.username }}</span></div>
      <div class="flex"><span class="w-32 text-muted-foreground">{{ t('profile.label.displayName') }}</span><span>{{ me.displayName || '-' }}</span></div>
      <div class="flex"><span class="w-32 text-muted-foreground">{{ t('profile.label.email') }}</span><span>{{ me.email || '-' }}</span></div>
      <div class="flex"><span class="w-32 text-muted-foreground">{{ t('profile.label.userNo') }}</span><span class="font-mono">{{ me.userNo || '-' }}</span></div>
      <div class="flex"><span class="w-32 text-muted-foreground">{{ t('profile.label.tenantId') }}</span><span class="font-mono">{{ me.tenantId }}</span></div>
      <div class="flex col-span-1 md:col-span-2">
        <span class="w-32 text-muted-foreground">{{ t('profile.label.roles') }}</span>
        <div class="flex flex-wrap gap-1">
          <Badge v-for="r in me.roles" :key="r" variant="outline">{{ r }}</Badge>
          <span v-if="!me.roles?.length" class="text-muted-foreground">-</span>
        </div>
      </div>
      <div class="flex col-span-1 md:col-span-2">
        <span class="w-32 text-muted-foreground">{{ t('profile.label.authorities') }}</span>
        <div class="flex flex-wrap gap-1">
          <Badge v-for="a in me.authorities" :key="a" variant="outline" class="font-mono">{{ a }}</Badge>
          <span v-if="!me.authorities?.length" class="text-muted-foreground">-</span>
        </div>
      </div>
    </div>
  </Card>
</template>
