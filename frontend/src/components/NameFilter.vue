<script setup lang="ts">
defineProps<{
  modelValue: string
}>()

defineEmits<{
  'update:modelValue': [value: string]
}>()
</script>

<template>
  <div class="filters">
    <label for="monitor-name-filter">Filter by name:</label>
    <input
      id="monitor-name-filter"
      type="text"
      placeholder="Monitor name..."
      :value="modelValue"
      @input="$emit('update:modelValue', ($event.target as HTMLInputElement).value)"
    />
  </div>
</template>

<style scoped>
.filters {
  margin-bottom: 0;
  display: flex;
  align-items: center;
  gap: 1rem;
  justify-content: flex-end;
}

.filters label {
  font-weight: 500;
  color: #495057;
  font-size: 0.9rem;
}

.filters input {
  padding: 0.5rem;
  border: 1px solid #dee2e6;
  border-radius: 4px;
  font-size: 0.9rem;
  background: white;
  width: 250px;
}

.filters input:focus {
  outline: none;
  border-color: #3b82f6;
  box-shadow: 0 0 0 3px rgba(59, 130, 246, 0.1);
}

</style>
