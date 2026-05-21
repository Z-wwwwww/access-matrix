<script setup>
import { ref } from 'vue'
import request from '../../../services/request'
import { toast } from '@/composables/useToast'

defineOptions({ name: 'ExportFileButton' })

const props = defineProps({
  url: { type: String, required: true },
  where: { type: Object, default: () => ({}) },
  selection: { type: Array, default: null },
  filtered: { type: Object, default: () => ({}) },
  label: { type: String, default: '' },
  type: { type: String, default: 'primary' },
  loading: { type: Boolean, default: false }
})

const emit = defineEmits(['update:loading', 'done'])

const exporting = ref(false)

async function exportFile() {
  if (exporting.value) return
  exporting.value = true
  emit('update:loading', true)
  try {
    const body = props.selection
      ? props.selection
      : { ...props.where, ...serializeFiltered(props.filtered) }

    const res = await request.post(props.url, body, { responseType: 'blob' })

    // JSON が返った（= ファイルではない）場合は業務エラーとして msg を表示
    const ctype = res.headers['content-type'] || ''
    if (ctype.includes('application/json') || res.data?.type === 'application/json') {
      const text = await res.data.text()
      let msg = 'エクスポート失敗'
      try {
        const json = JSON.parse(text)
        msg = json.msg || json.message || msg
      } catch { /* not JSON */ }
      toast.error(msg)
      return
    }

    const disposition = res.headers['content-disposition']
    let filename = 'export.xlsx'
    if (disposition) {
      const match = disposition.match(/filename\*?=(?:UTF-8'')?([^;\n]+)/i)
      if (match) filename = decodeURIComponent(match[1].replace(/["']/g, ''))
    }

    const blob = new Blob([res.data])
    const link = document.createElement('a')
    link.href = URL.createObjectURL(blob)
    link.download = filename
    document.body.appendChild(link)
    link.click()
    document.body.removeChild(link)
    URL.revokeObjectURL(link.href)
    emit('done')
  } catch (e) {
    console.error('Export failed:', e)
    let msg = e?.message || 'エクスポート失敗'
    const errData = e?.response?.data
    if (errData instanceof Blob) {
      try {
        const text = await errData.text()
        const json = JSON.parse(text)
        msg = json.msg || json.message || msg
      } catch { /* keep default */ }
    }
    toast.error(msg)
  } finally {
    exporting.value = false
    emit('update:loading', false)
  }
}

function serializeFiltered(filtered) {
  if (!filtered || Object.keys(filtered).length === 0) return {}
  const result = {}
  for (const [key, val] of Object.entries(filtered)) {
    if (Array.isArray(val)) {
      // 空数组は送らない (String フィールドへ送ると Jackson が落ちる)
      if (val.length > 0) result[key] = val.join(',')
    } else if (val !== '' && val != null) {
      result[key] = val
    }
  }
  return result
}
</script>

<template>
  <button
    :disabled="exporting"
    class="inline-flex items-center gap-1.5 h-9 px-3 rounded-lg text-sm font-medium transition-colors"
    :class="[
      type === 'primary'
        ? 'bg-primary text-primary-foreground hover:bg-primary/90'
        : 'bg-muted text-foreground hover:bg-muted/80',
      exporting ? 'opacity-50 cursor-not-allowed' : ''
    ]"
    @click="exportFile"
  >
    <slot>{{ label }}</slot>
  </button>
</template>
