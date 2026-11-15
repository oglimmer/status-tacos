<script setup lang="ts">
import { dismissToast, toasts } from '../services/toasts'
</script>

<template>
  <div class="toasts" aria-live="assertive">
    <div v-for="toast in toasts" :key="toast.id" class="toast" role="alert">
      <span class="toast-message">{{ toast.message }}</span>
      <button
        type="button"
        class="btn btn-icon btn-sm toast-close"
        aria-label="Close"
        @click="dismissToast(toast.id)"
      >
        <svg
          viewBox="0 0 24 24"
          fill="none"
          stroke="currentColor"
          stroke-width="2"
          stroke-linecap="round"
          aria-hidden="true"
        >
          <path d="M6 6l12 12M18 6L6 18" />
        </svg>
      </button>
    </div>
  </div>
</template>

<style scoped>
.toasts {
  position: fixed;
  top: var(--space-4);
  left: 50%;
  transform: translateX(-50%);
  z-index: 2000;
  display: flex;
  flex-direction: column;
  gap: var(--space-2);
  width: min(32rem, calc(100vw - 2rem));
  pointer-events: none;
}

/* The toasts show errors, so they have the red edge of a problem. */
.toast {
  display: flex;
  align-items: flex-start;
  gap: var(--space-3);
  padding: var(--space-2) var(--space-2) var(--space-2) var(--space-4);
  border: 1px solid var(--danger-soft-border);
  border-left: 4px solid var(--down);
  border-radius: var(--radius-md);
  background: var(--surface);
  color: var(--text);
  font-family: var(--font-text);
  font-size: var(--text-md);
  box-shadow: var(--shadow-2);
  pointer-events: auto;
}

.toast-message {
  flex: 1;
  padding-block: 0.3rem;
}

.toast-close {
  flex: none;
}
</style>
