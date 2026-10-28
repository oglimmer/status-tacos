<script setup lang="ts">
import { ref, onMounted, computed } from 'vue'
import { useMonitorsStore, type MonitorRequest, type MonitorResponse, type MonitorState } from '../stores/monitors'
import { useAuthStore } from '../stores/auth'
import FormModal from './FormModal.vue'
import HttpHeadersInput from './HttpHeadersInput.vue'
import SuccessCriteriaInput from './SuccessCriteriaInput.vue'
import MonitorStateButton from './MonitorStateButton.vue'
import type { CurrentUser } from '../services/api'
import { ApiError } from '../services/apiError'

const props = defineProps<{
  monitor?: MonitorResponse | null
  title: string
  submitText: string
  submittingText: string
  preselectedTenantId?: number | null
}>()

const emit = defineEmits<{
  close: []
  success: []
}>()

const monitorsStore = useMonitorsStore()
const authStore = useAuthStore()

const form = ref<MonitorRequest>({
  name: '',
  url: '',
  tenantId: 0,
  state: 'ACTIVE' as MonitorState,
  httpHeaders: {},
  statusCodeRegex: '^[23]\\d{2}$',
  responseBodyRegex: '',
  prometheusKey: '',
  prometheusMinValue: undefined,
  prometheusMaxValue: undefined,
  checkInterval: 15,
  connectTimeout: 5,
  alertingThreshold: 30,
  showOnPublicPage: false
})

const isSubmitting = ref(false)
const error = ref<string | null>(null)
const currentUser = ref<CurrentUser | null>(null)
const isInitializing = ref(true)

const isEditMode = computed(() => !!props.monitor)

// The public status page of the selected tenant
const publicPagePath = computed(() => {
  const code = currentUser.value?.tenants.find((t) => t.id === form.value.tenantId)?.code
  return code ? `/${encodeURIComponent(code)}/status` : null
})

const isTenantChanged = computed(
  () => isEditMode.value && !!props.monitor && form.value.tenantId !== props.monitor.tenant.id
)

// Success criteria data for the component
const successCriteriaData = computed({
  get: () => ({
    statusCodeRegex: form.value.statusCodeRegex || '',
    responseBodyRegex: form.value.responseBodyRegex || '',
    prometheusKey: form.value.prometheusKey || '',
    prometheusMinValue: form.value.prometheusMinValue,
    prometheusMaxValue: form.value.prometheusMaxValue
  }),
  set: (value) => {
    form.value.statusCodeRegex = value.statusCodeRegex
    form.value.responseBodyRegex = value.responseBodyRegex
    form.value.prometheusKey = value.prometheusKey
    form.value.prometheusMinValue = value.prometheusMinValue
    form.value.prometheusMaxValue = value.prometheusMaxValue
  }
})

const initializeForm = () => {
  if (props.monitor) {
    form.value = {
      name: props.monitor.name,
      url: props.monitor.url,
      tenantId: props.monitor.tenant.id,
      state: props.monitor.state,
      httpHeaders: props.monitor.httpHeaders || {},
      statusCodeRegex: props.monitor.statusCodeRegex || '',
      responseBodyRegex: props.monitor.responseBodyRegex || '',
      prometheusKey: props.monitor.prometheusKey || '',
      prometheusMinValue: props.monitor.prometheusMinValue,
      prometheusMaxValue: props.monitor.prometheusMaxValue,
      checkInterval: props.monitor.checkInterval ?? 15,
      connectTimeout: props.monitor.connectTimeout ?? 5,
      alertingThreshold: props.monitor.alertingThreshold,
      showOnPublicPage: props.monitor.showOnPublicPage ?? false
    }
  } else {
    // Reset form for new monitor
    const defaultTenantId = props.preselectedTenantId || currentUser.value?.tenants[0]?.id || 0
    form.value = {
      name: '',
      url: '',
      tenantId: defaultTenantId,
      state: 'ACTIVE' as MonitorState,
      httpHeaders: {},
      statusCodeRegex: '^[23]\\d{2}$',
      responseBodyRegex: '',
      prometheusKey: '',
      prometheusMinValue: undefined,
      prometheusMaxValue: undefined,
      checkInterval: 15,
      connectTimeout: 5,
      alertingThreshold: 30,
      showOnPublicPage: false
    }
  }
  isInitializing.value = false
}

onMounted(async () => {
  try {
    const userData = await authStore.fetchCurrentUser()
    currentUser.value = userData
    initializeForm()
  } catch (err) {
    error.value = 'Failed to load user data'
    console.error('Failed to load user data:', err)
  }
})

const validateForm = (): boolean => {
  if (!form.value.name.trim()) {
    error.value = 'Monitor name is required'
    return false
  }
  if (!form.value.url.trim()) {
    error.value = 'URL is required'
    return false
  }

  // Basic URL validation. The backend has the same rule: http or https, with a host.
  let parsedUrl: URL
  try {
    parsedUrl = new URL(form.value.url)
  } catch {
    error.value = 'Please enter a valid URL'
    return false
  }
  if (parsedUrl.protocol !== 'http:' && parsedUrl.protocol !== 'https:') {
    error.value = 'The URL must start with http:// or https://'
    return false
  }

  // Validate success criteria
  const hasStatusCode = form.value.statusCodeRegex && form.value.statusCodeRegex.trim()
  const hasResponseBody = form.value.responseBodyRegex && form.value.responseBodyRegex.trim()
  const hasPrometheus = form.value.prometheusKey && form.value.prometheusKey.trim()

  if (!hasStatusCode && !hasResponseBody && !hasPrometheus) {
    error.value = 'At least one success criteria is required'
    return false
  }

  // Validate regex patterns
  if (hasStatusCode && form.value.statusCodeRegex) {
    try {
      new RegExp(form.value.statusCodeRegex)
    } catch {
      error.value = 'Invalid status code regex pattern'
      return false
    }
  }

  if (hasResponseBody && form.value.responseBodyRegex) {
    try {
      new RegExp(form.value.responseBodyRegex)
    } catch {
      error.value = 'Invalid response body regex pattern'
      return false
    }
  }

  // Validate Prometheus thresholds
  if (hasPrometheus) {
    const hasMinValue = form.value.prometheusMinValue !== undefined && !isNaN(form.value.prometheusMinValue)
    const hasMaxValue = form.value.prometheusMaxValue !== undefined && !isNaN(form.value.prometheusMaxValue)

    if (!hasMinValue && !hasMaxValue) {
      error.value = 'At least one Prometheus threshold (min or max) is required'
      return false
    }
    if (hasMinValue && hasMaxValue) {
      if (form.value.prometheusMinValue! >= form.value.prometheusMaxValue!) {
        error.value = 'Prometheus minimum value must be less than maximum value'
        return false
      }
    }
  }

  // Validate check settings. The backend has the same rules.
  if (form.value.checkInterval !== undefined) {
    if (form.value.checkInterval < 15 || form.value.checkInterval > 3600) {
      error.value = 'Check interval must be between 15 and 3600 seconds'
      return false
    }
    if (form.value.checkInterval % 15 !== 0) {
      error.value = 'Check interval must be a multiple of 15 seconds'
      return false
    }
  }

  if (form.value.connectTimeout !== undefined) {
    if (!Number.isInteger(form.value.connectTimeout) || form.value.connectTimeout < 1 || form.value.connectTimeout > 10) {
      error.value = 'Connect timeout must be a whole number between 1 and 10 seconds'
      return false
    }
  }

  if (form.value.alertingThreshold !== undefined) {
    if (form.value.alertingThreshold < 15) {
      error.value = 'Alerting threshold must be at least 15 seconds'
      return false
    }
    if (form.value.alertingThreshold % 15 !== 0) {
      error.value = 'Alerting threshold must be a multiple of 15 seconds'
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
    const submitData = {
      ...form.value,
      prometheusMinValue: form.value.prometheusMinValue === undefined || isNaN(form.value.prometheusMinValue!) ? undefined : form.value.prometheusMinValue,
      prometheusMaxValue: form.value.prometheusMaxValue === undefined || isNaN(form.value.prometheusMaxValue!) ? undefined : form.value.prometheusMaxValue
    }

    if (isEditMode.value && props.monitor) {
      if (isTenantChanged.value) {
        try {
          await monitorsStore.moveMonitorToTenant(props.monitor.id, submitData.tenantId)
        } catch (err) {
          error.value = err instanceof ApiError && err.status === 409
            ? 'Failed to move monitor. The target tenant already has a monitor with this URL.'
            : 'Failed to move monitor. Please try again.'
          console.error('Move monitor error:', err)
          return
        }
      }
      await monitorsStore.updateMonitor(props.monitor.id, submitData)
    } else {
      await monitorsStore.createMonitor(submitData)
    }

    emit('success')
    emit('close')
  } catch (err) {
    error.value = `Failed to ${isEditMode.value ? 'update' : 'create'} monitor. Please try again.`
    console.error(`${isEditMode.value ? 'Update' : 'Create'} monitor error:`, err)
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
    <form @submit.prevent="handleSubmit" class="monitor-form">
      <div class="form-group">
        <label for="monitor-name">Monitor Name *</label>
        <input
          id="monitor-name"
          v-model="form.name"
          type="text"
          placeholder="e.g., Production API"
          required
        />
      </div>

      <div class="form-group">
        <label for="monitor-url">URL to Monitor *</label>
        <input
          id="monitor-url"
          v-model="form.url"
          type="url"
          placeholder="https://api.example.com/health"
          required
        />
      </div>

      <div v-if="isEditMode && monitor && !(currentUser && currentUser.tenants.length > 1)" class="form-group">
        <label for="monitor-tenant">Tenant</label>
        <input
          id="monitor-tenant"
          :value="monitor.tenant.name"
          type="text"
          disabled
          class="disabled-field"
        />
      </div>

      <div v-else-if="isEditMode && monitor && currentUser" class="form-group">
        <label for="monitor-tenant-move">Tenant</label>
        <select
          id="monitor-tenant-move"
          v-model="form.tenantId"
          required
        >
          <option v-for="tenant in currentUser.tenants" :key="tenant.id" :value="tenant.id">
            {{ tenant.name }}
          </option>
        </select>
        <small v-if="isTenantChanged" class="field-note tenant-move-note">
          The monitor moves with all its history. Alert contacts of the old tenant that are
          limited to selected monitors lose this monitor.
        </small>
      </div>

      <div v-else-if="currentUser && currentUser.tenants.length > 1" class="form-group">
        <label for="monitor-tenant-select">Tenant *</label>
        <select
          id="monitor-tenant-select"
          v-model="form.tenantId"
          required
        >
          <option v-for="tenant in currentUser.tenants" :key="tenant.id" :value="tenant.id">
            {{ tenant.name }}
          </option>
        </select>
      </div>

      <HttpHeadersInput v-model="form.httpHeaders" />

      <SuccessCriteriaInput
        v-if="!isInitializing"
        v-model="successCriteriaData"
      />

      <div class="form-group">
        <label>Check Settings *</label>
        <div class="check-settings">
          <div class="form-subgroup">
            <label for="check-interval">Check Interval (seconds)</label>
            <input
              id="check-interval"
              v-model.number="form.checkInterval"
              type="number"
              min="15"
              max="3600"
              step="15"
              placeholder="15"
              required
            />
            <small class="field-note">Time between two checks. A multiple of 15 seconds (15 to 3600).</small>
          </div>

          <div class="form-subgroup">
            <label for="connect-timeout">Connect Timeout (seconds)</label>
            <input
              id="connect-timeout"
              v-model.number="form.connectTimeout"
              type="number"
              min="1"
              max="10"
              step="1"
              placeholder="5"
              required
            />
            <small class="field-note">Maximum wait for the connection to the server (1 to 10).</small>
          </div>

          <div class="form-subgroup">
            <label for="alerting-threshold">Alerting Threshold (seconds)</label>
            <input
              id="alerting-threshold"
              v-model.number="form.alertingThreshold"
              type="number"
              min="15"
              step="15"
              placeholder="30"
              required
            />
            <small class="field-note">
              Time down before the alert goes out. A multiple of 15 seconds (minimum: 15).
            </small>
          </div>
        </div>
      </div>

      <div class="form-group">
        <label class="checkbox-label" for="show-on-public-page">
          <input id="show-on-public-page" v-model="form.showOnPublicPage" type="checkbox" />
          Show on the public status page
        </label>
        <small class="field-note">
          Anyone can see the
          <router-link v-if="publicPagePath" :to="publicPagePath" target="_blank">public status page</router-link>
          <template v-else>public status page</template>
          of the tenant without sign-in. It shows only the name, the current status and the 90-day uptime.
        </small>
      </div>

      <div class="form-group">
        <label class="form-label">Monitor State</label>
        <MonitorStateButton
          :state="form.state"
          @state-change="(newState) => form.state = newState"
        />
      </div>

      <div v-if="error" class="error-message">
        {{ error }}
      </div>

      <div class="form-actions">
        <button
          type="button"
          @click="handleClose"
          class="btn btn-secondary"
          :disabled="isSubmitting"
        >
          Cancel
        </button>
        <button
          type="submit"
          class="btn btn-primary"
          :disabled="isSubmitting"
        >
          {{ isSubmitting ? submittingText : submitText }}
        </button>
      </div>
    </form>
  </FormModal>
</template>

<style scoped>
.monitor-form {
  display: flex;
  flex-direction: column;
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
.form-group input[type="url"],
.form-group input[type="email"],
.form-group input[type="number"],
.form-group select {
  width: 100%;
  padding: 0.75rem;
  border: 1px solid #dee2e6;
  border-radius: 4px;
  font-size: 1rem;
  transition: border-color 0.2s;
  box-sizing: border-box;
}

.form-group input:focus,
.form-group select:focus {
  outline: none;
  border-color: #007bff;
  box-shadow: 0 0 0 2px rgba(0, 123, 255, 0.25);
}

.check-settings {
  border: 1px solid #dee2e6;
  border-radius: 4px;
  padding: 1rem;
  background: #f8f9fa;
  box-sizing: border-box;
  width: 100%;
}

.form-subgroup {
  margin-bottom: 0.75rem;
}

.form-subgroup:last-child {
  margin-bottom: 0;
}

.form-group .form-subgroup label {
  margin-bottom: 0.25rem;
  color: #495057;
  font-size: 0.875rem;
}

.disabled-field {
  background-color: #f8f9fa !important;
  color: #6c757d !important;
  cursor: not-allowed !important;
}

.field-note.tenant-move-note {
  color: #b45309;
}

.field-note {
  display: block;
  margin-top: 0.25rem;
  font-size: 0.875rem;
  color: #6c757d;
  font-style: italic;
}

.checkbox-label {
  display: flex;
  align-items: center;
  cursor: pointer;
  font-weight: normal !important;
}

.checkbox-label input[type="checkbox"] {
  margin-right: 0.5rem;
  width: auto;
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
