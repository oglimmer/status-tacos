import { ref, computed } from 'vue'
import { defineStore } from 'pinia'
import { apiService, type Tenant, type TenantRequest, type TenantUser } from '../services/api'

export const useTenantsStore = defineStore('tenants', () => {
  const tenants = ref<Tenant[]>([])
  const tenantUsers = ref<Record<number, TenantUser[]>>({})
  const isLoading = ref(false)
  const isLoadingUsers = ref(false)
  const error = ref<string | null>(null)

  const activeTenants = computed(() =>
    tenants.value.filter(tenant => tenant.isActive)
  )

  async function fetchTenants(activeOnly = false) {
    isLoading.value = true
    error.value = null

    try {
      const fetchedTenants = await apiService.getTenants(activeOnly)
      tenants.value = fetchedTenants
    } catch (err) {
      error.value = 'Failed to fetch tenants'
      console.error('Fetch tenants error:', err)
      throw err
    } finally {
      isLoading.value = false
    }
  }

  async function fetchTenant(id: number) {
    isLoading.value = true
    error.value = null

    try {
      const tenant = await apiService.getTenant(id)
      const existingIndex = tenants.value.findIndex(t => t.id === id)
      if (existingIndex >= 0) {
        tenants.value[existingIndex] = tenant
      } else {
        tenants.value.push(tenant)
      }
      return tenant
    } catch (err) {
      error.value = 'Failed to fetch tenant'
      console.error('Fetch tenant error:', err)
      throw err
    } finally {
      isLoading.value = false
    }
  }

  async function createTenant(request: TenantRequest) {
    isLoading.value = true
    error.value = null

    try {
      const newTenant = await apiService.createTenant(request)
      tenants.value.push(newTenant)
      return newTenant
    } catch (err) {
      error.value = 'Failed to create tenant'
      console.error('Create tenant error:', err)
      throw err
    } finally {
      isLoading.value = false
    }
  }

  async function updateTenant(id: number, request: Omit<TenantRequest, 'code'>) {
    isLoading.value = true
    error.value = null

    try {
      const updatedTenant = await apiService.updateTenant(id, request)
      const index = tenants.value.findIndex(tenant => tenant.id === id)
      if (index >= 0) {
        tenants.value[index] = updatedTenant
      }
      return updatedTenant
    } catch (err) {
      error.value = 'Failed to update tenant'
      console.error('Update tenant error:', err)
      throw err
    } finally {
      isLoading.value = false
    }
  }

  async function toggleTenantStatus(id: number) {
    isLoading.value = true
    error.value = null

    try {
      const updatedTenant = await apiService.toggleTenantStatus(id)
      const index = tenants.value.findIndex(tenant => tenant.id === id)
      if (index >= 0) {
        tenants.value[index] = updatedTenant
      }
      return updatedTenant
    } catch (err) {
      error.value = 'Failed to toggle tenant status'
      console.error('Toggle tenant status error:', err)
      throw err
    } finally {
      isLoading.value = false
    }
  }

  async function fetchTenantUsers(tenantId: number) {
    isLoadingUsers.value = true
    error.value = null

    try {
      const users = await apiService.getTenantUsers(tenantId)
      tenantUsers.value[tenantId] = users
      return users
    } catch (err) {
      error.value = 'Failed to fetch tenant users'
      console.error('Fetch tenant users error:', err)
      throw err
    } finally {
      isLoadingUsers.value = false
    }
  }

  async function assignUserToTenant(tenantId: number, email: string) {
    isLoadingUsers.value = true
    error.value = null

    try {
      await apiService.assignUserToTenant(tenantId, email)
      // Refresh tenant users
      await fetchTenantUsers(tenantId)
    } catch (err) {
      error.value = 'Failed to assign user to tenant'
      console.error('Assign user to tenant error:', err)
      throw err
    } finally {
      isLoadingUsers.value = false
    }
  }

  async function removeUserFromTenant(tenantId: number, email: string) {
    isLoadingUsers.value = true
    error.value = null

    try {
      await apiService.removeUserFromTenant(tenantId, email)
      // Refresh tenant users
      await fetchTenantUsers(tenantId)
    } catch (err) {
      error.value = 'Failed to remove user from tenant'
      console.error('Remove user from tenant error:', err)
      throw err
    } finally {
      isLoadingUsers.value = false
    }
  }

  function getTenantUsers(tenantId: number): TenantUser[] {
    return tenantUsers.value[tenantId] || []
  }

  return {
    tenants,
    activeTenants,
    isLoading,
    isLoadingUsers,
    error,
    fetchTenants,
    fetchTenant,
    createTenant,
    updateTenant,
    toggleTenantStatus,
    fetchTenantUsers,
    assignUserToTenant,
    removeUserFromTenant,
    getTenantUsers
  }
})
