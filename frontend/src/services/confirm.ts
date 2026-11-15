import { shallowRef } from 'vue'

// Asks the user before an action, in the style of the app. Replaces window.confirm().
// ConfirmDialog.vue (in App.vue) shows the question that is open now.

export interface ConfirmOptions {
  title: string
  // One or two sentences: what happens, and what cannot come back.
  message?: string
  // The name of the action, for example "Delete monitor". Not "OK".
  confirmLabel: string
  cancelLabel?: string
  // A red button, for actions that remove something.
  danger?: boolean
}

export interface ConfirmRequest extends ConfirmOptions {
  resolve: (confirmed: boolean) => void
}

export const confirmRequest = shallowRef<ConfirmRequest | null>(null)

/** Resolves with true when the user confirms, false when the user cancels or closes. */
export function askConfirm(options: ConfirmOptions): Promise<boolean> {
  // Only one question at a time: a new one cancels the open one.
  confirmRequest.value?.resolve(false)
  return new Promise((resolve) => {
    confirmRequest.value = { ...options, resolve }
  })
}

export function answerConfirm(confirmed: boolean): void {
  const request = confirmRequest.value
  if (!request) return
  confirmRequest.value = null
  request.resolve(confirmed)
}
