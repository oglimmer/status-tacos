<script setup lang="ts">
import { ref } from 'vue'
import type { Tenant, TenantUser } from '../services/api'

defineProps<{
  tenant: Tenant
  users: TenantUser[]
  isLoading: boolean
}>()

const emit = defineEmits<{
  'assign-user': [email: string]
  'remove-user': [email: string]
}>()

const newUserEmail = ref('')
const isAssigning = ref(false)

const handleAssignUser = async () => {
  if (!newUserEmail.value.trim() || isAssigning.value) return

  isAssigning.value = true
  try {
    await emit('assign-user', newUserEmail.value.trim())
    newUserEmail.value = ''
  } catch (err) {
    console.error('Failed to assign user:', err)
  } finally {
    isAssigning.value = false
  }
}

const handleRemoveUser = async (email: string) => {
  if (confirm(`Are you sure you want to remove ${email} from this tenant?`)) {
    try {
      await emit('remove-user', email)
    } catch (err) {
      console.error('Failed to remove user:', err)
    }
  }
}
</script>

<template>
  <div class="user-management">
    <h4>Users in {{ tenant.name }}</h4>

    <div class="add-user-form">
      <div class="input-group">
        <input
          v-model="newUserEmail"
          type="email"
          placeholder="Enter user email"
          :disabled="isAssigning"
          @keyup.enter="handleAssignUser"
        />
        <button
          @click="handleAssignUser"
          :disabled="!newUserEmail.trim() || isAssigning"
          class="btn btn-primary btn-sm"
        >
          {{ isAssigning ? 'Adding...' : 'Add User' }}
        </button>
      </div>
    </div>

    <div v-if="isLoading" class="loading">
      Loading users...
    </div>

    <div v-else-if="users.length === 0" class="empty-users">
      No users assigned to this tenant.
    </div>

    <div v-else class="users-list">
      <div
        v-for="user in users"
        :key="user.id"
        class="user-item"
      >
        <div class="user-info">
          <div class="user-name">
            {{ user.firstName }} {{ user.lastName }}
          </div>
          <div class="user-email">{{ user.email }}</div>
        </div>
        <button
          @click="handleRemoveUser(user.email)"
          class="btn btn-danger btn-sm"
        >
          Remove
        </button>
      </div>
    </div>
  </div>
</template>

<style scoped>
.user-management {
  margin-top: 1rem;
  padding-top: 1rem;
  border-top: 1px solid #e9ecef;
}

.user-management h4 {
  margin: 0 0 1rem 0;
  color: #495057;
  font-size: 1rem;
}

.add-user-form {
  margin-bottom: 1rem;
}

.input-group {
  display: flex;
  gap: 0.5rem;
}

.input-group input {
  flex: 1;
  padding: 0.375rem 0.5rem;
  border: 1px solid #ced4da;
  border-radius: 4px;
  font-size: 0.875rem;
}

.input-group input:focus {
  outline: none;
  border-color: #007bff;
  box-shadow: 0 0 0 2px rgba(0, 123, 255, 0.25);
}

.loading, .empty-users {
  color: #6c757d;
  font-style: italic;
  text-align: center;
  padding: 1rem;
}

.users-list {
  display: flex;
  flex-direction: column;
  gap: 0.5rem;
}

.user-item {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 0.5rem;
  background: #f8f9fa;
  border-radius: 4px;
  border: 1px solid #e9ecef;
}

.user-info {
  flex: 1;
}

.user-name {
  font-weight: 500;
  color: #495057;
}

.user-email {
  font-size: 0.875rem;
  color: #6c757d;
}

.btn {
  padding: 0.375rem 0.75rem;
  border: none;
  border-radius: 4px;
  cursor: pointer;
  font-size: 0.875rem;
  transition: all 0.2s;
  font-weight: 500;
}

.btn:disabled {
  opacity: 0.6;
  cursor: not-allowed;
}

.btn-sm {
  padding: 0.25rem 0.5rem;
  font-size: 0.8rem;
}

.btn-primary {
  background: #007bff;
  color: white;
}

.btn-primary:hover:not(:disabled) {
  background: #0056b3;
}

.btn-danger {
  background: #dc3545;
  color: white;
}

.btn-danger:hover:not(:disabled) {
  background: #c82333;
}
</style>
