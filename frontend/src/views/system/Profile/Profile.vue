<script setup>
import { onMounted, ref, reactive, computed } from 'vue'
import { useI18n } from 'vue-i18n'
import Card from '@/components/ui/Card.vue'
import Badge from '@/components/ui/Badge.vue'
import Input from '@/components/ui/Input.vue'
import { useAuthStore } from '@/stores/auth'
import { getMeApi, updateMyProfileApi } from '@/services/auth'
import { getDeptTreeApi } from '@/services/dept'
import { toast } from '@/composables/useToast'
import { isValidEmail } from '@/lib/validators'

const { t } = useI18n()
const authStore = useAuthStore()
const me = ref(null)
const loading = ref(true)
const saving = ref(false)
const deptNameMap = ref(new Map())

// Editable contact fields — the only things a user may change about themselves.
// Dept / status / roles are admin-only, and the admin user console refuses any
// self-targeted change, so this page is the sanctioned self-edit surface.
const form = reactive({ displayName: '', email: '' })

const deptDisplay = computed(() => {
  const id = me.value?.deptId
  if (id == null || id === '') return ''
  return deptNameMap.value.get(id) || ''
})

async function load() {
  loading.value = true
  try {
    const [meRes, deptRes] = await Promise.all([getMeApi(), getDeptTreeApi()])
    if (meRes.data.code === 0) {
      me.value = meRes.data.data
      form.displayName = me.value.displayName || ''
      form.email = me.value.email || ''
    }
    if (deptRes.data.code === 0) {
      const map = new Map()
      function walk(nodes) {
        for (const n of nodes) {
          map.set(n.id, n.name)
          if (n.children?.length) walk(n.children)
        }
      }
      walk(deptRes.data.data || [])
      deptNameMap.value = map
    }
  } finally {
    loading.value = false
  }
}

async function save() {
  if (form.email && !isValidEmail(form.email)) {
    toast.error(t('common.message.invalidEmail'))
    return
  }
  saving.value = true
  try {
    const res = await updateMyProfileApi({ email: form.email, displayName: form.displayName })
    if (res.data.code !== 0) { toast.error(res.data.msg); return }
    toast.success(t('common.message.saveSuccessful'))
    // Refresh the cached profile so the header / display name update everywhere.
    await authStore.fetchUserInfo()
    await load()
  } catch (e) { toast.error(e.message) }
  finally { saving.value = false }
}

onMounted(load)
</script>

<template>
  <Card class="p-6">
    <div class="flex items-center justify-between mb-4">
      <h1 class="text-lg font-semibold">{{ t('profile.title') }}</h1>
      <Badge v-if="deptDisplay" variant="secondary">{{ t('profile.label.deptId') }}: {{ deptDisplay }}</Badge>
    </div>

    <div v-if="loading" class="text-sm text-muted-foreground">{{ t('common.message.loading') }}...</div>

    <div v-else-if="me" class="space-y-5">
      <!-- Read-only identity -->
      <div class="grid grid-cols-1 md:grid-cols-2 gap-x-8 gap-y-3 text-sm">
        <div class="flex"><span class="w-32 text-muted-foreground">{{ t('profile.label.username') }}</span><span>{{ me.username }}</span></div>
        <div class="flex"><span class="w-32 text-muted-foreground">{{ t('profile.label.userNo') }}</span><span class="font-mono">{{ me.userNo || '-' }}</span></div>
        <div class="flex"><span class="w-32 text-muted-foreground">{{ t('profile.label.tenantId') }}</span><span class="font-mono">{{ me.tenantId }}</span></div>
        <div class="flex col-span-1 md:col-span-2">
          <span class="w-32 text-muted-foreground">{{ t('profile.label.roles') }}</span>
          <div class="flex flex-wrap gap-1">
            <Badge v-for="r in me.roleNames" :key="r" variant="outline">{{ r }}</Badge>
            <span v-if="!me.roleNames?.length" class="text-muted-foreground">-</span>
          </div>
        </div>
      </div>

      <!-- Editable contact fields -->
      <div class="border-t border-border pt-4">
        <div class="text-xs text-muted-foreground mb-3">{{ t('profile.editHint') }}</div>
        <div class="grid grid-cols-1 md:grid-cols-2 gap-4 max-w-2xl">
          <div>
            <label class="text-xs text-muted-foreground block mb-1">{{ t('profile.label.displayName') }}</label>
            <Input v-model="form.displayName" />
          </div>
          <div>
            <label class="text-xs text-muted-foreground block mb-1">{{ t('profile.label.email') }}</label>
            <Input v-model="form.email" type="email" />
          </div>
        </div>
        <div class="mt-4">
          <button
            class="h-9 px-4 rounded bg-primary text-primary-foreground text-sm disabled:opacity-50 disabled:cursor-not-allowed"
            :disabled="saving"
            @click="save">
            {{ saving ? t('user.edit.message.saving') : t('common.button.save') }}
          </button>
        </div>
      </div>
    </div>
  </Card>
</template>
