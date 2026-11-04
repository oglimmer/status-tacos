<script setup lang="ts">
defineProps<{
  error: string
}>()
</script>

<template>
  <div class="error">
    {{ error }}
  </div>
</template>

<style scoped>
.error {
  text-align: center;
  padding: 2rem;
  border-radius: 8px;
  margin-bottom: 2rem;
  background: var(--danger-soft);
  color: var(--danger-soft-text);
  border: 1px solid var(--danger-soft-border);
}
</style>
