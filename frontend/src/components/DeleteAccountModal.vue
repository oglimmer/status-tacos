<script setup lang="ts">
import { computed, ref } from 'vue'
import { useRouter } from 'vue-router'
import { useAuthStore } from '../stores/auth'
import FormModal from './FormModal.vue'

const emit = defineEmits<{
  close: []
}>()

const CONFIRM_WORD = 'DELETE'

const authStore = useAuthStore()
const router = useRouter()

const confirmText = ref('')
const isDeleting = ref(false)
const error = ref<string | null>(null)

const canDelete = computed(() => confirmText.value.trim() === CONFIRM_WORD && !isDeleting.value)

const close = () => {
  if (!isDeleting.value) {
    emit('close')
  }
}

const handleDelete = async () => {
  if (!canDelete.value) return
  isDeleting.value = true
  error.value = null
  try {
    const result = await authStore.deleteAccount()
    await router.push({
      name: 'home',
      query: { accountDeleted: result.loginAccountDeleted ? 'all' : 'data' }
    })
  } catch (err) {
    console.error('Account deletion failed:', err)
    error.value = 'Your account could not be deleted. Nothing was deleted. Please try again later.'
    isDeleting.value = false
  }
}
</script>

<template>
  <FormModal title="Delete Account" @close="close">
    <div class="delete-account">
      <p class="warning">
        <span class="warning-icon">⚠️</span>
        This permanently deletes your account. You cannot undo it.
      </p>
      <ul class="consequences">
        <li>Your login account and your profile are deleted.</li>
        <li>
          Every tenant that only you belong to is deleted, with its monitors, check history and
          alert contacts.
        </li>
        <li>Tenants you share with other users stay with them. You are removed from them.</li>
        <li>Your iOS push alerts stop.</li>
      </ul>
      <label class="confirm-label" for="delete-account-confirm">
        Type <strong>{{ CONFIRM_WORD }}</strong> to confirm:
      </label>
      <input
        id="delete-account-confirm"
        v-model="confirmText"
        class="confirm-input"
        type="text"
        autocomplete="off"
        :disabled="isDeleting"
        @keyup.enter="handleDelete"
      />
      <div v-if="error" class="error-message">{{ error }}</div>
      <div class="actions">
        <button type="button" class="btn btn-secondary" :disabled="isDeleting" @click="close">
          Cancel
        </button>
        <button type="button" class="btn btn-danger" :disabled="!canDelete" @click="handleDelete">
          {{ isDeleting ? 'Deleting...' : 'Delete My Account' }}
        </button>
      </div>
    </div>
  </FormModal>
</template>

<style scoped>
.delete-account {
  color: #495057;
  line-height: 1.6;
}

.warning {
  display: flex;
  gap: 0.5rem;
  align-items: flex-start;
  background: rgba(220, 53, 69, 0.08);
  border: 1px solid rgba(220, 53, 69, 0.25);
  color: #b02a37;
  border-radius: 8px;
  padding: 0.75rem 1rem;
  margin: 0 0 1rem;
  font-weight: 600;
}

.consequences {
  margin: 0 0 1.5rem;
  padding-left: 1.25rem;
}

.consequences li {
  margin-bottom: 0.4rem;
}

.confirm-label {
  display: block;
  margin-bottom: 0.5rem;
  color: #2c3e50;
}

.confirm-input {
  width: 100%;
  padding: 0.75rem;
  border: 1px solid #ced4da;
  border-radius: 8px;
  font-size: 1rem;
}

.confirm-input:focus {
  outline: none;
  border-color: #dc3545;
  box-shadow: 0 0 0 3px rgba(220, 53, 69, 0.15);
}

.error-message {
  margin-top: 1rem;
  color: #dc3545;
  font-weight: 500;
}

.actions {
  display: flex;
  justify-content: flex-end;
  gap: 0.75rem;
  margin-top: 1.5rem;
}

.btn {
  display: inline-flex;
  align-items: center;
  gap: 0.5rem;
  padding: 0.75rem 1.5rem;
  border: none;
  border-radius: 8px;
  font-size: 1rem;
  font-weight: 600;
  cursor: pointer;
  transition: all 0.2s;
}

.btn:disabled {
  opacity: 0.6;
  cursor: not-allowed;
}

.btn-secondary {
  background: #f8f9fa;
  color: #495057;
  border: 1px solid #ced4da;
}

.btn-secondary:hover:not(:disabled) {
  background: #e9ecef;
}

.btn-danger {
  background: #dc3545;
  color: white;
}

.btn-danger:hover:not(:disabled) {
  background: #bb2d3b;
}
</style>
