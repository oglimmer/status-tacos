<script setup lang="ts">
defineProps<{
  error: string
}>()
</script>

<template>
  <div class="error" role="alert">
    <svg
      viewBox="0 0 24 24"
      fill="none"
      stroke="currentColor"
      stroke-width="2"
      stroke-linecap="round"
      stroke-linejoin="round"
      aria-hidden="true"
    >
      <circle cx="12" cy="12" r="10" />
      <path d="M12 7v6" />
      <path d="M12 17h.01" />
    </svg>
    <span>{{ error }}</span>
  </div>
</template>

<style scoped>
.error {
  display: flex;
  align-items: flex-start;
  gap: var(--space-3);
  padding: var(--space-3) var(--space-4);
  margin-bottom: var(--space-5);
  border: 1px solid var(--danger-soft-border);
  border-left: 4px solid var(--down);
  border-radius: var(--radius-md);
  background: var(--danger-soft);
  color: var(--danger-soft-text);
  font-size: var(--text-md);
}

.error svg {
  flex: none;
  width: 1.25rem;
  height: 1.25rem;
  margin-top: 0.05rem;
  color: var(--down);
}
</style>
