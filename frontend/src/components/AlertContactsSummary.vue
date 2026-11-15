<script setup lang="ts">
import TenantFilter from './TenantFilter.vue'

// The counts of the alert contacts in a stats row, then the tenant filter in the toolbar.

interface Tenant {
  id: number
  name: string
}

interface SummaryData {
  total: number
  active: number
  inactive: number
  email: number
  webhook: number
  teams: number
  iosPush: number
}

defineProps<{
  summary: SummaryData
  tenants?: Tenant[]
  selectedTenantId: number | null
}>()

defineEmits<{
  'update:selectedTenantId': [value: number | null]
}>()
</script>

<template>
  <div class="page-tools">
    <div class="stats" aria-label="Alert contact counts">
      <div class="stat">
        <span class="stat-value">{{ summary.total }}</span>
        <span class="stat-label">Total</span>
      </div>
      <div class="stat stat-up">
        <span class="stat-value">{{ summary.active }}</span>
        <span class="stat-label">Active</span>
      </div>
      <div class="stat">
        <span class="stat-value">{{ summary.inactive }}</span>
        <span class="stat-label">Inactive</span>
      </div>
      <div class="stat">
        <span class="stat-value">{{ summary.email }}</span>
        <span class="stat-label">Email</span>
      </div>
      <div class="stat">
        <span class="stat-value">{{ summary.webhook }}</span>
        <span class="stat-label">Webhook</span>
      </div>
      <div class="stat">
        <span class="stat-value">{{ summary.teams }}</span>
        <span class="stat-label">Teams</span>
      </div>
      <div class="stat">
        <span class="stat-value">{{ summary.iosPush }}</span>
        <span class="stat-label">iOS Push</span>
      </div>
    </div>

    <div v-if="tenants && tenants.length > 1" class="toolbar">
      <TenantFilter
        :tenants="tenants"
        :model-value="selectedTenantId"
        @update:model-value="$emit('update:selectedTenantId', $event)"
      />
    </div>
  </div>
</template>

<style scoped>
.page-tools {
  display: flex;
  flex-direction: column;
  gap: var(--space-3);
  margin-bottom: var(--space-4);
}

/* On a phone the tiles share the width. */
@media (max-width: 520px) {
  .stats .stat {
    flex: 1 1 5.5rem;
    min-width: 0;
  }
}

/* TenantFilter must not be wider than a phone: let it wrap and shrink. */
.toolbar > * {
  max-width: 100%;
  flex-wrap: wrap;
}

.toolbar :deep(select) {
  max-width: 100%;
}
</style>
