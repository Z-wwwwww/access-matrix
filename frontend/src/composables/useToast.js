import { ref } from 'vue'

let nextId = 0
const toasts = ref([])

function add(message, type = 'info', duration = 3000) {
  const id = ++nextId
  const toast = { id, message, type, duration }
  toasts.value.push(toast)
  if (duration > 0) {
    setTimeout(() => remove(id), duration)
  }
  return id
}

function remove(id) {
  const idx = toasts.value.findIndex((t) => t.id === id)
  if (idx >= 0) toasts.value.splice(idx, 1)
}

export const toast = {
  success: (msg, duration) => add(msg, 'success', duration),
  error: (msg, duration) => add(msg, 'error', duration ?? 5000),
  warning: (msg, duration) => add(msg, 'warning', duration),
  info: (msg, duration) => add(msg, 'info', duration),
  remove
}

export function useToast() {
  return { toasts, toast }
}
