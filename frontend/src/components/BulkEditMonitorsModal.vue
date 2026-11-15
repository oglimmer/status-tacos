<script setup lang="ts">
import { computed, onUnmounted, reactive, ref } from 'vue'
import FormModal from './FormModal.vue'
import {
  useMonitorsStore,
  type MonitorEnvironment,
  type MonitorResponse,
  type MonitorState,
  type SilentFor
} from '../stores/monitors'
import SilencePicker from './SilencePicker.vue'
import { runWithConcurrency, toBulkRequest, type BulkEditField, type BulkPatch } from '../utils/bulkEdit'

const props = defineProps<{
  monitors: MonitorResponse[]
}>()

const emit = defineEmits<{
  close: []
  // Some monitors changed: the page loads the data again
  finished: []
}>()

const monitorsStore = useMonitorsStore()

// Requests at the same time. Enough to be fast, few enough to not flood the backend.
const CONCURRENCY = 4

type Phase = 'edit' | 'running' | 'done'
type ItemStatus = 'queued' | 'running' | 'ok' | 'failed' | 'skipped'

interface Item {
  monitor: MonitorResponse
  status: ItemStatus
  error?: string
}

const phase = ref<Phase>('edit')
const formError = ref<string | null>(null)

const enabled = reactive<Record<BulkEditField, boolean>>({
  state: false,
  checkInterval: false,
  connectTimeout: false,
  alertingThreshold: false,
  statusCodeRegex: false,
  useSystemDns: false,
  showOnPublicPage: false,
  environment: false
})

const first = props.monitors[0]
const values = reactive({
  state: (first?.state ?? 'ACTIVE') as MonitorState,
  silentFor: '24h' as SilentFor,
  checkInterval: first?.checkInterval ?? 15,
  connectTimeout: first?.connectTimeout ?? 5,
  alertingThreshold: first?.alertingThreshold ?? 30,
  statusCodeRegex: first?.statusCodeRegex || '^[23]\\d{2}$',
  useSystemDns: first?.useSystemDns ?? false,
  showOnPublicPage: first?.showOnPublicPage ?? false,
  environment: (first?.environment ?? null) as MonitorEnvironment | null
})

const FIELD_LABELS: Record<BulkEditField, string> = {
  state: 'State',
  checkInterval: 'Check interval',
  connectTimeout: 'Connect timeout',
  alertingThreshold: 'Alerting threshold',
  statusCodeRegex: 'Status code regex',
  useSystemDns: 'DNS server of the backend',
  showOnPublicPage: 'Show on public status page',
  environment: 'Environment'
}

const ENVIRONMENT_LABELS: Record<MonitorEnvironment, string> = {
  PROD: 'Production',
  STAGE: 'Stage',
  DEV: 'Development'
}
const ENVIRONMENT_CHOICES = [null, 'PROD', 'STAGE', 'DEV'] as const

const formatValue = (field: BulkEditField, value: unknown): string => {
  if (field === 'environment') return value ? ENVIRONMENT_LABELS[value as MonitorEnvironment] : 'Not set'
  if (value === undefined || value === null || value === '') return '(none)'
  if (field === 'checkInterval' || field === 'connectTimeout' || field === 'alertingThreshold') return `${value}s`
  if (field === 'useSystemDns' || field === 'showOnPublicPage') return value ? 'On' : 'Off'
  return String(value)
}

const currentValueOf = (monitor: MonitorResponse, field: BulkEditField): unknown => {
  const request = toBulkRequest(monitor, {})
  return request[field]
}

/** "15s (12) · 60s (3)": the values the selected monitors have now. */
const currentSummary = (field: BulkEditField): string => {
  const counts = new Map<string, number>()
  for (const monitor of props.monitors) {
    const text = formatValue(field, currentValueOf(monitor, field))
    counts.set(text, (counts.get(text) ?? 0) + 1)
  }
  if (counts.size === 1) return `All: ${[...counts.keys()][0]}`
  const sorted = [...counts.entries()].sort((a, b) => b[1] - a[1])
  const shown = sorted.slice(0, 3).map(([text, count]) => `${text} (${count})`)
  if (sorted.length > 3) shown.push(`+${sorted.length - 3} more`)
  return `Mixed: ${shown.join(' · ')}`
}

const fields = Object.keys(FIELD_LABELS) as BulkEditField[]
const enabledFields = computed(() => fields.filter((f) => enabled[f]))

const patch = computed<BulkPatch>(() => {
  const result: BulkPatch = {}
  for (const field of enabledFields.value) {
    ;(result as Record<string, unknown>)[field] = values[field]
  }
  if (enabled.state && values.state === 'SILENT') result.silentFor = values.silentFor
  return result
})

const validate = (): string | null => {
  if (enabledFields.value.length === 0) return 'Select at least one setting to change'
  if (enabled.checkInterval) {
    const v = values.checkInterval
    if (!Number.isInteger(v) || v < 15 || v > 3600 || v % 15 !== 0) {
      return 'Check interval must be a multiple of 15 seconds, between 15 and 3600'
    }
  }
  if (enabled.connectTimeout) {
    const v = values.connectTimeout
    if (!Number.isInteger(v) || v < 1 || v > 10) return 'Connect timeout must be a whole number between 1 and 10 seconds'
  }
  if (enabled.alertingThreshold) {
    const v = values.alertingThreshold
    if (!Number.isInteger(v) || v < 15 || v % 15 !== 0) {
      return 'Alerting threshold must be a multiple of 15 seconds (minimum: 15)'
    }
  }
  if (enabled.statusCodeRegex) {
    if (!values.statusCodeRegex.trim()) return 'The status code regex must not be empty'
    try {
      new RegExp(values.statusCodeRegex)
    } catch {
      return 'Invalid status code regex pattern'
    }
  }
  return null
}

// ---- Run ----

const items = ref<Item[]>([])
const startedAt = ref(0)
const now = ref(Date.now())
let ticker: ReturnType<typeof setInterval> | null = null
let controller: AbortController | null = null
const stopping = ref(false)

const count = (status: ItemStatus) => items.value.filter((i) => i.status === status).length
const okCount = computed(() => count('ok'))
const failedCount = computed(() => count('failed'))
const skippedCount = computed(() => count('skipped'))
const finishedCount = computed(() => okCount.value + failedCount.value + skippedCount.value)
const total = computed(() => items.value.length)
const percent = computed(() => (total.value ? Math.round((finishedCount.value / total.value) * 100) : 0))
const elapsedSeconds = computed(() => Math.max(0, Math.floor((now.value - startedAt.value) / 1000)))
const remainingSeconds = computed(() => {
  const done = okCount.value + failedCount.value
  if (done === 0 || phase.value !== 'running') return null
  const perItem = (now.value - startedAt.value) / done
  return Math.ceil((perItem * (total.value - finishedCount.value)) / 1000)
})

const onBeforeUnload = (event: BeforeUnloadEvent) => {
  event.preventDefault()
}

const run = async (targets: Item[]) => {
  phase.value = 'running'
  stopping.value = false
  controller = new AbortController()
  startedAt.value = Date.now()
  now.value = startedAt.value
  ticker = setInterval(() => (now.value = Date.now()), 250)
  window.addEventListener('beforeunload', onBeforeUnload)

  targets.forEach((item) => {
    item.status = 'queued'
    item.error = undefined
  })

  await runWithConcurrency(
    targets,
    CONCURRENCY,
    async (item) => {
      item.status = 'running'
      try {
        // Use the newest data of the monitor, so the update keeps changes from other places
        const latest = monitorsStore.getMonitorById(item.monitor.id) ?? item.monitor
        item.monitor = await monitorsStore.updateMonitor(item.monitor.id, toBulkRequest(latest, patch.value))
        item.status = 'ok'
      } catch (err) {
        item.status = 'failed'
        item.error = err instanceof Error ? err.message : 'Update failed'
      }
    },
    controller.signal
  )

  targets.filter((i) => i.status === 'queued').forEach((i) => (i.status = 'skipped'))
  finishRun()
}

const finishRun = () => {
  if (ticker) clearInterval(ticker)
  ticker = null
  now.value = Date.now()
  window.removeEventListener('beforeunload', onBeforeUnload)
  phase.value = 'done'
  emit('finished')
}

const start = () => {
  formError.value = validate()
  if (formError.value) return
  items.value = props.monitors.map((monitor) => ({ monitor, status: 'queued' as ItemStatus }))
  run(items.value)
}

const stop = () => {
  stopping.value = true
  controller?.abort()
}

const retry = () => {
  run(items.value.filter((i) => i.status === 'failed' || i.status === 'skipped'))
}

const handleClose = () => {
  // While requests run, the dialog stays open: the user sees the result
  if (phase.value === 'running') return
  emit('close')
}

onUnmounted(() => {
  if (ticker) clearInterval(ticker)
  window.removeEventListener('beforeunload', onBeforeUnload)
  controller?.abort()
})

// While it runs, the rows stay in place. At the end, the failed rows go to the top.
const ORDER: Record<ItemStatus, number> = { failed: 0, skipped: 1, running: 2, queued: 3, ok: 4 }
const sortedItems = computed(() =>
  phase.value === 'running' ? items.value : [...items.value].sort((a, b) => ORDER[a.status] - ORDER[b.status])
)

const title = computed(() => {
  const n = props.monitors.length
  const noun = n === 1 ? 'monitor' : 'monitors'
  if (phase.value === 'running') return `Updating ${n} ${noun}…`
  if (phase.value === 'done') return 'Update finished'
  return `Edit ${n} ${noun}`
})
</script>

<template>
  <FormModal :title="title" size="md" @close="handleClose">
    <!-- Step 1: choose the settings -->
    <form v-if="phase === 'edit'" class="bulk-form form-stack" @submit.prevent="start">
      <p v-if="formError" class="callout callout-danger" role="alert">{{ formError }}</p>
      <p class="field-hint intro">
        Tick the settings to change. All other settings of each monitor stay as they are.
      </p>

      <section class="form-section" aria-labelledby="bulk-checks-title">
        <h3 id="bulk-checks-title" class="form-section-title">Checks and alerts</h3>
        <div class="bulk-fields">
          <div class="bulk-field" :class="{ on: enabled.state }">
            <label class="check field-toggle">
              <input v-model="enabled.state" type="checkbox" />
              <span class="field-name">{{ FIELD_LABELS.state }}</span>
            </label>
            <div class="current">{{ currentSummary('state') }}</div>
            <div v-if="enabled.state" class="segmented" role="radiogroup" :aria-label="FIELD_LABELS.state">
              <label v-for="s in (['ACTIVE', 'SILENT', 'INACTIVE'] as const)" :key="s" class="chip" :class="{ active: values.state === s }">
                <input v-model="values.state" class="sr-only" type="radio" :value="s" />
                {{ s === 'ACTIVE' ? 'Active' : s === 'SILENT' ? 'Silent (no alerts)' : 'Inactive (no checks)' }}
              </label>
            </div>
            <div v-if="enabled.state && values.state === 'SILENT'" class="silence-choice">
              <span class="unit">Silent for</span>
              <SilencePicker
                :selected="values.silentFor"
                label="Silent for"
                @pick="(code) => (values.silentFor = code)"
              />
            </div>
          </div>

          <div class="bulk-field" :class="{ on: enabled.checkInterval }">
            <label class="check field-toggle">
              <input v-model="enabled.checkInterval" type="checkbox" />
              <span class="field-name">{{ FIELD_LABELS.checkInterval }}</span>
            </label>
            <div class="current">{{ currentSummary('checkInterval') }}</div>
            <div v-if="enabled.checkInterval" class="input-row">
              <input v-model.number="values.checkInterval" class="input num" type="number" min="15" max="3600" step="15" :aria-label="FIELD_LABELS.checkInterval" />
              <span class="unit">seconds, a multiple of 15, from 15 to 3600</span>
            </div>
          </div>

          <div class="bulk-field" :class="{ on: enabled.connectTimeout }">
            <label class="check field-toggle">
              <input v-model="enabled.connectTimeout" type="checkbox" />
              <span class="field-name">{{ FIELD_LABELS.connectTimeout }}</span>
            </label>
            <div class="current">{{ currentSummary('connectTimeout') }}</div>
            <div v-if="enabled.connectTimeout" class="input-row">
              <input v-model.number="values.connectTimeout" class="input num" type="number" min="1" max="10" step="1" :aria-label="FIELD_LABELS.connectTimeout" />
              <span class="unit">seconds, 1 to 10</span>
            </div>
          </div>

          <div class="bulk-field" :class="{ on: enabled.alertingThreshold }">
            <label class="check field-toggle">
              <input v-model="enabled.alertingThreshold" type="checkbox" />
              <span class="field-name">{{ FIELD_LABELS.alertingThreshold }}</span>
            </label>
            <div class="current">{{ currentSummary('alertingThreshold') }}</div>
            <div v-if="enabled.alertingThreshold" class="input-row">
              <input v-model.number="values.alertingThreshold" class="input num" type="number" min="15" step="15" :aria-label="FIELD_LABELS.alertingThreshold" />
              <span class="unit">seconds down before the alert, a multiple of 15</span>
            </div>
          </div>

          <div class="bulk-field" :class="{ on: enabled.statusCodeRegex }">
            <label class="check field-toggle">
              <input v-model="enabled.statusCodeRegex" type="checkbox" />
              <span class="field-name">{{ FIELD_LABELS.statusCodeRegex }}</span>
            </label>
            <div class="current mono">{{ currentSummary('statusCodeRegex') }}</div>
            <div v-if="enabled.statusCodeRegex" class="input-row">
              <input v-model="values.statusCodeRegex" type="text" class="input mono" placeholder="^[23]\d{2}$" :aria-label="FIELD_LABELS.statusCodeRegex" />
            </div>
          </div>

          <div class="bulk-field" :class="{ on: enabled.useSystemDns }">
            <label class="check field-toggle">
              <input v-model="enabled.useSystemDns" type="checkbox" />
              <span class="field-name">{{ FIELD_LABELS.useSystemDns }}</span>
            </label>
            <div class="current">{{ currentSummary('useSystemDns') }}</div>
            <div v-if="enabled.useSystemDns" class="segmented" role="radiogroup" :aria-label="FIELD_LABELS.useSystemDns">
              <label class="chip" :class="{ active: !values.useSystemDns }">
                <input v-model="values.useSystemDns" class="sr-only" type="radio" :value="false" /> Off (ask the domain's nameservers)
              </label>
              <label class="chip" :class="{ active: values.useSystemDns }">
                <input v-model="values.useSystemDns" class="sr-only" type="radio" :value="true" /> On
              </label>
            </div>
          </div>
        </div>
      </section>

      <section class="form-section" aria-labelledby="bulk-display-title">
        <h3 id="bulk-display-title" class="form-section-title">Display</h3>
        <div class="bulk-fields">
          <div class="bulk-field" :class="{ on: enabled.showOnPublicPage }">
            <label class="check field-toggle">
              <input v-model="enabled.showOnPublicPage" type="checkbox" />
              <span class="field-name">{{ FIELD_LABELS.showOnPublicPage }}</span>
            </label>
            <div class="current">{{ currentSummary('showOnPublicPage') }}</div>
            <div v-if="enabled.showOnPublicPage" class="segmented" role="radiogroup" :aria-label="FIELD_LABELS.showOnPublicPage">
              <label class="chip" :class="{ active: values.showOnPublicPage }">
                <input v-model="values.showOnPublicPage" class="sr-only" type="radio" :value="true" /> Show
              </label>
              <label class="chip" :class="{ active: !values.showOnPublicPage }">
                <input v-model="values.showOnPublicPage" class="sr-only" type="radio" :value="false" /> Hide
              </label>
            </div>
          </div>

          <div class="bulk-field" :class="{ on: enabled.environment }">
            <label class="check field-toggle">
              <input v-model="enabled.environment" type="checkbox" />
              <span class="field-name">{{ FIELD_LABELS.environment }}</span>
            </label>
            <div class="current">{{ currentSummary('environment') }}</div>
            <div v-if="enabled.environment" class="segmented" role="radiogroup" :aria-label="FIELD_LABELS.environment">
              <label v-for="env in ENVIRONMENT_CHOICES" :key="env ?? 'none'" class="chip" :class="{ active: values.environment === env }">
                <input v-model="values.environment" class="sr-only" type="radio" :value="env" />
                {{ env ? ENVIRONMENT_LABELS[env] : 'Not set' }}
              </label>
            </div>
          </div>
        </div>
      </section>

      <div class="form-actions">
        <button type="button" class="btn btn-secondary" @click="emit('close')">Cancel</button>
        <button type="submit" class="btn btn-primary" :disabled="enabledFields.length === 0">
          Update {{ monitors.length }} {{ monitors.length === 1 ? 'monitor' : 'monitors' }}
        </button>
      </div>
    </form>

    <!-- Step 2 and 3: progress and result -->
    <div v-else class="progress-view form-stack" aria-live="polite">
      <ul class="changes">
        <li v-for="field in enabledFields" :key="field">
          {{ FIELD_LABELS[field] }} → <strong>{{ formatValue(field, values[field]) }}</strong>
        </li>
      </ul>

      <div>
        <div
          class="progress-bar"
          :class="{ 'has-failures': failedCount > 0, complete: phase === 'done' }"
          role="progressbar"
          :aria-valuenow="finishedCount"
          :aria-valuemin="0"
          :aria-valuemax="total"
        >
          <div class="progress-fill" :style="{ width: `${percent}%` }" />
        </div>

        <div class="progress-stats">
          <span class="main-stat">
            <template v-if="phase === 'running'">
              <span class="num">{{ finishedCount }}</span> of <span class="num">{{ total }}</span> done
            </template>
            <template v-else-if="failedCount === 0 && skippedCount === 0">
              All <span class="num">{{ total }}</span> updated
            </template>
            <template v-else>
              {{ okCount }} of {{ total }} updated
            </template>
          </span>
          <span class="side-stats">
            <span v-if="failedCount" class="stat-failed">{{ failedCount }} failed</span>
            <span v-if="skippedCount" class="stat-skipped">{{ skippedCount }} not started</span>
            <span class="num">
              {{ elapsedSeconds }}s<template v-if="remainingSeconds !== null">, about {{ remainingSeconds }}s left</template>
            </span>
          </span>
        </div>
      </div>

      <ul class="item-list">
        <li v-for="item in sortedItems" :key="item.monitor.id" :class="`item-${item.status}`">
          <span class="item-icon" :aria-label="item.status">
            <span v-if="item.status === 'running'" class="spinner" />
            <svg v-else-if="item.status === 'ok'" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.5" stroke-linecap="round" stroke-linejoin="round" aria-hidden="true"><path d="M5 12.5l4.5 4.5L19 7.5" /></svg>
            <svg v-else-if="item.status === 'failed'" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.5" stroke-linecap="round" aria-hidden="true"><path d="M6 6l12 12M18 6L6 18" /></svg>
            <template v-else-if="item.status === 'skipped'">–</template>
            <template v-else>○</template>
          </span>
          <span class="item-name">{{ item.monitor.name }}</span>
          <span v-if="item.error" class="item-error">{{ item.error }}</span>
        </li>
      </ul>

      <div class="form-actions">
        <template v-if="phase === 'running'">
          <button type="button" class="btn btn-secondary" :disabled="stopping" @click="stop">
            {{ stopping ? 'Stopping…' : 'Stop' }}
          </button>
        </template>
        <template v-else>
          <button v-if="failedCount + skippedCount > 0" type="button" class="btn btn-secondary" @click="retry">
            Retry {{ failedCount + skippedCount }}
          </button>
          <button type="button" class="btn btn-primary" @click="emit('close')">Close</button>
        </template>
      </div>
    </div>
  </FormModal>
</template>

<style scoped>
.intro {
  margin: 0;
}

/* The settings as one list. A ticked row gets a teal edge: it will change. */
.bulk-fields {
  border: 1px solid var(--border-subtle);
  border-radius: var(--radius-md);
  overflow: hidden;
}

.bulk-field {
  padding: var(--space-2) var(--space-3);
  border-bottom: 1px solid var(--border-subtle);
}

.bulk-field:last-child {
  border-bottom: 0;
}

.bulk-field.on {
  background: var(--surface-2);
  box-shadow: inset 3px 0 0 var(--accent);
}

.field-name {
  font-weight: 700;
}

/* The current value, in line with the text of the checkbox */
.current,
.input-row,
.segmented,
.silence-choice {
  margin-left: calc(1rem + var(--space-2));
}

.current {
  font-size: var(--text-sm);
  color: var(--text-muted);
  overflow-wrap: anywhere;
}

.input-row,
.segmented,
.silence-choice {
  margin-top: var(--space-2);
}

.input-row {
  display: flex;
  align-items: center;
  flex-wrap: wrap;
  gap: var(--space-2);
}

.input-row .input[type='number'] {
  width: 7rem;
}

.input-row .input[type='text'] {
  flex: 1;
  min-width: 10rem;
}

.unit {
  font-size: var(--text-sm);
  color: var(--text-muted);
}

.segmented {
  display: flex;
  flex-wrap: wrap;
  gap: var(--space-1);
}

.segmented .chip:has(input:focus-visible) {
  outline: 2px solid var(--focus);
  outline-offset: 2px;
}

.silence-choice {
  max-width: 24rem;
}

.silence-choice .unit {
  display: block;
  margin-bottom: var(--space-1);
}

/* Progress */
.changes {
  margin: 0;
  padding: var(--space-2) var(--space-3);
  list-style: none;
  background: var(--surface-2);
  border-radius: var(--radius-md);
  font-size: var(--text-sm);
  color: var(--text-2);
}

.progress-bar {
  height: 8px;
  border-radius: var(--radius-pill);
  background: var(--surface-3);
  overflow: hidden;
}

.progress-fill {
  height: 100%;
  background: linear-gradient(
    90deg,
    var(--accent),
    color-mix(in srgb, var(--accent), var(--surface) 45%),
    var(--accent)
  );
  background-size: 200% 100%;
  animation: shimmer 1.2s linear infinite;
  transition: width 0.3s ease;
}

.progress-bar.complete .progress-fill {
  animation: none;
  background: var(--up);
}

.progress-bar.complete.has-failures .progress-fill {
  background: var(--masa);
}

@keyframes shimmer {
  from { background-position: 200% 0; }
  to { background-position: 0 0; }
}

.progress-stats {
  display: flex;
  justify-content: space-between;
  align-items: baseline;
  flex-wrap: wrap;
  gap: var(--space-2);
  margin-top: var(--space-2);
  font-size: var(--text-sm);
}

.main-stat {
  font-weight: 700;
  color: var(--text);
}

.side-stats {
  display: flex;
  gap: var(--space-3);
  color: var(--text-muted);
}

.stat-failed {
  color: var(--down);
  font-weight: 700;
}

.item-list {
  list-style: none;
  margin: 0;
  padding: 0;
  max-height: 40vh;
  overflow-y: auto;
  border: 1px solid var(--border-subtle);
  border-radius: var(--radius-md);
}

.item-list li {
  display: grid;
  grid-template-columns: 1.5rem 1fr;
  align-items: center;
  padding: var(--space-1) var(--space-3);
  font-size: var(--text-sm);
  border-bottom: 1px solid var(--border-subtle);
}

.item-list li:last-child {
  border-bottom: none;
}

.item-icon {
  display: inline-flex;
  justify-content: center;
}

.item-icon svg {
  width: 0.875rem;
  height: 0.875rem;
}

.item-queued,
.item-skipped {
  color: var(--text-faint);
}

.item-ok .item-icon {
  color: var(--up);
}

.item-failed {
  background: var(--danger-soft);
}

.item-failed .item-icon {
  color: var(--down);
}

.item-error {
  grid-column: 2;
  font-size: var(--text-xs);
  color: var(--danger-soft-text);
}

.spinner {
  width: 0.85rem;
  height: 0.85rem;
  border: 2px solid var(--info-soft-border);
  border-top-color: var(--accent);
  border-radius: 50%;
  animation: spin 0.7s linear infinite;
}

@keyframes spin {
  to { transform: rotate(360deg); }
}

@media (prefers-reduced-motion: reduce) {
  .progress-fill,
  .spinner {
    animation: none;
  }
}
</style>
