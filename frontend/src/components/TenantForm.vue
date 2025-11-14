<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { useTenantsStore } from '../stores/tenants'
import type { Tenant, TenantRequest } from '../services/api'
import FormModal from './FormModal.vue'

const props = defineProps<{
  tenant?: Tenant | null
}>()

const emit = defineEmits<{
  close: []
  success: []
}>()

const tenantsStore = useTenantsStore()

const form = ref<TenantRequest>({
  name: '',
  code: '',
  description: ''
})

const isSubmitting = ref(false)
const error = ref<string | null>(null)

const title = props.tenant ? 'Edit Tenant' : 'Add New Tenant'
const isEditing = !!props.tenant

onMounted(() => {
  if (props.tenant) {
    form.value = {
      name: props.tenant.name,
      code: props.tenant.code,
      description: props.tenant.description || ''
    }
  }
})

const handleSubmit = async () => {
  if (isSubmitting.value) return

  error.value = null
  isSubmitting.value = true

  try {
    if (isEditing && props.tenant) {
      // For editing, we don't send the code (it can't be changed)
      const updateData = {
        name: form.value.name,
        description: form.value.description
      }
      await tenantsStore.updateTenant(props.tenant.id, updateData)
    } else {
      await tenantsStore.createTenant(form.value)
    }

    emit('success')
    emit('close')
  } catch (err: unknown) {
    error.value = (err instanceof Error ? err.message : String(err)) || `Failed to ${isEditing ? 'update' : 'create'} tenant`
  } finally {
    isSubmitting.value = false
  }
}

const handleClose = () => {
  emit('close')
}
</script>

<template>
  <FormModal :title="title" @close="handleClose">
    <form @submit.prevent="handleSubmit" class="tenant-form">
      <div v-if="error" class="error-message">
        {{ error }}
      </div>

      <div class="form-group">
        <label for="name">Name *</label>
        <input
          id="name"
          v-model="form.name"
          type="text"
          required
          :disabled="isSubmitting"
          placeholder="Enter tenant name"
        />
      </div>

      <div class="form-group">
        <label for="code">Code *</label>
        <input
          id="code"
          v-model="form.code"
          type="text"
          required
          :disabled="isSubmitting || isEditing"
          :placeholder="isEditing ? 'Code cannot be changed' : 'Enter unique tenant code'"
          pattern="[a-zA-Z0-9\-_]+"
          title="Only letters, numbers, hyphens, and underscores are allowed"
        />
        <small v-if="!isEditing" class="form-help">
          Only letters, numbers, hyphens, and underscores are allowed
        </small>
      </div>

      <div class="form-group">
        <label for="description">Description</label>
        <textarea
          id="description"
          v-model="form.description"
          :disabled="isSubmitting"
          placeholder="Enter tenant description (optional)"
          rows="3"
        ></textarea>
      </div>

      <div class="form-actions">
        <button type="button" @click="handleClose" :disabled="isSubmitting" class="btn btn-secondary">
          Cancel
        </button>
        <button type="submit" :disabled="isSubmitting || !form.name || !form.code" class="btn btn-primary">
          {{ isSubmitting ? (isEditing ? 'Updating...' : 'Creating...') : (isEditing ? 'Update Tenant' : 'Create Tenant') }}
        </button>
      </div>
    </form>
  </FormModal>
</template>

<style scoped>
.tenant-form {
  max-width: 500px;
  margin: 0 auto;
}

.error-message {
  background: #f8d7da;
  color: #721c24;
  padding: 0.75rem;
  border-radius: 4px;
  margin-bottom: 1rem;
  border: 1px solid #f5c6cb;
}

.form-group {
  margin-bottom: 1rem;
}

.form-group label {
  display: block;
  margin-bottom: 0.5rem;
  font-weight: 500;
  color: #495057;
}

.form-group input,
.form-group textarea {
  width: 100%;
  padding: 0.5rem;
  border: 1px solid #ced4da;
  border-radius: 4px;
  font-size: 1rem;
  transition: border-color 0.2s;
}

.form-group input:focus,
.form-group textarea:focus {
  outline: none;
  border-color: #007bff;
  box-shadow: 0 0 0 2px rgba(0, 123, 255, 0.25);
}

.form-group input:disabled,
.form-group textarea:disabled {
  background-color: #e9ecef;
  opacity: 1;
}

.form-help {
  display: block;
  margin-top: 0.25rem;
  color: #6c757d;
  font-size: 0.875rem;
}

.form-actions {
  display: flex;
  gap: 1rem;
  justify-content: flex-end;
  margin-top: 2rem;
}

.btn {
  padding: 0.5rem 1rem;
  border: none;
  border-radius: 4px;
  cursor: pointer;
  font-size: 0.9rem;
  transition: all 0.2s;
  font-weight: 500;
}

.btn:disabled {
  opacity: 0.6;
  cursor: not-allowed;
}

.btn-secondary {
  background: #6c757d;
  color: white;
}

.btn-secondary:hover:not(:disabled) {
  background: #545b62;
}

.btn-primary {
  background: #007bff;
  color: white;
}

.btn-primary:hover:not(:disabled) {
  background: #0056b3;
}
</style>
