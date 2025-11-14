import { ref, computed } from 'vue'
import { defineStore } from 'pinia'
import { apiService } from '../services/api'

export interface AlertContactRequest {
  type: 'EMAIL' | 'HTTP'
  value: string
  name?: string
  isActive: boolean
  httpMethod?: 'GET' | 'POST'
  httpHeaders?: Record<string, string>
  httpBody?: string
  httpContentType?: 'application/json' | 'text/plain'
}

export interface AlertContactResponse {
  id: number
  type: 'EMAIL' | 'HTTP'
  value: string
  name?: string
  isActive: boolean
  tenant: {
    id: number
    name: string
    code: string
    description?: string
    isActive: boolean
    createdAt: string
    updatedAt: string
  }
  createdAt: string
  updatedAt: string
  httpMethod?: 'GET' | 'POST'
  httpHeaders?: Record<string, string>
  httpBody?: string
  httpContentType?: 'application/json' | 'text/plain'
}

export const useAlertContactsStore = defineStore('alertContacts', () => {
  const alertContacts = ref<AlertContactResponse[]>([])
  const isLoading = ref(false)
  const error = ref<string | null>(null)

  const activeAlertContacts = computed(() =>
    alertContacts.value.filter(contact => contact.isActive)
  )

  const getAlertContactsByTenant = computed(() =>
    (tenantId: number) => alertContacts.value.filter(contact => contact.tenant.id === tenantId)
  )

  async function fetchAlertContacts(activeOnly = false) {
    isLoading.value = true
    error.value = null

    try {
      const contacts = await apiService.getAlertContacts(activeOnly)
      alertContacts.value = contacts
    } catch (err) {
      error.value = 'Failed to fetch alert contacts'
      console.error('Fetch alert contacts error:', err)
      throw err
    } finally {
      isLoading.value = false
    }
  }

  async function fetchAlertContactsByTenant(tenantId: number) {
    isLoading.value = true
    error.value = null

    try {
      const contacts = await apiService.getAlertContactsByTenant(tenantId)
      // Update the store with these contacts
      for (const contact of contacts) {
        const existingIndex = alertContacts.value.findIndex(c => c.id === contact.id)
        if (existingIndex >= 0) {
          alertContacts.value[existingIndex] = contact
        } else {
          alertContacts.value.push(contact)
        }
      }
      return contacts
    } catch (err) {
      error.value = 'Failed to fetch alert contacts for tenant'
      console.error('Fetch alert contacts by tenant error:', err)
      throw err
    } finally {
      isLoading.value = false
    }
  }

  async function createAlertContact(tenantId: number, request: AlertContactRequest) {
    isLoading.value = true
    error.value = null

    try {
      const newContact = await apiService.createAlertContact(tenantId, request)
      alertContacts.value.push(newContact)
      return newContact
    } catch (err) {
      error.value = 'Failed to create alert contact'
      console.error('Create alert contact error:', err)
      throw err
    } finally {
      isLoading.value = false
    }
  }

  async function updateAlertContact(id: number, request: AlertContactRequest) {
    isLoading.value = true
    error.value = null

    try {
      const updatedContact = await apiService.updateAlertContact(id, request)
      const index = alertContacts.value.findIndex(contact => contact.id === id)
      if (index >= 0) {
        alertContacts.value[index] = updatedContact
      }
      return updatedContact
    } catch (err) {
      error.value = 'Failed to update alert contact'
      console.error('Update alert contact error:', err)
      throw err
    } finally {
      isLoading.value = false
    }
  }

  async function deleteAlertContact(id: number) {
    isLoading.value = true
    error.value = null

    try {
      await apiService.deleteAlertContact(id)
      const index = alertContacts.value.findIndex(contact => contact.id === id)
      if (index >= 0) {
        alertContacts.value.splice(index, 1)
      }
    } catch (err) {
      error.value = 'Failed to delete alert contact'
      console.error('Delete alert contact error:', err)
      throw err
    } finally {
      isLoading.value = false
    }
  }

  async function toggleAlertContactStatus(id: number) {
    isLoading.value = true
    error.value = null

    try {
      const updatedContact = await apiService.toggleAlertContactStatus(id)
      const index = alertContacts.value.findIndex(contact => contact.id === id)
      if (index >= 0) {
        alertContacts.value[index] = updatedContact
      }
      return updatedContact
    } catch (err) {
      error.value = 'Failed to toggle alert contact status'
      console.error('Toggle alert contact status error:', err)
      throw err
    } finally {
      isLoading.value = false
    }
  }

  async function sendTestNotification(id: number) {
    isLoading.value = true
    error.value = null

    try {
      await apiService.sendTestNotification(id)
    } catch (err) {
      error.value = 'Failed to send test notification'
      console.error('Send test notification error:', err)
      throw err
    } finally {
      isLoading.value = false
    }
  }

  return {
    alertContacts,
    activeAlertContacts,
    getAlertContactsByTenant,
    isLoading,
    error,
    fetchAlertContacts,
    fetchAlertContactsByTenant,
    createAlertContact,
    updateAlertContact,
    deleteAlertContact,
    toggleAlertContactStatus,
    sendTestNotification
  }
})
