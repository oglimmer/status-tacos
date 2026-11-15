<script setup lang="ts">
import { ref, onMounted, computed, watch } from 'vue'
import { askConfirm } from '../services/confirm'
import { useAlertContactsStore, type AlertContactResponse } from '../stores/alertContacts'
import { useAuthStore } from '../stores/auth'
import AlertContactForm from './AlertContactForm.vue'
import PageHeader from './PageHeader.vue'
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
    webhook: contacts.filter(c => c.type === 'HTTP').length,
    teams: contacts.filter(c => c.type === 'TEAMS').length,
    iosPush: contacts.filter(c => c.type === 'IOS_PUSH').length
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
  const name = alertContactsStore.alertContacts.find((contact) => contact.id === id)?.name
  const confirmed = await askConfirm({
    title: name ? `Delete ${name}?` : 'Delete this alert contact?',
    message: 'Monitors stop sending alerts to it. You cannot undo this.',
    confirmLabel: 'Delete alert contact',
    danger: true,
  })
  if (confirmed) {
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
    <PageHeader
      title="Alert Contacts"
      subtitle="Who gets a message when a monitor goes down or comes back."
    >
      <template #actions>
        <button type="button" class="btn btn-primary" @click="handleAddContact">
          <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.5" stroke-linecap="round" aria-hidden="true">
            <path d="M12 5v14M5 12h14" />
          </svg>
          Add Alert Contact
        </button>
      </template>
    </PageHeader>

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

    <p v-else-if="filteredContacts.length === 0" class="list-empty">
      No alert contacts in this tenant.
    </p>

    <ul v-else class="list contacts-list" aria-label="Alert contacts">
      <AlertContactCard
        v-for="contact in filteredContacts"
        :key="contact.id"
        :contact="contact"
        @edit="handleEdit"
        @toggle-status="handleToggleStatus"
        @delete="handleDelete"
        @test-notification="handleTestNotification"
      />
    </ul>

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
.list-empty {
  margin: 0;
  padding: var(--space-5) var(--space-4);
  border: 1px dashed var(--border);
  border-radius: var(--radius-lg);
  color: var(--text-muted);
  text-align: center;
}
</style>
