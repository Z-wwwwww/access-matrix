<script setup>
import { ref, watch } from 'vue'
import { useI18n } from 'vue-i18n'
import Drawer from '@/components/ui/Drawer.vue'
import Input from '@/components/ui/Input.vue'
import { toast } from '@/composables/useToast'
import { updateTenantApi } from '../../../../services/tenant'

/**
 * Inline edit drawer for a tenant's mutable fields (displayName, contactEmail).
 * tenantCode is intentionally read-only — renaming it would invalidate every
 * JWT's iss claim and break sessions across the SaaS install.
 *
 * Driven by a `row` prop instead of v-model:open + saved-prop combo so the
 * parent can pass the whole row in one shot and we don't have to re-fetch
 * just to populate the form.
 */
const { t } = useI18n()

const props = defineProps({
  row: { type: Object, default: null }   // null = closed; object = open with that row
})
const emit = defineEmits(['close', 'saved'])

const form = ref({ displayName: '', contactEmail: '' })
const saving = ref(false)
const errorMsg = ref('')

const isOpen = ref(false)

watch(() => props.row, (row) => {
  if (row) {
    form.value = {
      displayName: row.displayName || '',
      contactEmail: row.contactEmail || ''
    }
    errorMsg.value = ''
    isOpen.value = true
  } else {
    isOpen.value = false
  }
}, { immediate: true })

async function save() {
  errorMsg.value = ''
  if (!form.value.displayName?.trim()) {
    errorMsg.value = t('platform.tenant.edit.error.missingDisplayName')
    return
  }
  saving.value = true
  try {
    const res = await updateTenantApi(props.row.id, {
      displayName: form.value.displayName.trim(),
      contactEmail: form.value.contactEmail?.trim() || null
    })
    if (res.data.code === 0) {
      toast.success(t('platform.tenant.message.updateSuccess'))
      emit('saved')
    } else {
      errorMsg.value = res.data.msg || t('platform.tenant.message.updateFailed')
    }
  } catch (e) {
    errorMsg.value = e.message || t('platform.tenant.message.updateFailed')
  } finally {
    saving.value = false
  }
}

function onClose(v) {
  if (!v) emit('close')
}
</script>

<template>
  <Drawer
    :open="isOpen"
    :title="t('platform.tenant.edit.titleEdit')"
    width="max-w-md"
    @update:open="onClose"
  >
    <div v-if="row" class="space-y-4">
      <p class="text-xs text-muted-foreground leading-relaxed">
        {{ t('platform.tenant.edit.editIntro') }}
      </p>

      <div v-if="errorMsg"
           class="p-3 rounded-lg bg-destructive/10 text-destructive text-xs border border-destructive/20">
        {{ errorMsg }}
      </div>

      <!-- Read-only tenantCode shown so the operator knows which tenant they're editing. -->
      <div>
        <label class="text-xs text-muted-foreground block mb-1">
          {{ t('platform.tenant.edit.label.tenantCode') }}
        </label>
        <Input :model-value="row.tenantCode" disabled />
        <p class="text-[11px] text-muted-foreground mt-1">{{ t('platform.tenant.edit.hint.tenantCode') }}</p>
      </div>

      <div>
        <label class="text-xs text-muted-foreground block mb-1">
          {{ t('platform.tenant.edit.label.displayName') }} <span class="text-destructive">*</span>
        </label>
        <Input v-model="form.displayName" />
      </div>

      <div>
        <label class="text-xs text-muted-foreground block mb-1">
          {{ t('platform.tenant.edit.label.contactEmail') }}
        </label>
        <Input v-model="form.contactEmail" type="email"
               :placeholder="t('platform.tenant.edit.placeholder.contactEmail')" />
      </div>
    </div>

    <template #footer>
      <div class="flex justify-end gap-2">
        <button class="h-9 px-3 rounded border border-border text-sm"
                @click="emit('close')">{{ t('common.button.cancel') }}</button>
        <button class="h-9 px-3 rounded bg-primary text-primary-foreground text-sm disabled:opacity-50"
                :disabled="saving" @click="save">
          {{ saving ? t('platform.tenant.edit.saving') : t('common.button.save') }}
        </button>
      </div>
    </template>
  </Drawer>
</template>
