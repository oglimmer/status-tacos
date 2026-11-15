<script setup lang="ts">
defineProps<{
  title?: string
  message: string
  buttonText?: string
}>()

defineEmits<{
  'action': []
}>()
</script>

<template>
  <div class="empty-state">
    <h3 v-if="title">{{ title }}</h3>
    <p>{{ message }}</p>
    <button v-if="buttonText" type="button" class="btn btn-primary" @click="$emit('action')">
      {{ buttonText }}
    </button>
  </div>
</template>

<style scoped>
.empty-state {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: var(--space-2);
  padding: var(--space-7) var(--space-5);
  border: 1px dashed var(--border);
  border-radius: var(--radius-lg);
  background: var(--surface);
  text-align: center;
}

.empty-state h3 {
  margin: 0;
  font-size: var(--text-lg);
  color: var(--text);
}

.empty-state p {
  max-width: 36rem;
  margin: 0 0 var(--space-3);
  color: var(--text-muted);
}
</style>
