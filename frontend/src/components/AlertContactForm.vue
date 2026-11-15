<script setup lang="ts">
import { ref, computed, watch, onMounted } from 'vue'
import { useAlertContactsStore, type AlertContactRequest, type AlertContactResponse } from '../stores/alertContacts'
import { useAuthStore } from '../stores/auth'
import type { MonitorResponse } from '../stores/monitors'
import { apiService, type CurrentUser } from '../services/api'
import FormModal from './FormModal.vue'

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
  httpContentType: 'application/json',
  allMonitors: true,
  monitorIds: []
})

// Manage headers as an array with stable IDs for proper reactivity
const headerItems = ref<Array<{ id: string; key: string; value: string }>>([])

const selectedTenantId = ref<number>(0)
const isSubmitting = ref(false)
const error = ref<string | null>(null)
const currentUser = ref<CurrentUser | null>(null)
const monitors = ref<MonitorResponse[]>([])
const monitorsLoading = ref(false)

// Only monitors of the contact's tenant can be selected
const tenantMonitors = computed(() =>
  monitors.value
    .filter(monitor => monitor.tenantId === selectedTenantId.value)
    .sort((a, b) => a.name.localeCompare(b.name))
)

watch(selectedTenantId, () => {
  // Monitors not loaded yet: keep the selection of the edited contact
  if (monitors.value.length === 0) {
    return
  }
  const allowed = new Set(tenantMonitors.value.map(monitor => monitor.id))
  form.value.monitorIds = (form.value.monitorIds || []).filter(id => allowed.has(id))
})

const loadMonitors = async () => {
  monitorsLoading.value = true
  try {
    monitors.value = await apiService.getMonitors()
  } catch (err) {
    console.error('Failed to load monitors:', err)
  } finally {
    monitorsLoading.value = false
  }
}

onMounted(async () => {
  try {
    const userData = await authStore.fetchCurrentUser()
    currentUser.value = userData

    // IOS_PUSH contacts are managed in the iOS app; the card offers no edit for them
    if (props.contact?.type === 'IOS_PUSH') {
      emit('close')
      return
    }

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
        httpContentType: props.contact.httpContentType || 'application/json',
        allMonitors: props.contact.allMonitors ?? true,
        monitorIds: (props.contact.monitors || []).map(monitor => monitor.id)
      }
      selectedTenantId.value = props.contact.tenant.id
    } else {
      // Set default tenant using preselected tenant ID or first available tenant for new contacts
      const defaultTenantId = props.preselectedTenantId || (userData?.tenants && userData.tenants.length > 0 ? userData.tenants[0]?.id ?? 0 : 0)
      selectedTenantId.value = defaultTenantId
    }

    // Initialize headers
    initializeHeaders()

    await loadMonitors()
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
  } else if (form.value.type === 'TEAMS') {
    if (!/^https:\/\/.+/.test(form.value.value)) {
      error.value = 'Please enter the https:// URL of the Teams workflow'
      return false
    }
  }

  if (!form.value.allMonitors && !form.value.monitorIds?.length) {
    error.value = 'Please select at least one monitor, or send alerts for all monitors'
    return false
  }

  return true
}

const handleSubmit = async () => {
  error.value = null

  if (!validateForm()) {
    return
  }

  isSubmitting.value = true

  const request: AlertContactRequest = {
    ...form.value,
    monitorIds: form.value.allMonitors ? [] : form.value.monitorIds
  }

  try {
    if (props.contact) {
      // Update existing contact
      await alertContactsStore.updateAlertContact(props.contact.id, request)
    } else {
      // Create new contact
      await alertContactsStore.createAlertContact(selectedTenantId.value, request)
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

const VARIABLES = ['MONITOR_NAME', 'MONITOR_URL', 'TENANT_NAME', 'STATUS_CODE', 'RESPONSE_BODY', 'ALERT_TYPE', 'TIMESTAMP']
</script>

<template>
  <FormModal
    :title="props.contact ? 'Edit alert contact' : 'Add alert contact'"
    size="md"
    @close="handleClose"
  >
    <form @submit.prevent="handleSubmit" class="form-stack">
      <p v-if="error" class="callout callout-danger" role="alert">{{ error }}</p>

      <section class="form-section" aria-labelledby="contact-target-title">
        <h3 id="contact-target-title" class="form-section-title">Where alerts go</h3>
        <div class="form-grid">
          <div class="field">
            <label for="type">Type</label>
            <select id="type" v-model="form.type" required>
              <option value="EMAIL">Email</option>
              <option value="HTTP">HTTP webhook</option>
              <option value="TEAMS">Microsoft Teams</option>
            </select>
          </div>

          <div class="field">
            <label for="name">Name <span class="optional">(optional)</span></label>
            <input
              id="name"
              v-model="form.name"
              type="text"
              placeholder="For example Primary admin"
            />
          </div>

          <div v-if="form.type === 'EMAIL'" class="field span-all">
            <label for="email">Email address</label>
            <input
              id="email"
              v-model="form.value"
              type="email"
              placeholder="alerts@example.com"
              required
            />
          </div>

          <div v-if="form.type === 'HTTP'" class="field span-all">
            <label for="url">Webhook URL</label>
            <input
              id="url"
              v-model="form.value"
              class="mono"
              type="url"
              placeholder="https://example.com/webhook"
              required
            />
            <p class="field-hint">
              You can use these variables:
              <template v-for="(name, index) in VARIABLES" :key="name"><code v-text="`{{${name}}}`"></code><template v-if="index < VARIABLES.length - 1">, </template></template>
            </p>
          </div>

          <div v-if="form.type === 'TEAMS'" class="field span-all">
            <label for="teamsUrl">Teams workflow URL</label>
            <input
              id="teamsUrl"
              v-model="form.value"
              class="mono"
              type="url"
              placeholder="https://….powerplatform.com/…/triggers/manual/paths/invoke?…"
              required
            />
            <p class="field-hint">
              In Teams, open the channel, select "…" → Workflows → "Send webhook alerts to a channel",
              and copy the URL here. Keep the URL secret: everybody with it can post to the channel.
              Each alert is a card with the monitor, URL, reason, status code, response time,
              down since / downtime and links to the monitored URL and Status Tacos.
            </p>
          </div>

          <div v-if="currentUser && currentUser.tenants.length > 1" class="field span-all">
            <label for="tenant">Tenant</label>
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
            <p v-if="props.contact" class="field-hint">
              The tenant cannot change after the contact is created.
            </p>
          </div>
        </div>

        <label class="check">
          <input v-model="form.isActive" type="checkbox" />
          Contact is active
        </label>
      </section>

      <section v-if="form.type === 'HTTP'" class="form-section" aria-labelledby="contact-request-title">
        <h3 id="contact-request-title" class="form-section-title">Webhook request</h3>
        <div class="form-grid">
          <div class="field">
            <label for="method">Method</label>
            <select id="method" v-model="form.httpMethod" class="mono">
              <option value="GET">GET</option>
              <option value="POST">POST</option>
            </select>
          </div>

          <div v-if="form.httpMethod === 'POST'" class="field">
            <label for="contentType">Content type</label>
            <select id="contentType" v-model="form.httpContentType">
              <option value="application/json">JSON (application/json)</option>
              <option value="text/plain">Plain text (text/plain)</option>
            </select>
          </div>

          <div v-if="form.httpMethod === 'POST'" class="field span-all">
            <label for="body">Body <span class="optional">(optional)</span></label>
            <textarea
              id="body"
              v-model="form.httpBody"
              class="mono"
              placeholder="The body, with variables"
              rows="4"
            ></textarea>
            <p class="field-hint">The same variables as in the URL work here. Leave it empty for no body.</p>
          </div>
        </div>

        <div class="field">
          <span class="field-label">Headers</span>
          <div class="repeat-rows">
            <p v-if="headerItems.length === 0" class="field-hint">No headers.</p>
            <div v-for="(item, index) in headerItems" :key="item.id" class="repeat-row">
              <input
                :value="item.key"
                @input="updateHeaderKey(item.id, ($event.target as HTMLInputElement).value)"
                placeholder="Name"
                class="input mono header-key"
                :aria-label="`Header ${index + 1} name`"
                maxlength="100"
              />
              <input
                :value="item.value"
                @input="updateHeaderValue(item.id, ($event.target as HTMLInputElement).value)"
                placeholder="Value"
                class="input mono header-value"
                :aria-label="`Header ${index + 1} value`"
                maxlength="200"
              />
              <button
                @click="removeHeader(item.id)"
                type="button"
                class="btn btn-icon btn-sm"
                :aria-label="`Remove header ${item.key || index + 1}`"
              >
                <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" aria-hidden="true">
                  <path d="M6 6l12 12M18 6L6 18" />
                </svg>
              </button>
            </div>
            <button @click="addHeader" type="button" class="btn btn-link btn-sm repeat-add">
              <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" aria-hidden="true">
                <path d="M12 5v14M5 12h14" />
              </svg>
              Add header
            </button>
          </div>
          <p class="field-hint">Header values can use the same variables as the URL.</p>
        </div>
      </section>

      <section class="form-section" aria-labelledby="contact-scope-title">
        <h3 id="contact-scope-title" class="form-section-title">Send alerts for</h3>
        <div class="scope-choice" role="radiogroup" aria-labelledby="contact-scope-title">
          <label class="check">
            <input v-model="form.allMonitors" type="radio" :value="true" name="scope" />
            All monitors of the tenant
          </label>
          <label class="check">
            <input v-model="form.allMonitors" type="radio" :value="false" name="scope" />
            Only selected monitors
          </label>
        </div>
        <template v-if="!form.allMonitors">
          <div class="monitor-list">
            <p v-if="monitorsLoading" class="monitor-list-empty">Loading monitors…</p>
            <p v-else-if="tenantMonitors.length === 0" class="monitor-list-empty">
              This tenant has no monitors yet.
            </p>
            <label
              v-for="monitor in tenantMonitors"
              :key="monitor.id"
              class="check monitor-option"
            >
              <input v-model="form.monitorIds" type="checkbox" :value="monitor.id" />
              <span class="monitor-option-text">
                <span>{{ monitor.name }}</span>
                <small class="mono">{{ monitor.url }}</small>
              </span>
            </label>
          </div>
          <p class="field-hint">New monitors are not added to this contact automatically.</p>
        </template>
      </section>

      <div class="form-actions">
        <button type="button" @click="handleClose" class="btn btn-secondary">
          Cancel
        </button>
        <button type="submit" :disabled="isSubmitting" class="btn btn-primary">
          {{ isSubmitting
            ? 'Saving...'
            : (props.contact ? 'Save changes' : 'Add alert contact')
          }}
        </button>
      </div>
    </form>
  </FormModal>
</template>

<style scoped>
.scope-choice {
  display: flex;
  flex-wrap: wrap;
  gap: var(--space-2) var(--space-5);
}

.monitor-list {
  max-height: 15rem;
  overflow-y: auto;
  border: 1px solid var(--border-subtle);
  border-radius: var(--radius-md);
}

.monitor-option {
  display: flex;
  align-items: flex-start;
  padding: var(--space-2) var(--space-3);
  border-bottom: 1px solid var(--border-subtle);
}

.monitor-option:last-child {
  border-bottom: 0;
}

.monitor-option:hover {
  background: var(--surface-2);
}

.monitor-option input {
  margin-top: 0.2rem;
}

.monitor-option-text {
  display: flex;
  flex-direction: column;
  min-width: 0;
}

.monitor-option-text small {
  font-size: var(--text-xs);
  color: var(--text-muted);
  overflow-wrap: anywhere;
}

.monitor-list-empty {
  margin: 0;
  padding: var(--space-3);
  color: var(--text-muted);
  font-size: var(--text-sm);
}
</style>
