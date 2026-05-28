<script setup>
import { computed, ref, watch } from 'vue'
import { useI18n } from 'vue-i18n'
import Drawer from '@/components/ui/Drawer.vue'
import Input from '@/components/ui/Input.vue'
import { toast } from '@/composables/useToast'
import { hardDeleteTenantApi } from '../../../../services/tenant'
import { Trash2, AlertOctagon } from 'lucide-vue-next'

/**
 * "Empty recycle bin" modal. Only opened from a row whose tenant is
 * already suspended (status=0). Two-step gate:
 *   1. operator must read the warning copy
 *   2. operator must type the tenantCode exactly into the input
 * Both UI gates + backend confirmCode re-validation make casual / typo
 * mistakes hard to land.
 *
 * No undo. Drops business data across every per-tenant table, the KC
 * realm, and the registry row.
 */
const { t } = useI18n()

const props = defineProps({
  row: { type: Object, default: null }
})
const emit = defineEmits(['close', 'deleted'])

const typed = ref('')
const isOpen = ref(false)
const submitting = ref(false)

watch(() => props.row, (row) => {
  if (row) {
    typed.value = ''
    submitting.value = false
    isOpen.value = true
  } else {
    isOpen.value = false
  }
}, { immediate: true })

const canSubmit = computed(() =>
  !!props.row && typed.value === props.row.tenantCode && !submitting.value)

async function submit() {
  if (!canSubmit.value) return
  submitting.value = true
  try {
    const res = await hardDeleteTenantApi(props.row.id, typed.value)
    if (res.data.code === 0) {
      toast.success(t('platform.tenant.hardDelete.message.success', {
        tenantCode: props.row.tenantCode
      }))
      emit('deleted')
    } else {
      toast.error(res.data.msg || t('platform.tenant.hardDelete.message.failed'))
    }
  } catch (e) {
    toast.error(e.message || t('platform.tenant.hardDelete.message.failed'))
  } finally {
    submitting.value = false
  }
}

function onClose(v) {
  if (!v) emit('close')
}
</script>

<template>
  <Drawer
    :open="isOpen"
    :title="t('platform.tenant.hardDelete.title')"
    width="max-w-md"
    @update:open="onClose"
  >
    <div v-if="row" class="space-y-4">
      <!-- Big red warning -->
      <div class="p-3 rounded-lg bg-destructive/10 border border-destructive/40">
        <div class="flex items-start gap-2">
          <AlertOctagon class="size-5 text-destructive shrink-0 mt-0.5" />
          <div class="text-sm leading-relaxed">
            <p class="font-semibold text-destructive mb-1">
              {{ t('platform.tenant.hardDelete.warning.title') }}
            </p>
            <p class="text-foreground">
              {{ t('platform.tenant.hardDelete.warning.intro', {
                displayName: row.displayName, tenantCode: row.tenantCode
              }) }}
            </p>
            <ul class="mt-2 text-xs text-muted-foreground space-y-1 list-disc pl-5">
              <li>{{ t('platform.tenant.hardDelete.warning.dropBusiness') }}</li>
              <li>{{ t('platform.tenant.hardDelete.warning.dropRealm') }}</li>
              <li>{{ t('platform.tenant.hardDelete.warning.dropRegistry') }}</li>
              <li class="font-medium text-destructive">{{ t('platform.tenant.hardDelete.warning.noUndo') }}</li>
            </ul>
          </div>
        </div>
      </div>

      <!-- Typed confirmation -->
      <div>
        <label class="text-xs text-muted-foreground block mb-1">
          {{ t('platform.tenant.hardDelete.label.typeCode', { tenantCode: row.tenantCode }) }}
        </label>
        <Input v-model="typed"
               :placeholder="row.tenantCode"
               autocomplete="off"
               class="font-mono" />
        <p v-if="typed && typed !== row.tenantCode"
           class="text-[11px] text-destructive mt-1">
          {{ t('platform.tenant.hardDelete.error.mismatch') }}
        </p>
      </div>
    </div>

    <template #footer>
      <div class="flex justify-end gap-2">
        <button class="h-9 px-3 rounded border border-border text-sm"
                @click="emit('close')">{{ t('common.button.cancel') }}</button>
        <button class="h-9 px-3 rounded bg-destructive text-destructive-foreground text-sm inline-flex items-center gap-1 disabled:opacity-40 disabled:cursor-not-allowed"
                :disabled="!canSubmit"
                @click="submit">
          <Trash2 class="size-4" />
          {{ submitting ? t('platform.tenant.hardDelete.button.deleting') : t('platform.tenant.hardDelete.button.confirm') }}
        </button>
      </div>
    </template>
  </Drawer>
</template>
