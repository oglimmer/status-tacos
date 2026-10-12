import { reactive } from 'vue'

export interface Toast {
  id: number
  message: string
}

const DISMISS_AFTER_MS = 8000

/** The messages that show at the top of every page. Not in Pinia, so plain services can use it. */
export const toasts = reactive<Toast[]>([])

let nextId = 1

export function dismissToast(id: number) {
  const index = toasts.findIndex((toast) => toast.id === id)
  if (index >= 0) {
    toasts.splice(index, 1)
  }
}

/** Shows an error message. The same message shows only once at a time (polling repeats errors). */
export function showErrorToast(message: string) {
  if (toasts.some((toast) => toast.message === message)) {
    return
  }
  const id = nextId++
  toasts.push({ id, message })
  setTimeout(() => dismissToast(id), DISMISS_AFTER_MS)
}
