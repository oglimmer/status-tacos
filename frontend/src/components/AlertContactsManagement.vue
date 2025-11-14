<script setup lang="ts">
import { ref, onMounted, computed, watch } from 'vue'
import { useAlertContactsStore, type AlertContactResponse } from '../stores/alertContacts'
import { useAuthStore } from '../stores/auth'
import AlertContactForm from './AlertContactForm.vue'
import AlertContactsHeader from './AlertContactsHeader.vue'
import AlertContactsSummary from './AlertContactsSummary.vue'
import AlertContactCard from './AlertContactCard.vue'
import LoadingState from './LoadingState.vue'
import EmptyState from './EmptyState.vue'
import ErrorState from './ErrorState.vue'
import type { CurrentUser } from '../services/api'
import { userConfigService } from '../services/userConfig'

const alertContactsStore = useAlertContactsStore()
const authStore = useAuthStore()

const showForm = ref(false)
const editingContact = ref<AlertContactResponse | null>(null)
const currentUser = ref<CurrentUser | null>(null)
const selectedTenantId = ref<number | null>(userConfigService.getAlertContactTenantFilter())

const filteredContacts = computed(() => {
  if (!selectedTenantId.value) {
    return alertContactsStore.alertContacts
  }
  return alertContactsStore.getAlertContactsByTenant(selectedTenantId.value)
})

// Summary data computed property
const summaryData = computed(() => {
  const contacts = filteredContacts.value
  return {
    total: contacts.length,
    active: contacts.filter(c => c.isActive).length,
    inactive: contacts.filter(c => !c.isActive).length,
    email: contacts.filter(c => c.type === 'EMAIL').length,
    webhook: contacts.filter(c => c.type === 'HTTP').length
  }
})

onMounted(async () => {
  try {
    const userData = await authStore.fetchCurrentUser()
    currentUser.value = userData

    // Load all alert contacts
    await alertContactsStore.fetchAlertContacts()

    // Set default tenant filter if not already set
    if (!selectedTenantId.value && userData.tenants.length > 0) {
      selectedTenantId.value = null // Default to 'All Tenants'
    }
  } catch (err) {
    console.error('Failed to load data:', err)
  }
})

const handleAddContact = () => {
  showForm.value = true
}

const handleFormSuccess = () => {
  editingContact.value = null
}

const handleEdit = (contact: AlertContactResponse) => {
  editingContact.value = contact
  showForm.value = true
}

const handleCloseForm = () => {
  showForm.value = false
  editingContact.value = null
}

const handleDelete = async (id: number) => {
  if (confirm('Are you sure you want to delete this alert contact?')) {
    try {
      await alertContactsStore.deleteAlertContact(id)
    } catch (err) {
      console.error('Failed to delete alert contact:', err)
    }
  }
}

const handleToggleStatus = async (id: number) => {
  try {
    await alertContactsStore.toggleAlertContactStatus(id)
  } catch (err) {
    console.error('Failed to toggle alert contact status:', err)
  }
}

const handleTestNotification = async (id: number) => {
  try {
    await alertContactsStore.sendTestNotification(id)
    alert('Test notification sent successfully!')
  } catch (err) {
    console.error('Failed to send test notification:', err)
    alert('Failed to send test notification. Please try again.')
  }
}

// Watch for changes to selectedTenantId and save to localStorage
watch(selectedTenantId, (newValue) => {
  userConfigService.setAlertContactTenantFilter(newValue)
})
</script>

<template>
  <div class="alert-contacts-management">
    <AlertContactsHeader @add-contact="handleAddContact" />

    <AlertContactsSummary
      v-if="!alertContactsStore.isLoading && !alertContactsStore.error && alertContactsStore.alertContacts.length > 0"
      :summary="summaryData"
      :tenants="currentUser?.tenants"
      :selected-tenant-id="selectedTenantId"
      @update:selected-tenant-id="selectedTenantId = $event"
    />

    <LoadingState
      v-if="alertContactsStore.isLoading"
      message="Loading alert contacts..."
    />

    <ErrorState
      v-else-if="alertContactsStore.error"
      :error="alertContactsStore.error"
    />

    <EmptyState
      v-else-if="alertContactsStore.alertContacts.length === 0"
      title="No alert contacts yet"
      message="Get started by adding your first alert contact to receive notifications when monitors go down."
      button-text="Add Your First Alert Contact"
      @action="handleAddContact"
    />

    <div v-else class="contacts-list">
      <AlertContactCard
        v-for="contact in filteredContacts"
        :key="contact.id"
        :contact="contact"
        @edit="handleEdit"
        @toggle-status="handleToggleStatus"
        @delete="handleDelete"
        @test-notification="handleTestNotification"
      />
    </div>

    <AlertContactForm
      v-if="showForm"
      :contact="editingContact"
      :preselected-tenant-id="selectedTenantId"
      @close="handleCloseForm"
      @success="handleFormSuccess"
    />
  </div>
</template>

<style scoped>
.contacts-list {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(400px, 1fr));
  gap: 1.5rem;
  margin-bottom: 2rem;
}
</style>
