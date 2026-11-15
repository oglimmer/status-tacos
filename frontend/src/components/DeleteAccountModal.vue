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
  <FormModal title="Delete account" @close="close">
    <form class="form-stack" @submit.prevent="handleDelete">
      <p v-if="error" class="callout callout-danger" role="alert">{{ error }}</p>
      <p class="callout callout-danger lead">
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
      <div class="field">
        <label for="delete-account-confirm">
          Type <span class="mono">{{ CONFIRM_WORD }}</span> to confirm
        </label>
        <input
          id="delete-account-confirm"
          v-model="confirmText"
          class="mono"
          type="text"
          autocomplete="off"
          :disabled="isDeleting"
        />
      </div>
      <div class="form-actions">
        <button type="button" class="btn btn-secondary" :disabled="isDeleting" @click="close">
          Cancel
        </button>
        <button type="submit" class="btn btn-danger" :disabled="!canDelete">
          {{ isDeleting ? 'Deleting...' : 'Delete my account' }}
        </button>
      </div>
    </form>
  </FormModal>
</template>

<style scoped>
.lead {
  font-weight: 700;
}

.consequences {
  margin: 0;
  padding-left: var(--space-5);
  color: var(--text-2);
}

.consequences li + li {
  margin-top: var(--space-1);
}
</style>
