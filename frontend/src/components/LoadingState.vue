<script setup lang="ts">
defineProps<{
  message?: string
}>()
</script>

<template>
  <div class="loading" role="status">
    <span class="loading-dot" aria-hidden="true"></span>
    {{ message || 'Loading...' }}
  </div>
</template>

<style scoped>
.loading {
  display: flex;
  align-items: center;
  justify-content: center;
  gap: var(--space-2);
  padding: var(--space-6) var(--space-4);
  margin-bottom: var(--space-5);
  border: 1px dashed var(--border);
  border-radius: var(--radius-lg);
  color: var(--text-muted);
  font-size: var(--text-md);
}

/* A small teal dot that pulses while the data loads. */
.loading-dot {
  width: 0.5rem;
  height: 0.5rem;
  border-radius: 50%;
  background: var(--brand);
  animation: pulse 1.2s ease-in-out infinite;
}

@keyframes pulse {
  50% {
    opacity: 0.25;
  }
}
</style>
