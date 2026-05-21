<script setup>
import { ref, watch } from 'vue'
import Select from '@/components/ui/Select.vue'
import { getUserListApi } from '../../../services/user'

defineOptions({ name: 'UserPicker' })

/**
 * Generic active-user dropdown.
 *
 * Backend: paginated /admin/user/list (returns { records, total }). We fetch
 * up to 500 records (server cap from PaginationInnerInterceptor) which covers
 * every realistic admin-scope user pool. Records are mapped to `{value,label}`
 * pairs the Select component understands.
 *
 * NOTE: The legacy PMS UserPicker also took a `companyId` prop that filtered
 * by company. In access-matrix tenancy is enforced at the request layer
 * (X-Tenant-Id header / JWT tid claim) and the backend list endpoint already
 * scopes to the current tenant, so no equivalent prop is exposed here.
 */
const props = defineProps({
  modelValue: {
    type: [String, Number],
    default: ''
  },
  /** Optional keyword pre-filter pushed to the server (matches username / email / displayName). */
  keyword: {
    type: String,
    default: ''
  },
  placeholder: {
    type: String,
    default: ''
  },
  disabled: {
    type: Boolean,
    default: false
  },
  error: {
    type: Boolean,
    default: false
  }
})

const emit = defineEmits(['update:modelValue', 'change'])

const options = ref([])

async function loadOptions() {
  try {
    const params = { page: 1, size: 500 }
    if (props.keyword) params.keyword = props.keyword
    const res = await getUserListApi(params)
    if (res.data.code === 0) {
      const records = res.data.data?.records || []
      // Prefer displayName for readability; fall back to username so the
      // label is never blank. value is the user's stable id.
      options.value = records.map(u => ({
        value: u.id,
        label: u.displayName ? `${u.displayName} (${u.username})` : u.username
      }))
    } else {
      options.value = []
    }
  } catch (e) {
    console.error('ユーザーリスト取得失敗:', e)
    options.value = []
  }
}

watch(() => props.keyword, loadOptions, { immediate: true })

function onChange(val) {
  emit('update:modelValue', val)
  const opt = options.value.find((o) => String(o.value) === String(val))
  emit('change', opt || { value: val, label: '' })
}
</script>

<template>
  <Select
    :model-value="modelValue"
    :options="options"
    :placeholder="placeholder"
    :disabled="disabled"
    :error="error"
    :searchable="true"
    clearable
    @update:model-value="onChange"
  />
</template>
