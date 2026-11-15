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
    <label for="tenant-filter" class="sr-only">Filter by tenant</label>
    <select
      id="tenant-filter"
      class="input"
      :value="modelValue ?? ''"
      @input="$emit('update:modelValue', ($event.target as HTMLSelectElement).value === '' ? null : Number(($event.target as HTMLSelectElement).value))"
    >
      <option value="">All tenants</option>
      <option v-for="tenant in tenants" :key="tenant.id" :value="tenant.id">
        {{ tenant.name }}
      </option>
    </select>
  </div>
</template>

<style scoped>
.filters {
  display: flex;
  align-items: center;
  min-width: 0;
}

.filters select {
  width: auto;
  max-width: 100%;
}
</style>
