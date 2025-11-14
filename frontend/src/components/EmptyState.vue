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
    <button v-if="buttonText" @click="$emit('action')" class="btn btn-primary">
      {{ buttonText }}
    </button>
  </div>
</template>

<style scoped>
.empty-state {
  text-align: center;
  padding: 3rem;
  background: white;
  border-radius: 8px;
  border: 1px solid #e9ecef;
}

.empty-state h3 {
  color: #2c3e50;
  margin-bottom: 1rem;
}

.empty-state p {
  color: #6c757d;
  margin-bottom: 2rem;
}

.btn {
  padding: 0.5rem 1rem;
  border: none;
  border-radius: 4px;
  cursor: pointer;
  font-size: 0.9rem;
  transition: all 0.2s;
  font-weight: 500;
}

.btn:disabled {
  opacity: 0.6;
  cursor: not-allowed;
}

.btn-primary {
  background: #007bff;
  color: white;
}

.btn-primary:hover:not(:disabled) {
  background: #0056b3;
}
</style>
