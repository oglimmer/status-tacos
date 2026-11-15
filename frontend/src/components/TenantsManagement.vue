<script setup lang="ts">
import { ref, onMounted, computed } from 'vue'
import { useTenantsStore } from '../stores/tenants'
import { useAuthStore } from '../stores/auth'
import TenantForm from './TenantForm.vue'
import PageHeader from './PageHeader.vue'
import TenantsSummary from './TenantsSummary.vue'
import TenantCard from './TenantCard.vue'
import LoadingState from './LoadingState.vue'
import EmptyState from './EmptyState.vue'
import ErrorState from './ErrorState.vue'
import type { Tenant, CurrentUser } from '../services/api'

const tenantsStore = useTenantsStore()
const authStore = useAuthStore()

const showForm = ref(false)
const editingTenant = ref<Tenant | null>(null)
const currentUser = ref<CurrentUser | null>(null)
const showActiveOnly = ref(false)

const filteredTenants = computed(() => {
  if (showActiveOnly.value) {
    return tenantsStore.activeTenants
  }
  return tenantsStore.tenants
})

// Summary data computed property
const summaryData = computed(() => ({
  total: tenantsStore.tenants.length,
  active: tenantsStore.activeTenants.length,
  inactive: tenantsStore.tenants.length - tenantsStore.activeTenants.length
}))

onMounted(async () => {
  try {
    const userData = await authStore.fetchCurrentUser()
    currentUser.value = userData

    // Load all tenants
    await tenantsStore.fetchTenants()
  } catch (err) {
    console.error('Failed to load data:', err)
  }
})

const handleAddTenant = () => {
  showForm.value = true
}

const handleFormSuccess = () => {
  editingTenant.value = null
}

const handleEdit = (tenant: Tenant) => {
  editingTenant.value = tenant
  showForm.value = true
}

const handleCloseForm = () => {
  showForm.value = false
  editingTenant.value = null
}

const handleToggleStatus = async (id: number) => {
  try {
    await tenantsStore.toggleTenantStatus(id)
  } catch (err) {
    console.error('Failed to toggle tenant status:', err)
  }
}

const handleToggleFilter = () => {
  showActiveOnly.value = !showActiveOnly.value
}
</script>

<template>
  <div class="tenants-management">
    <PageHeader
      title="Tenants"
      subtitle="Each tenant groups monitors, alert contacts and users, and has its own status page."
    >
      <template #actions>
        <button type="button" class="btn btn-primary" @click="handleAddTenant">
          <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.5" stroke-linecap="round" aria-hidden="true">
            <path d="M12 5v14M5 12h14" />
          </svg>
          Add Tenant
        </button>
      </template>
    </PageHeader>

    <TenantsSummary
      v-if="!tenantsStore.isLoading && !tenantsStore.error && tenantsStore.tenants.length > 0"
      :summary="summaryData"
      :show-active-only="showActiveOnly"
      @toggle-filter="handleToggleFilter"
    />

    <LoadingState
      v-if="tenantsStore.isLoading"
      message="Loading tenants..."
    />

    <ErrorState
      v-else-if="tenantsStore.error"
      :error="tenantsStore.error"
    />

    <EmptyState
      v-else-if="tenantsStore.tenants.length === 0"
      title="No tenants yet"
      message="Get started by adding your first tenant to organize your monitoring infrastructure."
      button-text="Add Your First Tenant"
      @action="handleAddTenant"
    />

    <p v-else-if="filteredTenants.length === 0" class="list-empty">
      No active tenants.
    </p>

    <ul v-else class="list tenants-list" aria-label="Tenants">
      <TenantCard
        v-for="tenant in filteredTenants"
        :key="tenant.id"
        :tenant="tenant"
        @edit="handleEdit"
        @toggle-status="handleToggleStatus"
      />
    </ul>

    <TenantForm
      v-if="showForm"
      :tenant="editingTenant"
      @close="handleCloseForm"
      @success="handleFormSuccess"
    />
  </div>
</template>

<style scoped>
.list-empty {
  margin: 0;
  padding: var(--space-5) var(--space-4);
  border: 1px dashed var(--border);
  border-radius: var(--radius-lg);
  color: var(--text-muted);
  text-align: center;
}
</style>
