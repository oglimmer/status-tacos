<script setup lang="ts">
import { dismissToast, toasts } from '../services/toasts'
</script>

<template>
  <div class="toasts" aria-live="assertive">
    <div v-for="toast in toasts" :key="toast.id" class="toast" role="alert">
      <span class="toast-message">{{ toast.message }}</span>
      <button class="toast-close" aria-label="Close" @click="dismissToast(toast.id)">×</button>
    </div>
  </div>
</template>

<style scoped>
.toasts {
  position: fixed;
  top: 1rem;
  left: 50%;
  transform: translateX(-50%);
  z-index: 2000;
  display: flex;
  flex-direction: column;
  gap: 0.5rem;
  width: min(32rem, calc(100vw - 2rem));
  pointer-events: none;
}

.toast {
  display: flex;
  align-items: flex-start;
  gap: 0.75rem;
  padding: 0.75rem 1rem;
  border-radius: 8px;
  background: #f8d7da;
  color: #721c24;
  border: 1px solid #f5c6cb;
  box-shadow: 0 4px 12px rgba(0, 0, 0, 0.15);
  pointer-events: auto;
}

.toast-message {
  flex: 1;
}

.toast-close {
  border: none;
  background: none;
  color: inherit;
  font-size: 1.25rem;
  line-height: 1;
  cursor: pointer;
  padding: 0;
}
</style>
