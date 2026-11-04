<script setup lang="ts">
import { computed, onUnmounted, reactive, ref } from 'vue'
import FormModal from './FormModal.vue'
import { useMonitorsStore, type MonitorResponse, type MonitorState, type SilentFor } from '../stores/monitors'
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
  showOnPublicPage: false
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
  showOnPublicPage: first?.showOnPublicPage ?? false
})

const FIELD_LABELS: Record<BulkEditField, string> = {
  state: 'State',
  checkInterval: 'Check interval',
  connectTimeout: 'Connect timeout',
  alertingThreshold: 'Alerting threshold',
  statusCodeRegex: 'Status code regex',
  useSystemDns: 'DNS server of the backend',
  showOnPublicPage: 'Show on public status page'
}

const formatValue = (field: BulkEditField, value: unknown): string => {
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
  <FormModal :title="title" @close="handleClose">
    <!-- Step 1: choose the settings -->
    <form v-if="phase === 'edit'" class="bulk-form" @submit.prevent="start">
      <p class="intro">
        Tick the settings to change. All other settings of each monitor stay as they are.
      </p>

      <div class="field" :class="{ on: enabled.state }">
        <label class="field-toggle">
          <input v-model="enabled.state" type="checkbox" />
          <span class="field-name">{{ FIELD_LABELS.state }}</span>
        </label>
        <div class="current">{{ currentSummary('state') }}</div>
        <div v-if="enabled.state" class="segmented" role="radiogroup">
          <label v-for="s in (['ACTIVE', 'SILENT', 'INACTIVE'] as const)" :key="s" :class="{ selected: values.state === s }">
            <input v-model="values.state" type="radio" :value="s" />
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

      <div class="field" :class="{ on: enabled.checkInterval }">
        <label class="field-toggle">
          <input v-model="enabled.checkInterval" type="checkbox" />
          <span class="field-name">{{ FIELD_LABELS.checkInterval }}</span>
        </label>
        <div class="current">{{ currentSummary('checkInterval') }}</div>
        <div v-if="enabled.checkInterval" class="input-row">
          <input v-model.number="values.checkInterval" type="number" min="15" max="3600" step="15" />
          <span class="unit">seconds · multiple of 15, 15 to 3600</span>
        </div>
      </div>

      <div class="field" :class="{ on: enabled.connectTimeout }">
        <label class="field-toggle">
          <input v-model="enabled.connectTimeout" type="checkbox" />
          <span class="field-name">{{ FIELD_LABELS.connectTimeout }}</span>
        </label>
        <div class="current">{{ currentSummary('connectTimeout') }}</div>
        <div v-if="enabled.connectTimeout" class="input-row">
          <input v-model.number="values.connectTimeout" type="number" min="1" max="10" step="1" />
          <span class="unit">seconds · 1 to 10</span>
        </div>
      </div>

      <div class="field" :class="{ on: enabled.alertingThreshold }">
        <label class="field-toggle">
          <input v-model="enabled.alertingThreshold" type="checkbox" />
          <span class="field-name">{{ FIELD_LABELS.alertingThreshold }}</span>
        </label>
        <div class="current">{{ currentSummary('alertingThreshold') }}</div>
        <div v-if="enabled.alertingThreshold" class="input-row">
          <input v-model.number="values.alertingThreshold" type="number" min="15" step="15" />
          <span class="unit">seconds down before the alert · multiple of 15</span>
        </div>
      </div>

      <div class="field" :class="{ on: enabled.statusCodeRegex }">
        <label class="field-toggle">
          <input v-model="enabled.statusCodeRegex" type="checkbox" />
          <span class="field-name">{{ FIELD_LABELS.statusCodeRegex }}</span>
        </label>
        <div class="current">{{ currentSummary('statusCodeRegex') }}</div>
        <div v-if="enabled.statusCodeRegex" class="input-row">
          <input v-model="values.statusCodeRegex" type="text" class="mono" placeholder="^[23]\d{2}$" />
        </div>
      </div>

      <div class="field" :class="{ on: enabled.useSystemDns }">
        <label class="field-toggle">
          <input v-model="enabled.useSystemDns" type="checkbox" />
          <span class="field-name">{{ FIELD_LABELS.useSystemDns }}</span>
        </label>
        <div class="current">{{ currentSummary('useSystemDns') }}</div>
        <div v-if="enabled.useSystemDns" class="segmented">
          <label :class="{ selected: !values.useSystemDns }">
            <input v-model="values.useSystemDns" type="radio" :value="false" /> Off (ask the domain's nameservers)
          </label>
          <label :class="{ selected: values.useSystemDns }">
            <input v-model="values.useSystemDns" type="radio" :value="true" /> On
          </label>
        </div>
      </div>

      <div class="field" :class="{ on: enabled.showOnPublicPage }">
        <label class="field-toggle">
          <input v-model="enabled.showOnPublicPage" type="checkbox" />
          <span class="field-name">{{ FIELD_LABELS.showOnPublicPage }}</span>
        </label>
        <div class="current">{{ currentSummary('showOnPublicPage') }}</div>
        <div v-if="enabled.showOnPublicPage" class="segmented">
          <label :class="{ selected: values.showOnPublicPage }">
            <input v-model="values.showOnPublicPage" type="radio" :value="true" /> Show
          </label>
          <label :class="{ selected: !values.showOnPublicPage }">
            <input v-model="values.showOnPublicPage" type="radio" :value="false" /> Hide
          </label>
        </div>
      </div>

      <div v-if="formError" class="error-message">{{ formError }}</div>

      <div class="form-actions">
        <button type="button" class="btn btn-secondary" @click="emit('close')">Cancel</button>
        <button type="submit" class="btn btn-primary" :disabled="enabledFields.length === 0">
          Update {{ monitors.length }} {{ monitors.length === 1 ? 'monitor' : 'monitors' }}
        </button>
      </div>
    </form>

    <!-- Step 2 and 3: progress and result -->
    <div v-else class="progress-view" aria-live="polite">
      <ul class="changes">
        <li v-for="field in enabledFields" :key="field">
          {{ FIELD_LABELS[field] }} → <strong>{{ formatValue(field, values[field]) }}</strong>
        </li>
      </ul>

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
            {{ finishedCount }} of {{ total }} done
          </template>
          <template v-else-if="failedCount === 0 && skippedCount === 0">
            ✓ All {{ total }} updated
          </template>
          <template v-else>
            {{ okCount }} of {{ total }} updated
          </template>
        </span>
        <span class="side-stats">
          <span v-if="failedCount" class="stat-failed">{{ failedCount }} failed</span>
          <span v-if="skippedCount" class="stat-skipped">{{ skippedCount }} not started</span>
          <span class="stat-time">
            {{ elapsedSeconds }}s<template v-if="remainingSeconds !== null"> · about {{ remainingSeconds }}s left</template>
          </span>
        </span>
      </div>

      <ul class="item-list">
        <li v-for="item in sortedItems" :key="item.monitor.id" :class="`item-${item.status}`">
          <span class="item-icon" :aria-label="item.status">
            <span v-if="item.status === 'running'" class="spinner" />
            <template v-else-if="item.status === 'ok'">✓</template>
            <template v-else-if="item.status === 'failed'">✕</template>
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
.silence-choice {
  margin-top: 0.75rem;
  max-width: 24rem;
}

.silence-choice .unit {
  display: block;
  margin-bottom: 0.25rem;
}

.intro {
  margin: 0 0 1rem;
  color: var(--text-muted);
  font-size: 0.9rem;
}

.field {
  border: 1px solid var(--border-subtle);
  border-radius: 6px;
  padding: 0.6rem 0.75rem;
  margin-bottom: 0.5rem;
  transition: border-color 0.15s, background 0.15s;
}

.field.on {
  border-color: var(--accent);
  background: var(--surface-2);
}

.field-toggle {
  display: flex;
  align-items: center;
  gap: 0.5rem;
  cursor: pointer;
}

.field-name {
  font-weight: 500;
  color: var(--text);
}

.current {
  margin: 0.15rem 0 0 1.6rem;
  font-size: 0.8rem;
  color: var(--text-muted);
  overflow-wrap: anywhere;
}

.input-row,
.segmented {
  margin: 0.5rem 0 0 1.6rem;
}

.input-row {
  display: flex;
  align-items: center;
  gap: 0.5rem;
  flex-wrap: wrap;
}

.input-row input {
  padding: 0.45rem 0.6rem;
  border: 1px solid var(--input-border);
  border-radius: 4px;
  font-size: 0.95rem;
}

.input-row input[type='number'] {
  width: 7rem;
}

.input-row input[type='text'] {
  flex: 1;
  min-width: 10rem;
}

.mono {
  font-family: ui-monospace, SFMono-Regular, Menlo, monospace;
}

.unit {
  font-size: 0.8rem;
  color: var(--text-muted);
}

.segmented {
  display: flex;
  flex-wrap: wrap;
  gap: 0.4rem;
}

.segmented label {
  border: 1px solid var(--input-border);
  border-radius: 4px;
  padding: 0.35rem 0.6rem;
  font-size: 0.85rem;
  cursor: pointer;
  background: var(--surface);
}

.segmented label.selected {
  border-color: var(--accent);
  background: var(--accent);
  color: white;
}

.segmented input {
  display: none;
}

.error-message {
  background: var(--danger-soft);
  color: var(--danger-soft-text);
  padding: 0.75rem;
  border-radius: 4px;
  margin-top: 0.75rem;
  border: 1px solid var(--danger-soft-border);
}

.form-actions {
  display: flex;
  gap: 1rem;
  justify-content: flex-end;
  margin-top: 1.5rem;
}

.btn {
  padding: 0.65rem 1.25rem;
  border: none;
  border-radius: 4px;
  cursor: pointer;
  font-size: 0.95rem;
  font-weight: 500;
}

.btn:disabled {
  opacity: 0.6;
  cursor: not-allowed;
}

.btn-secondary {
  background: var(--secondary);
  color: white;
}

.btn-primary {
  background: var(--accent);
  color: white;
}

/* Progress */
.changes {
  margin: 0 0 1rem;
  padding: 0.5rem 0.75rem;
  list-style: none;
  background: var(--surface-2);
  border-radius: 6px;
  font-size: 0.85rem;
  color: var(--text-2);
}

.progress-bar {
  height: 10px;
  border-radius: 5px;
  background: var(--surface-3);
  overflow: hidden;
}

.progress-fill {
  height: 100%;
  background: linear-gradient(90deg, var(--accent), #3d9bff, var(--accent));
  background-size: 200% 100%;
  animation: shimmer 1.2s linear infinite;
  transition: width 0.3s ease;
}

.progress-bar.complete .progress-fill {
  animation: none;
  background: var(--success);
}

.progress-bar.complete.has-failures .progress-fill {
  background: #fd7e14;
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
  gap: 0.5rem;
  margin: 0.5rem 0 1rem;
  font-size: 0.9rem;
}

.main-stat {
  font-weight: 600;
  color: var(--text);
}

.side-stats {
  display: flex;
  gap: 0.75rem;
  color: var(--text-muted);
  font-size: 0.85rem;
}

.stat-failed {
  color: var(--danger-text);
  font-weight: 600;
}

.stat-time {
  font-variant-numeric: tabular-nums;
}

.item-list {
  list-style: none;
  margin: 0;
  padding: 0;
  max-height: 40vh;
  overflow-y: auto;
  border: 1px solid var(--border-subtle);
  border-radius: 6px;
}

.item-list li {
  display: grid;
  grid-template-columns: 1.5rem 1fr;
  align-items: center;
  padding: 0.35rem 0.6rem;
  font-size: 0.875rem;
  border-bottom: 1px solid var(--border-subtle);
}

.item-list li:last-child {
  border-bottom: none;
}

.item-icon {
  display: inline-flex;
  justify-content: center;
}

.item-queued,
.item-skipped {
  color: var(--text-faint);
}

.item-ok .item-icon {
  color: var(--success-text);
}

.item-failed {
  background: var(--danger-soft);
}

.item-failed .item-icon {
  color: var(--danger-text);
  font-weight: 700;
}

.item-error {
  grid-column: 2;
  font-size: 0.8rem;
  color: var(--danger-text);
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
