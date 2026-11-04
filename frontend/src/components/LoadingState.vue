<script setup lang="ts">
defineProps<{
  message?: string
}>()
</script>

<template>
  <div class="loading">
    {{ message || 'Loading...' }}
  </div>
</template>

<style scoped>
.loading {
  text-align: center;
  padding: 2rem;
  border-radius: 8px;
  margin-bottom: 2rem;
  background: var(--surface-2);
  color: var(--text-muted);
}
</style>
