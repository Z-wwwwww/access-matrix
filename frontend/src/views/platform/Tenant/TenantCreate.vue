<script setup>
import { computed, ref, watch } from 'vue'
import { useI18n } from 'vue-i18n'
import Drawer from '@/components/ui/Drawer.vue'
import Input from '@/components/ui/Input.vue'
import { toast } from '@/composables/useToast'
import { createTenantApi } from '../../../../services/tenant'

const { t } = useI18n()

const props = defineProps({ open: Boolean })
const emit = defineEmits(['update:open', 'saved'])

const form = ref({
  tenantCode: '',
  displayName: '',
  contactEmail: '',
  adminUsername: ''   // blank = derive from contactEmail local-part server-side
})
const saving = ref(false)
const errorMsg = ref('')

// RFC1035 label — must match the backend's CreateRequest @Pattern.
const TENANT_CODE_RE = /^[a-z0-9](?:[a-z0-9-]{0,61}[a-z0-9])?$/
// Mirror backend's adminUsername @Pattern: lowercase alphanumeric + dash/underscore,
// 1..64 chars, must start alphanumeric.
const USERNAME_RE = /^[a-z0-9][a-z0-9_-]{0,63}$/

watch(() => props.open, (v) => {
  if (!v) return
  form.value = { tenantCode: '', displayName: '', contactEmail: '', adminUsername: '' }
  errorMsg.value = ''
})

// Show the derived default while the operator hasn't typed an override.
// Pure UI hint — the actual derivation runs server-side so the displayed
// value can drift if it does too much sanitisation; keep it simple.
const derivedUsername = computed(() => {
  const email = form.value.contactEmail?.trim() || ''
  const at = email.indexOf('@')
  if (at < 0) return ''
  const local = email.slice(0, at).toLowerCase().replace(/[^a-z0-9_-]/g, '')
  // strip leading separators to match backend rule
  return local.replace(/^[-_]+/, '') || 'admin'
})

async function save() {
  errorMsg.value = ''
  const code = (form.value.tenantCode || '').trim().toLowerCase()
  if (!TENANT_CODE_RE.test(code)) {
    errorMsg.value = t('platform.tenant.edit.error.invalidCode')
    return
  }
  if (!form.value.displayName?.trim()) {
    errorMsg.value = t('platform.tenant.edit.error.missingDisplayName')
    return
  }
  const overrideUsername = form.value.adminUsername?.trim() || ''
  if (overrideUsername && !USERNAME_RE.test(overrideUsername)) {
    errorMsg.value = t('platform.tenant.edit.error.invalidAdminUsername')
    return
  }
  saving.value = true
  try {
    const res = await createTenantApi({
      tenantCode: code,
      displayName: form.value.displayName.trim(),
      contactEmail: form.value.contactEmail?.trim() || null,
      // Empty string → server derives from email. Don't send a literal "" or
      // the @Pattern's "^$" branch matches but the server's resolveAdminUsername
      // sees a blank value and falls back to derivation either way; null is cleaner.
      adminUsername: overrideUsername || null
    })
    if (res.data.code === 0) {
      toast.success(t('platform.tenant.message.createSuccess'))
      emit('saved')
      emit('update:open', false)
    } else {
      errorMsg.value = res.data.msg || t('platform.tenant.message.createFailed')
    }
  } catch (e) {
    errorMsg.value = e.message || t('platform.tenant.message.createFailed')
  } finally {
    saving.value = false
  }
}
</script>

<template>
  <Drawer
    :open="open"
    :title="t('platform.tenant.edit.titleCreate')"
    width="max-w-md"
    @update:open="(v) => emit('update:open', v)"
  >
    <div class="space-y-4">
      <p class="text-xs text-muted-foreground leading-relaxed">
        {{ t('platform.tenant.edit.intro') }}
      </p>

      <div v-if="errorMsg"
           class="p-3 rounded-lg bg-destructive/10 text-destructive text-xs border border-destructive/20">
        {{ errorMsg }}
      </div>

      <div>
        <label class="text-xs text-muted-foreground block mb-1">
          {{ t('platform.tenant.edit.label.tenantCode') }} <span class="text-destructive">*</span>
        </label>
        <Input v-model="form.tenantCode"
               :placeholder="t('platform.tenant.edit.placeholder.tenantCode')" />
        <p class="text-[11px] text-muted-foreground mt-1">{{ t('platform.tenant.edit.hint.tenantCode') }}</p>
      </div>

      <div>
        <label class="text-xs text-muted-foreground block mb-1">
          {{ t('platform.tenant.edit.label.displayName') }} <span class="text-destructive">*</span>
        </label>
        <Input v-model="form.displayName"
               :placeholder="t('platform.tenant.edit.placeholder.displayName')" />
      </div>

      <div>
        <label class="text-xs text-muted-foreground block mb-1">
          {{ t('platform.tenant.edit.label.contactEmail') }}
        </label>
        <Input v-model="form.contactEmail" type="email"
               :placeholder="t('platform.tenant.edit.placeholder.contactEmail')" />
        <p class="text-[11px] text-muted-foreground mt-1">{{ t('platform.tenant.edit.hint.contactEmail') }}</p>
      </div>

      <div>
        <label class="text-xs text-muted-foreground block mb-1">
          {{ t('platform.tenant.edit.label.adminUsername') }}
        </label>
        <Input v-model="form.adminUsername"
               :placeholder="derivedUsername || t('platform.tenant.edit.placeholder.adminUsername')" />
        <p class="text-[11px] text-muted-foreground mt-1">{{ t('platform.tenant.edit.hint.adminUsername') }}</p>
      </div>
    </div>

    <template #footer>
      <div class="flex justify-end gap-2">
        <button class="h-9 px-3 rounded border border-border text-sm"
                @click="emit('update:open', false)">{{ t('common.button.cancel') }}</button>
        <button class="h-9 px-3 rounded bg-primary text-primary-foreground text-sm disabled:opacity-50"
                :disabled="saving" @click="save">
          {{ saving ? t('platform.tenant.edit.saving') : t('common.button.save') }}
        </button>
      </div>
    </template>
  </Drawer>
</template>
