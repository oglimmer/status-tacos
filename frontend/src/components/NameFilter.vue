<script setup lang="ts">
import MonitorIcon from './MonitorIcon.vue'

defineProps<{
  modelValue: string
}>()

defineEmits<{
  'update:modelValue': [value: string]
}>()
</script>

<template>
  <div class="name-filter">
    <label for="monitor-name-filter" class="sr-only">Search monitors by name</label>
    <MonitorIcon name="search" class="name-filter-icon" />
    <input
      id="monitor-name-filter"
      type="search"
      class="input"
      placeholder="Search by name"
      autocomplete="off"
      :value="modelValue"
      @input="$emit('update:modelValue', ($event.target as HTMLInputElement).value)"
    />
  </div>
</template>

<style scoped>
.name-filter {
  position: relative;
  display: flex;
  align-items: center;
  min-width: 0;
}

.name-filter-icon {
  position: absolute;
  left: var(--space-3);
  color: var(--text-muted);
  pointer-events: none;
}

.name-filter .input {
  padding-left: calc(var(--space-3) + 1em + var(--space-2));
}
</style>
