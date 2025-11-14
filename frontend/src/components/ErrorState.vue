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
  background: #f8d7da;
  color: #721c24;
  border: 1px solid #f5c6cb;
}
</style>
