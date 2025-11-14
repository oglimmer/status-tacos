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
  background: #f8f9fa;
  color: #6c757d;
}
</style>
