<script setup lang="ts">
import { ref } from 'vue'
import { useTenantsStore } from '../stores/tenants'
import type { Tenant } from '../services/api'
import UserManagement from './UserManagement.vue'

defineProps<{
  tenant: Tenant
}>()

defineEmits<{
  'edit': [tenant: Tenant]
  'toggle-status': [id: number]
}>()

const tenantsStore = useTenantsStore()
const showUserManagement = ref(false)

const formatDate = (dateString: string) => {
  return new Date(dateString).toLocaleDateString()
}

const handleToggleUserManagement = async (tenant: Tenant) => {
  if (!showUserManagement.value) {
    try {
      await tenantsStore.fetchTenantUsers(tenant.id)
    } catch (error) {
      console.error('Error fetching users:', error)
    }
  }
  showUserManagement.value = !showUserManagement.value
}
</script>

<template>
  <div
    class="tenant-card"
    :class="{ inactive: !tenant.isActive }"
  >
    <div class="tenant-header">
      <h3>{{ tenant.name }}</h3>
      <div class="tenant-status">
        <span class="tenant-code">{{ tenant.code }}</span>
        <span class="status-badge" :class="{ active: tenant.isActive }">
          {{ tenant.isActive ? 'Active' : 'Inactive' }}
        </span>
      </div>
    </div>

    <div class="tenant-details">
      <div v-if="tenant.description" class="detail-item">
        <strong>Description:</strong>
        <span>{{ tenant.description }}</span>
      </div>
      <div class="detail-item">
        <strong>Created:</strong>
        <span>{{ formatDate(tenant.createdAt) }}</span>
      </div>
      <div class="detail-item">
        <strong>Updated:</strong>
        <span>{{ formatDate(tenant.updatedAt) }}</span>
      </div>
    </div>

    <div class="tenant-actions">
      <button @click="$emit('edit', tenant)" class="btn btn-sm btn-outline">
        Edit
      </button>
      <button
        @click="handleToggleUserManagement(tenant)"
        class="btn btn-sm btn-outline"
        :style="{ backgroundColor: showUserManagement ? '#007bff' : 'transparent', color: showUserManagement ? 'white' : '#007bff' }"
      >
        {{ showUserManagement ? 'Hide Users' : 'Manage Users' }}
      </button>
      <button
        @click="$emit('toggle-status', tenant.id)"
        class="btn btn-sm"
        :class="tenant.isActive ? 'btn-warning' : 'btn-success'"
      >
        {{ tenant.isActive ? 'Deactivate' : 'Activate' }}
      </button>
    </div>

    <UserManagement
      v-if="showUserManagement"
      :tenant="tenant"
      :users="tenantsStore.getTenantUsers(tenant.id)"
      :is-loading="tenantsStore.isLoadingUsers"
      @assign-user="(email) => tenantsStore.assignUserToTenant(tenant.id, email)"
      @remove-user="(email) => tenantsStore.removeUserFromTenant(tenant.id, email)"
    />
  </div>
</template>

<style scoped>
.tenant-card {
  background: white;
  border-radius: 8px;
  padding: 1.5rem;
  box-shadow: 0 2px 4px rgba(0, 0, 0, 0.1);
  border: 1px solid #e9ecef;
}

.tenant-card.inactive {
  opacity: 0.7;
  background: #f8f9fa;
}

.tenant-header {
  display: flex;
  justify-content: space-between;
  align-items: flex-start;
  margin-bottom: 1rem;
  gap: 1rem;
}

.tenant-header h3 {
  margin: 0;
  color: #2c3e50;
  font-size: 1.2rem;
  word-break: break-word;
}

.tenant-status {
  display: flex;
  flex-direction: column;
  align-items: flex-end;
  gap: 0.5rem;
}

.tenant-code {
  background: #f8f9fa;
  color: #6c757d;
  padding: 0.25rem 0.5rem;
  border-radius: 4px;
  font-size: 0.8rem;
  font-family: monospace;
}

.status-badge {
  padding: 0.25rem 0.5rem;
  border-radius: 4px;
  font-size: 0.8rem;
  font-weight: 500;
  background: #dc3545;
  color: white;
}

.status-badge.active {
  background: #28a745;
}

.tenant-details {
  margin-bottom: 1rem;
}

.detail-item {
  display: flex;
  margin-bottom: 0.5rem;
  gap: 0.5rem;
}

.detail-item strong {
  color: #495057;
  min-width: 80px;
}

.detail-item span {
  color: #6c757d;
  word-break: break-word;
}

.tenant-actions {
  display: flex;
  gap: 0.5rem;
  flex-wrap: wrap;
}

.btn {
  padding: 0.375rem 0.75rem;
  border: none;
  border-radius: 4px;
  cursor: pointer;
  font-size: 0.875rem;
  transition: all 0.2s;
  font-weight: 500;
  text-decoration: none;
  display: inline-block;
}

.btn:disabled {
  opacity: 0.6;
  cursor: not-allowed;
}

.btn-sm {
  padding: 0.25rem 0.5rem;
  font-size: 0.8rem;
}

.btn-outline {
  background: transparent;
  color: #007bff;
  border: 1px solid #007bff;
}

.btn-outline:hover:not(:disabled) {
  background: #007bff;
  color: white;
}

.btn-success {
  background: #28a745;
  color: white;
}

.btn-success:hover:not(:disabled) {
  background: #1e7e34;
}

.btn-warning {
  background: #ffc107;
  color: #212529;
}

.btn-warning:hover:not(:disabled) {
  background: #e0a800;
}
</style>
