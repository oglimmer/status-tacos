<script setup lang="ts">
interface Tenant {
  id: number
  name: string
}

defineProps<{
  tenants: Tenant[]
  modelValue: number | null
}>()

defineEmits<{
  'update:modelValue': [value: number | null]
}>()
</script>

<template>
  <div v-if="tenants.length > 1" class="filters">
    <label for="tenant-filter">Filter by Tenant:</label>
    <select
      id="tenant-filter"
      :value="modelValue ?? ''"
      @input="$emit('update:modelValue', ($event.target as HTMLSelectElement).value === '' ? null : Number(($event.target as HTMLSelectElement).value))"
    >
      <option value="">All Tenants</option>
      <option v-for="tenant in tenants" :key="tenant.id" :value="tenant.id">
        {{ tenant.name }}
      </option>
    </select>
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

.filters select {
  padding: 0.5rem;
  border: 1px solid #dee2e6;
  border-radius: 4px;
  font-size: 0.9rem;
  background: white;
  width: 250px;
}
</style>
