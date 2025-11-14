<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { useAlertContactsStore, type AlertContactRequest, type AlertContactResponse } from '../stores/alertContacts'
import { useAuthStore } from '../stores/auth'
import type { CurrentUser } from '../services/api'

const props = defineProps<{
  contact?: AlertContactResponse | null
  preselectedTenantId?: number | null
}>()

const emit = defineEmits<{
  close: []
  success: []
}>()

const alertContactsStore = useAlertContactsStore()
const authStore = useAuthStore()

const form = ref<AlertContactRequest>({
  type: 'EMAIL',
  value: '',
  name: '',
  isActive: true,
  httpMethod: 'GET',
  httpHeaders: {},
  httpBody: '',
  httpContentType: 'application/json'
})

// Manage headers as an array with stable IDs for proper reactivity
const headerItems = ref<Array<{ id: string; key: string; value: string }>>([])

const selectedTenantId = ref<number>(0)
const isSubmitting = ref(false)
const error = ref<string | null>(null)
const currentUser = ref<CurrentUser | null>(null)

onMounted(async () => {
  try {
    const userData = await authStore.fetchCurrentUser()
    currentUser.value = userData

    // If editing, populate form with existing data
    if (props.contact) {
      form.value = {
        type: props.contact.type,
        value: props.contact.value,
        name: props.contact.name || '',
        isActive: props.contact.isActive,
        httpMethod: props.contact.httpMethod || 'GET',
        httpHeaders: props.contact.httpHeaders || {},
        httpBody: props.contact.httpBody || '',
        httpContentType: props.contact.httpContentType || 'application/json'
      }
      selectedTenantId.value = props.contact.tenant.id
    } else {
      // Set default tenant using preselected tenant ID or first available tenant for new contacts
      const defaultTenantId = props.preselectedTenantId || (userData?.tenants && userData.tenants.length > 0 ? userData.tenants[0]?.id ?? 0 : 0)
      selectedTenantId.value = defaultTenantId
    }

    // Initialize headers
    initializeHeaders()
  } catch (err) {
    error.value = 'Failed to load user data'
    console.error('Failed to load user data:', err)
  }
})

const validateForm = (): boolean => {
  if (!form.value.value.trim()) {
    error.value = form.value.type === 'EMAIL' ? 'Email address is required' : 'URL is required'
    return false
  }

  if (!selectedTenantId.value) {
    error.value = 'Please select a tenant'
    return false
  }

  if (form.value.type === 'EMAIL') {
    // Basic email validation
    const emailRegex = /^[^\s@]+@[^\s@]+\.[^\s@]+$/
    if (!emailRegex.test(form.value.value)) {
      error.value = 'Please enter a valid email address'
      return false
    }
  } else if (form.value.type === 'HTTP') {
    // Basic URL validation
    const urlRegex = /^https?:\/\/.+/
    if (!urlRegex.test(form.value.value)) {
      error.value = 'Please enter a valid HTTP/HTTPS URL'
      return false
    }
  }

  return true
}

const handleSubmit = async () => {
  error.value = null

  if (!validateForm()) {
    return
  }

  isSubmitting.value = true

  try {
    if (props.contact) {
      // Update existing contact
      await alertContactsStore.updateAlertContact(props.contact.id, form.value)
    } else {
      // Create new contact
      await alertContactsStore.createAlertContact(selectedTenantId.value, form.value)
    }
    emit('success')
    emit('close')
  } catch (err) {
    error.value = props.contact
      ? 'Failed to update alert contact. Please try again.'
      : 'Failed to create alert contact. Please try again.'
    console.error('Submit alert contact error:', err)
  } finally {
    isSubmitting.value = false
  }
}

const addHeader = () => {
  const id = `header-${Date.now()}-${Math.random()}`
  headerItems.value.push({ id, key: '', value: '' })
  updateHttpHeaders()
}

const removeHeader = (id: string) => {
  const item = headerItems.value.find(item => item.id === id)
  if (item && (item.key || item.value)) {
    if (!confirm('Are you sure you want to remove this header?')) {
      return
    }
  }

  const index = headerItems.value.findIndex(item => item.id === id)
  if (index !== -1) {
    headerItems.value.splice(index, 1)
    updateHttpHeaders()
  }
}

const updateHeaderKey = (id: string, newKey: string) => {
  const item = headerItems.value.find(item => item.id === id)
  if (item) {
    item.key = newKey
    updateHttpHeaders()
  }
}

const updateHeaderValue = (id: string, newValue: string) => {
  const item = headerItems.value.find(item => item.id === id)
  if (item) {
    item.value = newValue
    updateHttpHeaders()
  }
}

// Sync headerItems with form.httpHeaders
const updateHttpHeaders = () => {
  const headers: Record<string, string> = {}
  headerItems.value.forEach(item => {
    if (item.key.trim()) {
      headers[item.key] = item.value
    }
  })
  form.value.httpHeaders = headers
}

// Initialize headerItems from existing httpHeaders
const initializeHeaders = () => {
  headerItems.value = Object.entries(form.value.httpHeaders || {}).map(([key, value]) => ({
    id: `header-${Date.now()}-${Math.random()}`,
    key,
    value
  }))
}

const handleClose = () => {
  emit('close')
}
</script>

<template>
  <div class="modal-overlay" @click="handleClose">
    <div class="modal-content" @click.stop>
      <div class="modal-header">
        <h2>{{ props.contact ? 'Edit Alert Contact' : 'Add Alert Contact' }}</h2>
        <button @click="handleClose" class="close-btn" type="button">
          ×
        </button>
      </div>

      <form @submit.prevent="handleSubmit" class="alert-contact-form">
        <div class="form-group">
          <label for="type">Alert Type *</label>
          <select id="type" v-model="form.type" required>
            <option value="EMAIL">Email</option>
            <option value="HTTP">HTTP Webhook</option>
          </select>
        </div>

        <div class="form-group">
          <label for="name">Name (Optional)</label>
          <input
            id="name"
            v-model="form.name"
            type="text"
            placeholder="e.g., Primary Admin"
          />
        </div>

        <div v-if="form.type === 'EMAIL'" class="form-group">
          <label for="email">Email Address *</label>
          <input
            id="email"
            v-model="form.value"
            type="email"
            placeholder="alerts@example.com"
            required
          />
        </div>

        <div v-if="form.type === 'HTTP'" class="form-group">
          <label for="url">Webhook URL *</label>
          <input
            id="url"
            v-model="form.value"
            type="url"
            placeholder="https://example.com/webhook"
            required
          />
          <small class="form-help-text">
            Supports variables: &#123;&#123;MONITOR_NAME&#125;&#125;, &#123;&#123;MONITOR_URL&#125;&#125;, &#123;&#123;TENANT_NAME&#125;&#125;, &#123;&#123;STATUS_CODE&#125;&#125;, &#123;&#123;RESPONSE_BODY&#125;&#125;,&#123;&#123;ALERT_TYPE&#125;&#125;, &#123;&#123;TIMESTAMP&#125;&#125;
          </small>
        </div>

        <div v-if="form.type === 'HTTP'" class="form-group">
          <label for="method">HTTP Method</label>
          <select id="method" v-model="form.httpMethod">
            <option value="GET">GET</option>
            <option value="POST">POST</option>
          </select>
        </div>

        <div v-if="form.type === 'HTTP' && form.httpMethod === 'POST'" class="form-group">
          <label for="contentType">Content Type</label>
          <select id="contentType" v-model="form.httpContentType">
            <option value="application/json">JSON (application/json)</option>
            <option value="text/plain">Plain Text (text/plain)</option>
          </select>
        </div>

        <div v-if="form.type === 'HTTP' && form.httpMethod === 'POST'" class="form-group">
          <label for="body">Request Body</label>
          <textarea
            id="body"
            v-model="form.httpBody"
            placeholder="Request body content with variables"
            rows="4"
          ></textarea>
          <small class="form-help-text">
            Supports the same variables as URL. Leave empty for no body.
          </small>
        </div>

        <div v-if="form.type === 'HTTP'" class="form-group">
          <label>Custom Headers</label>
          <div class="headers-container">
            <div v-for="item in headerItems" :key="item.id" class="header-row">
              <input
                :value="item.key"
                @input="updateHeaderKey(item.id, ($event.target as HTMLInputElement).value)"
                placeholder="Header name"
                class="header-key"
                maxlength="100"
              />
              <input
                :value="item.value"
                @input="updateHeaderValue(item.id, ($event.target as HTMLInputElement).value)"
                placeholder="Header value"
                class="header-value"
                maxlength="200"
              />
              <button @click="removeHeader(item.id)" type="button" class="remove-header-btn">×</button>
            </div>
            <button @click="addHeader" type="button" class="add-header-btn">+ Add Header</button>
          </div>
          <small class="form-help-text">
            Header values support the same variables as URL.
          </small>
        </div>

        <div v-if="currentUser && currentUser.tenants.length > 1" class="form-group">
          <label for="tenant">Tenant *</label>
          <select
            id="tenant"
            v-model="selectedTenantId"
            :disabled="!!props.contact"
            required
          >
            <option v-for="tenant in currentUser.tenants" :key="tenant.id" :value="tenant.id">
              {{ tenant.name }}
            </option>
          </select>
          <small v-if="props.contact" class="form-help-text">
            Tenant cannot be changed when editing
          </small>
        </div>

        <div class="form-group">
          <label class="checkbox-label">
            <input
              v-model="form.isActive"
              type="checkbox"
            />
            <span class="checkmark"></span>
            Contact is active
          </label>
        </div>

        <div v-if="error" class="error-message">
          {{ error }}
        </div>

        <div class="form-actions">
          <button type="button" @click="handleClose" class="btn btn-cancel">
            Cancel
          </button>
          <button type="submit" :disabled="isSubmitting" class="btn btn-primary">
            {{ isSubmitting
              ? (props.contact ? 'Updating...' : 'Creating...')
              : (props.contact ? 'Update Alert Contact' : 'Create Alert Contact')
            }}
          </button>
        </div>
      </form>
    </div>
  </div>
</template>

<style scoped>
.modal-overlay {
  position: fixed;
  top: 0;
  left: 0;
  right: 0;
  bottom: 0;
  background: rgba(0, 0, 0, 0.5);
  display: flex;
  align-items: center;
  justify-content: center;
  z-index: 1000;
}

.modal-content {
  background: white;
  border-radius: 8px;
  width: 90%;
  max-width: 500px;
  max-height: 90vh;
  overflow-y: auto;
  box-shadow: 0 4px 6px rgba(0, 0, 0, 0.1);
}

.modal-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 1.5rem;
  border-bottom: 1px solid #e9ecef;
}

.modal-header h2 {
  margin: 0;
  color: #2c3e50;
}

.close-btn {
  background: none;
  border: none;
  font-size: 1.5rem;
  cursor: pointer;
  color: #6c757d;
  padding: 0;
  width: 2rem;
  height: 2rem;
  display: flex;
  align-items: center;
  justify-content: center;
}

.close-btn:hover {
  color: #495057;
}

.alert-contact-form {
  padding: 1.5rem;
}

.form-group {
  margin-bottom: 1.5rem;
}

.form-group label {
  display: block;
  margin-bottom: 0.5rem;
  font-weight: 500;
  color: #2c3e50;
}

.form-group input[type="text"],
.form-group input[type="email"],
.form-group input[type="url"],
.form-group select,
.form-group textarea {
  width: 100%;
  padding: 0.75rem;
  border: 1px solid #dee2e6;
  border-radius: 4px;
  font-size: 1rem;
  transition: border-color 0.2s;
}

.form-group input:focus,
.form-group select:focus {
  outline: none;
  border-color: #007bff;
  box-shadow: 0 0 0 2px rgba(0, 123, 255, 0.25);
}

.form-group select:disabled {
  background-color: #f8f9fa;
  color: #6c757d;
  cursor: not-allowed;
}

.form-help-text {
  color: #6c757d;
  font-size: 0.875rem;
  margin-top: 0.5rem;
  display: block;
}

.checkbox-label {
  display: flex;
  align-items: center;
  cursor: pointer;
  font-weight: normal !important;
}

.checkbox-label input[type="checkbox"] {
  margin-right: 0.5rem;
}

.error-message {
  background: #f8d7da;
  color: #721c24;
  padding: 0.75rem;
  border-radius: 4px;
  margin-bottom: 1rem;
  border: 1px solid #f5c6cb;
}

.form-actions {
  display: flex;
  gap: 1rem;
  justify-content: flex-end;
  margin-top: 2rem;
}

.btn {
  padding: 0.75rem 1.5rem;
  border: none;
  border-radius: 4px;
  cursor: pointer;
  font-size: 1rem;
  transition: all 0.2s;
  font-weight: 500;
}

.btn:disabled {
  opacity: 0.6;
  cursor: not-allowed;
}

.btn-cancel {
  background: #6c757d;
  color: white;
}

.btn-cancel:hover:not(:disabled) {
  background: #545b62;
}

.btn-primary {
  background: #007bff;
  color: white;
}

.btn-primary:hover:not(:disabled) {
  background: #0056b3;
}

.headers-container {
  border: 1px solid #dee2e6;
  border-radius: 4px;
  padding: 1rem;
  background: #f8f9fa;
  box-sizing: border-box;
  width: 100%;
}

.header-row {
  display: flex;
  gap: 0.5rem;
  margin-bottom: 0.5rem;
  align-items: center;
  max-width: 100%;
  box-sizing: border-box;
}

.header-key,
.header-value {
  flex: 1;
  min-width: 120px;
  padding: 0.5rem;
  border: 1px solid #dee2e6;
  border-radius: 4px;
  font-size: 0.875rem;
}

.remove-header-btn {
  background: #dc3545;
  color: white;
  border: none;
  border-radius: 4px;
  width: 2rem;
  height: 2rem;
  cursor: pointer;
  font-size: 1rem;
  display: flex;
  align-items: center;
  justify-content: center;
  flex-shrink: 0;
}

.remove-header-btn:hover {
  background: #c82333;
}

.add-header-btn {
  background: #28a745;
  color: white;
  border: none;
  border-radius: 4px;
  padding: 0.5rem 1rem;
  cursor: pointer;
  font-size: 0.875rem;
  margin-top: 0.5rem;
  width: fit-content;
}

.add-header-btn:hover {
  background: #218838;
}

.form-group textarea {
  resize: vertical;
  min-height: 100px;
}
</style>
