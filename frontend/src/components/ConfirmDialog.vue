<script setup lang="ts">
import { nextTick, ref, watch } from 'vue'
import { answerConfirm, confirmRequest } from '../services/confirm'

// The one confirm dialog of the app. A native <dialog> with showModal(): it is above all other
// modals, holds the keyboard focus inside, and closes with Escape.
const dialog = ref<HTMLDialogElement | null>(null)
const cancelButton = ref<HTMLButtonElement | null>(null)

watch(confirmRequest, async (request) => {
  await nextTick()
  const el = dialog.value
  if (!el) return
  if (request && !el.open) {
    el.showModal?.()
    // Focus on Cancel: Enter must not remove something by accident.
    cancelButton.value?.focus()
  } else if (!request && el.open) {
    el.close()
  }
})

// Escape, or the close() above. The 'close' event comes in both cases.
function onClose() {
  answerConfirm(false)
}

function onBackdropClick(event: MouseEvent) {
  // A click on the backdrop has the dialog itself as target, not its content.
  if (event.target === dialog.value) answerConfirm(false)
}
</script>

<template>
  <dialog
    ref="dialog"
    class="confirm-dialog"
    aria-labelledby="confirm-title"
    :aria-describedby="confirmRequest?.message ? 'confirm-message' : undefined"
    @close="onClose"
    @click="onBackdropClick"
  >
    <div v-if="confirmRequest" class="confirm-inner">
      <h2 id="confirm-title" class="confirm-title">{{ confirmRequest.title }}</h2>
      <p v-if="confirmRequest.message" id="confirm-message" class="confirm-message">
        {{ confirmRequest.message }}
      </p>
      <div class="confirm-actions">
        <button
          ref="cancelButton"
          type="button"
          class="btn btn-outline"
          @click="answerConfirm(false)"
        >
          {{ confirmRequest.cancelLabel ?? 'Cancel' }}
        </button>
        <button
          type="button"
          class="btn"
          :class="confirmRequest.danger ? 'btn-danger' : 'btn-primary'"
          @click="answerConfirm(true)"
        >
          {{ confirmRequest.confirmLabel }}
        </button>
      </div>
    </div>
  </dialog>
</template>

<style scoped>
.confirm-dialog {
  width: calc(100% - 2 * var(--space-4));
  max-width: 26rem;
  padding: 0;
  border: 1px solid var(--border);
  border-radius: var(--radius-lg);
  background: var(--surface);
  color: var(--text);
  box-shadow: var(--shadow-2);
}

.confirm-dialog::backdrop {
  background: var(--overlay);
}

.confirm-inner {
  padding: var(--space-5);
}

.confirm-title {
  margin: 0;
  font-size: var(--text-lg);
  line-height: var(--leading-tight);
}

.confirm-message {
  margin: var(--space-2) 0 0;
  color: var(--text-2);
  font-size: var(--text-sm);
  line-height: var(--leading);
  overflow-wrap: anywhere;
}

.confirm-actions {
  display: flex;
  flex-wrap: wrap;
  justify-content: flex-end;
  gap: var(--space-2);
  margin-top: var(--space-5);
}

@media (prefers-reduced-motion: no-preference) {
  .confirm-dialog[open] {
    animation: confirm-in 120ms ease-out;
  }

  @keyframes confirm-in {
    from {
      opacity: 0;
      transform: translateY(4px);
    }
  }
}
</style>
