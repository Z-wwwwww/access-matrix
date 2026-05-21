import { ref } from 'vue'

const state = ref(null)

export function confirm(options) {
  const opts = typeof options === 'string' ? { message: options } : (options || {})
  return new Promise((resolve) => {
    state.value = {
      title: opts.title || '',
      message: opts.message || '',
      confirmText: opts.confirmText || '',
      cancelText: opts.cancelText || '',
      variant: opts.variant || 'default',
      resolve
    }
  })
}

export function useConfirm() {
  return { confirm, state }
}
