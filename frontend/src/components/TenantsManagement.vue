<script setup lang="ts">
import { ref, onMounted, computed } from 'vue'
import { useTenantsStore } from '../stores/tenants'
import { useAuthStore } from '../stores/auth'
import TenantForm from './TenantForm.vue'
import TenantsHeader from './TenantsHeader.vue'
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
    <TenantsHeader @add-tenant="handleAddTenant" />

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

    <div v-else class="tenants-list">
      <TenantCard
        v-for="tenant in filteredTenants"
        :key="tenant.id"
        :tenant="tenant"
        @edit="handleEdit"
        @toggle-status="handleToggleStatus"
      />
    </div>

    <TenantForm
      v-if="showForm"
      :tenant="editingTenant"
      @close="handleCloseForm"
      @success="handleFormSuccess"
    />
  </div>
</template>

<style scoped>
.tenants-list {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(400px, 1fr));
  gap: 1.5rem;
  margin-bottom: 2rem;
}
</style>
