<script setup lang="ts">
import { ref, onMounted, computed, watch, nextTick } from 'vue'
import { useMonitorsStore, type MonitorRequest, type MonitorResponse, type MonitorState, type SilentFor } from '../stores/monitors'
import { silenceEnd } from '../utils/silence'
import { browserTimeZone, formatPauseInBrowserZone, timeZones, wallTime } from '../utils/dailyPause'
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
  environment: null,
  state: 'ACTIVE' as MonitorState,
  httpHeaders: {},
  statusCodeRegex: '^[23]\\d{2}$',
  responseBodyRegex: '',
  prometheusKey: '',
  prometheusMinValue: undefined,
  prometheusMaxValue: undefined,
  checkInterval: 15,
  connectTimeout: 5,
  useSystemDns: false,
  alertingThreshold: 30,
  showOnPublicPage: false
})

// The end of the silence the form shows: the new choice, else the running silence
const formSilentUntil = computed(() => {
  if (form.value.state !== 'SILENT') return null
  if (form.value.silentFor) return silenceEnd(form.value.silentFor).toISOString()
  return props.monitor?.state === 'SILENT' ? props.monitor.silentUntil ?? null : null
})

const onStateChange = (newState: MonitorState, silentFor?: SilentFor) => {
  form.value.state = newState
  form.value.silentFor = newState === 'SILENT' ? silentFor : undefined
}

// Daily pause: no checks in a time window each day, for example for a nightly database dump
const pauseEnabled = ref(false)
const allTimeZones = timeZones()
const pauseTimeZoneOptions = computed(() => {
  const tz = form.value.dailyPauseTimezone
  return tz && !allTimeZones.includes(tz) ? [tz, ...allTimeZones] : allTimeZones
})
const pauseIsValid = computed(
  () => !!form.value.dailyPauseFrom && !!form.value.dailyPauseUntil && form.value.dailyPauseFrom !== form.value.dailyPauseUntil
)
const pausePastMidnight = computed(
  () => pauseIsValid.value && form.value.dailyPauseUntil! < form.value.dailyPauseFrom!
)
const pauseZoneNow = computed(() => wallTime(form.value.dailyPauseTimezone || 'UTC'))
const pauseInBrowserZone = computed(() =>
  pauseIsValid.value
    ? formatPauseInBrowserZone({
        from: form.value.dailyPauseFrom!,
        until: form.value.dailyPauseUntil!,
        timezone: form.value.dailyPauseTimezone || 'UTC'
      })
    : null
)
const myTimeZone = browserTimeZone()

const onPauseToggle = () => {
  if (pauseEnabled.value) {
    form.value.dailyPauseFrom ||= '02:00'
    form.value.dailyPauseUntil ||= '02:30'
    form.value.dailyPauseTimezone ||= myTimeZone
  }
}

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
      environment: props.monitor.environment ?? null,
      state: props.monitor.state,
      httpHeaders: props.monitor.httpHeaders || {},
      statusCodeRegex: props.monitor.statusCodeRegex || '',
      responseBodyRegex: props.monitor.responseBodyRegex || '',
      prometheusKey: props.monitor.prometheusKey || '',
      prometheusMinValue: props.monitor.prometheusMinValue,
      prometheusMaxValue: props.monitor.prometheusMaxValue,
      checkInterval: props.monitor.checkInterval ?? 15,
      connectTimeout: props.monitor.connectTimeout ?? 5,
      useSystemDns: props.monitor.useSystemDns ?? false,
      alertingThreshold: props.monitor.alertingThreshold,
      showOnPublicPage: props.monitor.showOnPublicPage ?? false,
      dailyPauseFrom: props.monitor.dailyPauseFrom ?? undefined,
      dailyPauseUntil: props.monitor.dailyPauseUntil ?? undefined,
      dailyPauseTimezone: props.monitor.dailyPauseTimezone ?? undefined
    }
    pauseEnabled.value = !!(props.monitor.dailyPauseFrom && props.monitor.dailyPauseUntil)
  } else {
    // Reset form for new monitor
    const defaultTenantId = props.preselectedTenantId || currentUser.value?.tenants[0]?.id || 0
    form.value = {
      name: '',
      url: '',
      tenantId: defaultTenantId,
      environment: null,
      state: 'ACTIVE' as MonitorState,
      httpHeaders: {},
      statusCodeRegex: '^[23]\\d{2}$',
      responseBodyRegex: '',
      prometheusKey: '',
      prometheusMinValue: undefined,
      prometheusMaxValue: undefined,
      checkInterval: 15,
      connectTimeout: 5,
      useSystemDns: false,
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

  if (pauseEnabled.value && !pauseIsValid.value) {
    error.value = 'The daily pause needs a start and an end, and they must not be the same'
    return false
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
      silentFor: form.value.state === 'SILENT' ? form.value.silentFor : undefined,
      // No daily pause: send no times, the backend then removes it
      dailyPauseFrom: pauseEnabled.value ? form.value.dailyPauseFrom : undefined,
      dailyPauseUntil: pauseEnabled.value ? form.value.dailyPauseUntil : undefined,
      dailyPauseTimezone: pauseEnabled.value ? form.value.dailyPauseTimezone || myTimeZone : undefined,
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

// The error shows at the top of the form. In a long form the user is at the bottom, so bring it into view.
const errorBox = ref<HTMLElement | null>(null)
watch(error, async (message) => {
  if (!message) return
  await nextTick()
  errorBox.value?.scrollIntoView?.({ block: 'nearest', behavior: 'smooth' })
})
</script>

<template>
  <FormModal :title="title" size="md" @close="handleClose">
    <form @submit.prevent="handleSubmit" class="form-stack">
      <p v-if="error" ref="errorBox" class="callout callout-danger" role="alert">
        {{ error }}
      </p>

      <section class="form-section" aria-labelledby="monitor-basics-title">
        <h3 id="monitor-basics-title" class="form-section-title">Basics</h3>
        <div class="form-grid">
          <div class="field">
            <label for="monitor-name">Name</label>
            <input
              id="monitor-name"
              v-model="form.name"
              type="text"
              placeholder="For example Shop API"
              required
            />
          </div>

          <div class="field">
            <label for="monitor-environment">Environment</label>
            <select id="monitor-environment" v-model="form.environment">
              <option :value="null">Not set</option>
              <option value="PROD">Production</option>
              <option value="STAGE">Stage</option>
              <option value="DEV">Development</option>
            </select>
            <p class="field-hint">Shows next to the name, so the name does not need it.</p>
          </div>

          <div class="field span-all">
            <label for="monitor-url">URL</label>
            <input
              id="monitor-url"
              v-model="form.url"
              class="mono"
              type="url"
              placeholder="https://api.example.com/health"
              required
            />
          </div>

          <div v-if="isEditMode && monitor && !(currentUser && currentUser.tenants.length > 1)" class="field">
            <label for="monitor-tenant">Tenant</label>
            <input
              id="monitor-tenant"
              :value="monitor.tenant.name"
              type="text"
              disabled
            />
          </div>

          <div v-else-if="isEditMode && monitor && currentUser" class="field" :class="{ 'span-all': isTenantChanged }">
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
            <p v-if="isTenantChanged" class="callout callout-warning">
              The monitor moves with all its history. Alert contacts of the old tenant that are
              limited to selected monitors lose this monitor.
            </p>
          </div>

          <div v-else-if="currentUser && currentUser.tenants.length > 1" class="field">
            <label for="monitor-tenant-select">Tenant</label>
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

          <div class="field">
            <label for="check-interval">Check every <span class="optional">(seconds)</span></label>
            <input
              id="check-interval"
              v-model.number="form.checkInterval"
              class="num"
              type="number"
              min="15"
              max="3600"
              step="15"
              placeholder="15"
              required
            />
            <p class="field-hint">A multiple of 15, from 15 to 3600.</p>
          </div>
        </div>

        <div class="toggle-field">
          <label class="check" for="show-on-public-page">
            <input id="show-on-public-page" v-model="form.showOnPublicPage" type="checkbox" />
            Show on the public status page
          </label>
          <p class="field-hint">
            Anyone can see the
            <router-link v-if="publicPagePath" :to="publicPagePath" target="_blank">public status page</router-link>
            <template v-else>public status page</template>
            of the tenant without sign-in. It shows only the name, the current status and the 90-day uptime.
          </p>
        </div>
      </section>

      <section class="form-section" aria-labelledby="monitor-criteria-title">
        <h3 id="monitor-criteria-title" class="form-section-title">Success criteria</h3>
        <SuccessCriteriaInput
          v-if="!isInitializing"
          v-model="successCriteriaData"
        />
      </section>

      <section class="form-section" aria-labelledby="monitor-headers-title">
        <h3 id="monitor-headers-title" class="form-section-title">Request headers</h3>
        <p class="field-hint">Extra HTTP headers that each check sends, for example a token.</p>
        <HttpHeadersInput v-model="form.httpHeaders" />
      </section>

      <section class="form-section" aria-labelledby="monitor-alerting-title">
        <h3 id="monitor-alerting-title" class="form-section-title">Alerting</h3>
        <div class="form-grid">
          <div class="field">
            <label for="alerting-threshold">Alert after <span class="optional">(seconds down)</span></label>
            <input
              id="alerting-threshold"
              v-model.number="form.alertingThreshold"
              class="num"
              type="number"
              min="15"
              step="15"
              placeholder="30"
              required
            />
            <p class="field-hint">A multiple of 15, at least 15.</p>
          </div>
          <div class="field">
            <span class="field-label">State</span>
            <MonitorStateButton
              :state="form.state"
              :silent-until="formSilentUntil"
              @state-change="onStateChange"
            />
          </div>
        </div>
      </section>

      <section class="form-section" aria-labelledby="monitor-pause-title">
        <h3 id="monitor-pause-title" class="form-section-title">Daily pause</h3>
        <div class="toggle-field">
          <label class="check" for="daily-pause-enabled">
            <input id="daily-pause-enabled" v-model="pauseEnabled" type="checkbox" @change="onPauseToggle" />
            Pause checks every day
          </label>
          <p class="field-hint">
            For a planned downtime each day, for example a database dump. In this time the monitor has no
            checks and sends no alerts. The uptime does not count this time.
          </p>
        </div>
        <div v-if="pauseEnabled" class="form-grid">
          <div class="field">
            <label for="daily-pause-from">From</label>
            <input id="daily-pause-from" v-model="form.dailyPauseFrom" class="num" type="time" required />
          </div>
          <div class="field">
            <label for="daily-pause-until">Until</label>
            <input id="daily-pause-until" v-model="form.dailyPauseUntil" class="num" type="time" required />
          </div>
          <div class="field span-all">
            <label for="daily-pause-timezone">Time zone of these times</label>
            <select id="daily-pause-timezone" v-model="form.dailyPauseTimezone" required>
              <option v-for="tz in pauseTimeZoneOptions" :key="tz" :value="tz">
                {{ tz }}{{ tz === myTimeZone ? ' (your time zone)' : '' }}
              </option>
            </select>
            <p class="field-hint">
              The times are in {{ form.dailyPauseTimezone || 'UTC' }}. It is {{ pauseZoneNow }} there now.
              The pause follows the summer and winter time of this zone.
              <template v-if="pauseInBrowserZone">
                Today in your time zone ({{ myTimeZone }}): {{ pauseInBrowserZone }}.
              </template>
              <template v-if="pausePastMidnight">The end is before the start, so the pause goes past midnight.</template>
            </p>
          </div>
        </div>
      </section>

      <section class="form-section" aria-labelledby="monitor-advanced-title">
        <h3 id="monitor-advanced-title" class="form-section-title">Advanced</h3>
        <div class="form-grid">
          <div class="field">
            <label for="connect-timeout">Connect timeout <span class="optional">(seconds)</span></label>
            <input
              id="connect-timeout"
              v-model.number="form.connectTimeout"
              class="num"
              type="number"
              min="1"
              max="10"
              step="1"
              placeholder="5"
              required
            />
            <p class="field-hint">Maximum wait for the connection, 1 to 10.</p>
          </div>
        </div>
        <div class="toggle-field">
          <label class="check" for="use-system-dns">
            <input id="use-system-dns" v-model="form.useSystemDns" type="checkbox" />
            Use the DNS server of the backend
          </label>
          <p class="field-hint">
            Off (default): each check asks the nameservers of the domain for the IP, so a changed IP shows at the
            next check. On: the DNS server of the backend gives the IP. It can keep an old IP for hours after a
            change. Use this for names that only the DNS of the backend knows.
          </p>
        </div>
      </section>

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
