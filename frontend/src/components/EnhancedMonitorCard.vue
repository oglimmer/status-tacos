<script setup lang="ts">
import { computed, onMounted, onUnmounted, ref } from 'vue'
import { type TimeframeType } from './TimeframeSwitcher.vue'
import { type ViewModeType } from './ViewModeSwitcher.vue'
import UptimeMetrics, { type MetricsValues } from './UptimeMetrics.vue'
import EnhancedResponseTimeChart from './EnhancedResponseTimeChart.vue'
import EnhancedStatusChart from './EnhancedStatusChart.vue'
import ResponseTimeChart from './ResponseTimeChart.vue'
import type { MonitorResponse, UptimeStats, MonitorState, PeerResult, SilentFor } from '../stores/monitors'
import { formatSilenceEnd, formatSilenceEndLong } from '../utils/silence'
import { formatPause, formatPauseInBrowserZone, isPaused, pauseOf } from '../utils/dailyPause'
import MonitorStateButton from './MonitorStateButton.vue'
import EnvironmentBadge from './EnvironmentBadge.vue'
import MonitorDetailsList from './MonitorDetailsList.vue'
import MonitorIcon from './MonitorIcon.vue'
import { formatUptime, getTimeframeLabel, uptimeLevel } from '../utils/uptime'

interface MonitorStatus {
  currentStatus: 'up' | 'down'
  lastCheckedAt: string
  lastResponseTimeMs: number
  lastStatusCode: number
  lastErrorMessage?: string | null
  peerResult?: PeerResult | null
  consecutiveFailures: number
}

interface ResponseTimeHistory {
  uptimePercentage24h?: number
  totalChecks24h?: number
  dataPoints?: Array<{ maxResponseTimeMs: number }>
  statusDownPeriods?: Array<{ start: string; end: string }>
  firstCheckAt?: string
  // Missing from older servers
  minResponseTimeMs?: number | null
  maxResponseTimeMs?: number | null
  avgResponseTimeMs?: number | null
  p99ResponseTimeMs?: number | null
}

interface EnhancedMonitorCardProps {
  monitor: MonitorResponse
  status: MonitorStatus | null
  responseTimeHistory: ResponseTimeHistory | null
  uptimeStats7d: UptimeStats | null
  uptimeStats90d: UptimeStats | null
  selectedTimeframe: TimeframeType
  selectedViewMode: ViewModeType
  isExpanded: boolean
  isLoadingStats?: boolean
  // The monitor of the heartbeat of the other Status Tacos server
  isPeer?: boolean
  // Multi edit: the card shows a checkbox
  selectable?: boolean
  selected?: boolean
}

interface EnhancedMonitorCardEmits {
  'toggle-expanded': [id: number]
  'edit': [monitor: MonitorResponse]
  'state-change': [id: number, state: MonitorState, silentFor?: SilentFor]
  'check-now': [id: number]
  'delete': [id: number]
  'toggle-select': [id: number]
}

const props = defineProps<EnhancedMonitorCardProps>()
const emit = defineEmits<EnhancedMonitorCardEmits>()

// Silent monitors show when their alerts start again
const silentEnd = computed(() =>
  props.monitor.state === 'SILENT' && props.monitor.silentUntil ? new Date(props.monitor.silentUntil) : null
)
const silentChipText = computed(() => (silentEnd.value ? `Silent until ${formatSilenceEnd(silentEnd.value)}` : null))
// Daily pause: the card shows when the monitor has no checks now
const now = ref(new Date())
let nowTimer: ReturnType<typeof setInterval> | undefined
onMounted(() => {
  nowTimer = setInterval(() => (now.value = new Date()), 30_000)
})
onUnmounted(() => clearInterval(nowTimer))
const dailyPause = computed(() => pauseOf(props.monitor))
const dailyPauseText = computed(() => {
  if (!dailyPause.value) return null
  const local = formatPauseInBrowserZone(dailyPause.value, now.value)
  return local ? `${formatPause(dailyPause.value)} (your time: ${local})` : formatPause(dailyPause.value)
})
const pausedNow = computed(
  () => props.monitor.state !== 'INACTIVE' && !!dailyPause.value && isPaused(dailyPause.value, now.value)
)
const pausedChipTitle = computed(() =>
  dailyPause.value ? `Daily pause ${dailyPauseText.value}: no checks and no alerts.` : undefined
)

const silentChipTitle = computed(() =>
  silentEnd.value ? `No alerts. Alerts start again ${formatSilenceEndLong(silentEnd.value)}.` : undefined
)

const isMinimal = computed(() => props.selectedViewMode === 'minimal')
const showDetails = computed(() => !isMinimal.value && (props.isExpanded || props.selectedViewMode === 'full'))
const isDown = computed(() => props.status?.currentStatus === 'down')

// Up is a quiet dot, down is a red fill, no status yet is grey
const statusBadge = computed(() => {
  // The class also colors the dot of the minimal view.
  if (!props.status) return { class: 'badge-neutral', text: 'No data' }
  return props.status.currentStatus === 'down'
    ? { class: 'badge-down', text: 'Down' }
    : { class: 'badge-up', text: 'Up' }
})

// What went wrong at the last check: the status code, else the error message
const problemText = computed(() => {
  const status = props.status
  if (!status || status.currentStatus !== 'down') return null
  if (status.lastStatusCode != null) {
    return status.lastErrorMessage
      ? `HTTP ${status.lastStatusCode}: ${status.lastErrorMessage}`
      : `HTTP ${status.lastStatusCode}`
  }
  return status.lastErrorMessage || 'Check failed'
})

const silentUntilText = computed(() => (silentEnd.value ? `until ${formatSilenceEndLong(silentEnd.value)}` : null))

const pausedChipText = computed(() =>
  pausedNow.value && dailyPause.value ? `Paused until ${dailyPause.value.until} ${dailyPause.value.timezone}` : null
)

// Check now: only for monitors that get checks now
const checkQueued = computed(() => !!props.monitor.checkRequestedAt)
const canCheckNow = computed(() => props.monitor.state !== 'INACTIVE' && !pausedNow.value)
const checkNowTitle = computed(() => {
  if (props.monitor.state === 'INACTIVE') return 'Inactive monitors get no checks'
  if (pausedNow.value) return 'No checks in the daily pause'
  if (checkQueued.value) return 'The check runs in the next 15 seconds'
  return 'Check in the next 15 seconds, not at the next interval'
})

const onRowClick = () => {
  if (props.selectable) emit('toggle-select', props.monitor.id)
}

// Get current stats based on selected timeframe
const currentStats = computed(() => {
  switch (props.selectedTimeframe) {
    case '7d':
      return props.uptimeStats7d
    case '90d':
      return props.uptimeStats90d
    default:
      return null
  }
})

// Numbers for the metrics box: 24h comes from the 24h history, 7d/90d from the stats
const metricsValues = computed((): MetricsValues | null => {
  if (props.selectedTimeframe !== '24h') return currentStats.value ?? null
  const history = props.responseTimeHistory
  if (!history) return null
  return {
    uptimePercentage: history.uptimePercentage24h,
    minResponseTimeMs: history.minResponseTimeMs,
    avgResponseTimeMs: history.avgResponseTimeMs,
    maxResponseTimeMs: history.maxResponseTimeMs,
    p99ResponseTimeMs: history.p99ResponseTimeMs,
    totalChecks: history.totalChecks24h ?? 0
  }
})

// Uptime of the selected timeframe, for the row
const currentUptime = computed(() =>
  props.selectedTimeframe === '24h'
    ? props.responseTimeHistory?.uptimePercentage24h
    : currentStats.value?.uptimePercentage
)

const uptimeLabel = computed(() => `uptime ${props.selectedTimeframe}`)
const uptimeClass = computed(() => `level-${uptimeLevel(currentUptime.value)}`)
// The stats of 7d and 90d load on demand
const uptimeLoading = computed(
  () => props.selectedTimeframe !== '24h' && props.isLoadingStats && currentUptime.value === undefined
)

// The data of the status strip: 24h from the history, 7d/90d from the stats
const statusStrip = computed(() => {
  if (props.selectedTimeframe === '24h') {
    const history = props.responseTimeHistory
    return {
      statusDownPeriods: get24hStatusDownPeriods(history),
      uptimePercentage: history?.uptimePercentage24h,
      firstCheckAt: history?.firstCheckAt,
      totalChecks: history?.totalChecks24h,
      timeframe: '24h' as TimeframeType
    }
  }
  const stats = currentStats.value
  if (!stats) return null
  return {
    statusDownPeriods: stats.statusDownPeriods,
    uptimePercentage: stats.uptimePercentage,
    firstCheckAt: stats.firstCheckAt,
    totalChecks: stats.totalChecks,
    timeframe: props.selectedTimeframe,
    windowStart: stats.periodStart,
    windowEnd: stats.periodEnd
  }
})

const get24hResponseTimeData = (history: ResponseTimeHistory | null): number[] => {
  if (!history || !history.dataPoints || history.dataPoints.length === 0) {
    return []
  }
  return history.dataPoints.map((point: { maxResponseTimeMs: number }) => point.maxResponseTimeMs)
}

function get24hStatusDownPeriods(history: ResponseTimeHistory | null): Array<{ start: string; end: string }> {
  if (!history || !history.statusDownPeriods || history.statusDownPeriods.length === 0) {
    return []
  }
  return history.statusDownPeriods
}
</script>


<template>
  <div
    class="monitor-item"
    :class="{
      'is-down': isDown,
      'is-minimal': isMinimal,
      'is-full': selectedViewMode === 'full',
      'is-open': showDetails,
      'is-inactive': monitor.state === 'INACTIVE',
      'is-selectable': selectable,
      'is-selected': selected
    }"
    @click="onRowClick"
  >
    <div class="mi-row">
      <div class="mi-status">
        <input
          v-if="selectable"
          type="checkbox"
          class="mi-select"
          :checked="selected"
          :aria-label="`Select ${monitor.name}`"
          @click.stop
          @change="$emit('toggle-select', monitor.id)"
        />
        <!-- Minimal: a dot, so the name has the room. The text is in the tooltip. -->
        <span
          v-if="isMinimal"
          class="mi-dot"
          :class="statusBadge.class"
          role="img"
          :aria-label="statusBadge.text"
          :title="statusBadge.text"
        ></span>
        <span v-else class="badge" :class="statusBadge.class">{{ statusBadge.text }}</span>
      </div>

      <div class="mi-main">
        <div class="mi-title">
          <button
            v-if="selectedViewMode === 'default'"
            type="button"
            class="mi-name"
            :aria-expanded="isExpanded"
            :title="isExpanded ? 'Hide details' : 'Show details'"
            @click.stop="$emit('toggle-expanded', monitor.id)"
          >{{ monitor.name }}</button>
          <span v-else class="mi-name" :title="monitor.name">{{ monitor.name }}</span>

          <span class="mi-tags">
            <EnvironmentBadge :environment="monitor.environment" />
            <span
              v-if="isPeer"
              class="badge badge-info"
              title="Watches the other Status Tacos server. The other server checks DOWN monitors before alerts."
            >peer</span>
            <span v-if="monitor.state === 'INACTIVE' && !isMinimal" class="badge badge-neutral" title="No checks and no alerts">Inactive</span>

            <!-- Minimal: only an icon, the text is in the tooltip -->
            <template v-if="isMinimal">
              <span v-if="silentChipText" class="mi-flag" :title="silentChipTitle">
                <MonitorIcon name="bell-off" /><span class="sr-only">{{ silentChipText }}</span>
              </span>
              <span v-if="pausedChipText" class="mi-flag" :title="pausedChipTitle">
                <MonitorIcon name="pause" /><span class="sr-only">{{ pausedChipText }}</span>
              </span>
            </template>
            <template v-else>
              <span v-if="silentChipText" class="badge badge-warning" :title="silentChipTitle">{{ silentChipText }}</span>
              <span v-if="pausedChipText" class="badge badge-warning" :title="pausedChipTitle">{{ pausedChipText }}</span>
            </template>
          </span>
        </div>

        <a
          v-if="!isMinimal"
          class="mi-url mono"
          :href="monitor.url"
          target="_blank"
          rel="noopener noreferrer"
          :title="monitor.url"
          @click.stop
        >{{ monitor.url }}</a>

        <p v-if="!isMinimal && problemText" class="mi-problem">
          <span class="mi-problem-text mono" :title="problemText">{{ problemText }}</span>
          <span v-if="status && status.consecutiveFailures > 0" class="num mi-problem-count">
            {{ status.consecutiveFailures }} {{ status.consecutiveFailures === 1 ? 'failure' : 'failures' }} in a row
          </span>
        </p>
      </div>

      <div class="mi-figure mi-uptime" :title="`${getTimeframeLabel(selectedTimeframe)} uptime`">
        <span class="mi-figure-value num" :class="uptimeClass">{{ uptimeLoading ? '…' : formatUptime(currentUptime) }}</span>
        <span class="mi-figure-label">{{ uptimeLabel }}</span>
      </div>

      <div v-if="!isMinimal" class="mi-figure mi-response" title="Response time of the last check">
        <span class="mi-figure-value num">{{ status ? `${status.lastResponseTimeMs} ms` : 'N/A' }}</span>
        <span class="mi-figure-label">response</span>
      </div>

      <div v-if="!isMinimal" class="mi-strip">
        <EnhancedStatusChart v-if="statusStrip" v-bind="statusStrip" compact />
        <span v-else class="mi-strip-empty">{{ isLoadingStats ? 'Loading…' : 'No data yet' }}</span>
      </div>

      <div v-if="selectedViewMode === 'default'" class="mi-toggle">
        <button
          type="button"
          class="btn btn-icon btn-sm"
          :aria-expanded="isExpanded"
          :aria-label="isExpanded ? `Hide details of ${monitor.name}` : `Show details of ${monitor.name}`"
          @click.stop="$emit('toggle-expanded', monitor.id)"
        >
          <MonitorIcon name="chevron-down" class="mi-chevron" />
        </button>
      </div>
    </div>

    <!-- Details: settings, numbers and charts of the timeframe, then the actions -->
    <div v-if="showDetails" class="mi-details" @click.stop>
      <MonitorDetailsList
        :monitor="monitor"
        :status="status"
        :silent-until-text="silentUntilText"
        :daily-pause-text="dailyPauseText"
      />

      <UptimeMetrics
        :stats="metricsValues"
        :timeframe="selectedTimeframe"
        :is-loading="selectedTimeframe !== '24h' && isLoadingStats"
      />

      <div class="mi-charts">
        <ResponseTimeChart
          v-if="selectedTimeframe === '24h'"
          :data="get24hResponseTimeData(responseTimeHistory)"
        />
        <EnhancedResponseTimeChart
          v-else-if="currentStats"
          :data="currentStats.responseTimeDataPoints"
          :timeframe="selectedTimeframe"
          :window-start="currentStats.periodStart"
          :window-end="currentStats.periodEnd"
        />
        <EnhancedStatusChart v-if="statusStrip" v-bind="statusStrip" />
      </div>

      <div class="mi-actions">
        <button type="button" class="btn btn-sm btn-primary" @click="$emit('edit', monitor)">Edit</button>
        <MonitorStateButton
          :state="monitor.state"
          :silent-until="monitor.silentUntil"
          @state-change="(newState, silentFor) => $emit('state-change', monitor.id, newState, silentFor)"
        />
        <button
          type="button"
          class="btn btn-sm btn-outline"
          :disabled="!canCheckNow || checkQueued"
          :title="checkNowTitle"
          @click="$emit('check-now', monitor.id)"
        >
          <MonitorIcon name="refresh" />
          {{ checkQueued ? 'Check queued' : 'Check now' }}
        </button>
        <button type="button" class="btn btn-sm btn-outline mi-delete" @click="$emit('delete', monitor.id)">Delete</button>
      </div>
    </div>
  </div>
</template>

<style scoped>
/*
 * One monitor is one row. All rows use the same columns, so the eye runs down the status,
 * the uptime and the strip. The columns come from the list (--mi-columns); the defaults
 * here are for a monitor shown alone.
 */
.monitor-item {
  --mi-status-width: 4.25rem;
  background: var(--surface);
  min-width: 0;
}

.monitor-item.is-selectable {
  --mi-status-width: 6rem;
  cursor: pointer;
  user-select: none;
}

/* While the user selects, a click anywhere on the row selects it. */
.monitor-item.is-selectable :deep(a),
.monitor-item.is-selectable :deep(button) {
  pointer-events: none;
}

.monitor-item.is-selected {
  background: var(--accent-soft);
}

/* The response column fits "12345 ms". */
.mi-row {
  display: grid;
  grid-template-columns:
    var(--mi-status-width) minmax(0, 1fr) 5.5rem 5.25rem
    var(--mi-strip-width, clamp(8rem, 20vw, 15rem)) 2rem;
  grid-template-areas: 'status main uptime response strip toggle';
  align-items: center;
  column-gap: var(--space-4);
  min-height: 3.25rem;
  padding: var(--space-2) var(--space-3) var(--space-2) var(--space-4);
}

.is-full .mi-row {
  grid-template-columns:
    var(--mi-status-width) minmax(0, 1fr) 5.5rem 5.25rem
    var(--mi-strip-width, clamp(8rem, 20vw, 15rem));
  grid-template-areas: 'status main uptime response strip';
}

.is-minimal .mi-row {
  grid-template-columns: auto minmax(0, 1fr) auto 4.5rem;
  grid-template-areas: 'status name tags uptime';
  column-gap: var(--space-3);
  min-height: 2.5rem;
  padding: var(--space-1) var(--space-3);
}

/*
 * Minimal: the name and the tags are columns of the row, not one line. So the tags of all
 * rows line up next to the uptime, and they are never cut; the name gets the rest.
 */
.is-minimal .mi-main,
.is-minimal .mi-title {
  display: contents;
}

.is-minimal .mi-name {
  grid-area: name;
}

.is-minimal .mi-tags {
  grid-area: tags;
  justify-self: end;
  flex-wrap: nowrap;
}

/* The status as a dot. Up is a quiet green; down is red with a ring; no data is hollow. */
.mi-dot {
  width: 0.625rem;
  height: 0.625rem;
  flex: none;
  border-radius: 50%;
  background: var(--up);
}

.mi-dot.badge-down {
  background: var(--down);
  box-shadow: 0 0 0 3px color-mix(in srgb, var(--down) 30%, transparent);
}

.mi-dot.badge-neutral {
  background: transparent;
  box-shadow: inset 0 0 0 2px var(--text-faint);
}

/* A monitor that is down has a red edge. */
.is-down {
  box-shadow: inset 3px 0 0 var(--down);
}

.mi-status {
  grid-area: status;
  display: flex;
  align-items: center;
  gap: var(--space-2);
}

.mi-select {
  width: 1rem;
  height: 1rem;
  margin: 0;
  accent-color: var(--accent);
  cursor: pointer;
}

.mi-main {
  grid-area: main;
  display: flex;
  flex-direction: column;
  gap: 0.125rem;
  min-width: 0;
}

.mi-title {
  display: flex;
  align-items: center;
  flex-wrap: wrap;
  gap: var(--space-1) var(--space-2);
  min-width: 0;
}

.mi-name {
  min-width: 0;
  max-width: 100%;
  padding: 0;
  border: 0;
  background: none;
  color: var(--text);
  font: inherit;
  font-weight: 700;
  text-align: left;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

button.mi-name {
  cursor: pointer;
}

button.mi-name:hover {
  color: var(--link);
}

.is-inactive .mi-name {
  color: var(--text-muted);
}

.mi-tags {
  display: inline-flex;
  flex-wrap: wrap;
  align-items: center;
  gap: var(--space-1);
}

.mi-flag {
  display: inline-flex;
  color: var(--warn);
}

.mi-url {
  display: block;
  max-width: 100%;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  font-size: var(--text-sm);
  color: var(--text-muted);
  text-decoration: none;
}

.mi-url:hover {
  color: var(--link);
  text-decoration: underline;
}

/* The reason of the outage, readable without a click. */
.mi-problem {
  display: flex;
  flex-wrap: wrap;
  gap: 0 var(--space-3);
  margin: 0;
  font-size: var(--text-sm);
  color: var(--down);
}

.mi-problem-text {
  min-width: 0;
  max-width: 100%;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  font-weight: 700;
}

.mi-problem-count {
  white-space: nowrap;
}

/* A number with a small label below. */
.mi-figure {
  display: flex;
  flex-direction: column;
  align-items: flex-end;
  min-width: 0;
  line-height: var(--leading-tight);
}

.mi-uptime {
  grid-area: uptime;
}

.mi-response {
  grid-area: response;
}

.mi-figure-value {
  font-size: var(--text-md);
  color: var(--text);
  white-space: nowrap;
}

.mi-figure-label {
  font-size: var(--text-xs);
  color: var(--text-muted);
  white-space: nowrap;
}

.is-minimal .mi-figure-label {
  display: none;
}

.level-warning {
  color: var(--warn);
}

.level-poor {
  color: var(--down);
  font-weight: 700;
}

.level-no-data {
  color: var(--text-muted);
}

.mi-strip {
  grid-area: strip;
  min-width: 0;
}

.mi-strip-empty {
  display: block;
  padding: 0.125rem var(--space-2);
  border: 1px dashed var(--border);
  border-radius: var(--radius-sm);
  font-size: var(--text-xs);
  color: var(--text-muted);
  text-align: center;
}

.mi-toggle {
  grid-area: toggle;
  display: flex;
  justify-content: flex-end;
}

.mi-chevron {
  transition: transform 0.15s;
}

.is-open .mi-chevron {
  transform: rotate(180deg);
}

/* The open part sits on a grey ground, under the row, in the width of the name column. */
.mi-details {
  display: flex;
  flex-direction: column;
  gap: var(--space-4);
  padding: var(--space-4) var(--space-4) var(--space-4) calc(var(--space-4) + var(--mi-status-width) + var(--space-4));
  border-top: 1px dashed var(--border-subtle);
  background: var(--surface-2);
  cursor: auto;
  user-select: text;
}

.mi-charts {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: var(--space-4);
}

.mi-actions {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: var(--space-2);
}

/* Delete is the last action and red only on hover: it should not catch the eye. */
.mi-delete {
  margin-left: auto;
}

.mi-delete:hover:not(:disabled) {
  border-color: var(--down);
  color: var(--down);
}

/* Tablets: the strip moves below the name. */
@media (max-width: 1000px) {
  .mi-row,
  .is-full .mi-row {
    grid-template-columns: var(--mi-status-width) minmax(0, 1fr) 5.5rem 5.25rem 2rem;
    grid-template-areas:
      'status main uptime response toggle'
      '.      strip strip strip strip';
    row-gap: var(--space-2);
  }

  .is-full .mi-row {
    grid-template-columns: var(--mi-status-width) minmax(0, 1fr) 5.5rem 5.25rem;
    grid-template-areas:
      'status main uptime response'
      '.      strip strip strip';
  }
}

/* Phones: the name gets the full width; status and numbers in one line below, then the strip. */
@media (max-width: 640px) {
  .mi-row,
  .is-full .mi-row {
    grid-template-columns: auto auto minmax(0, 1fr) 2rem;
    grid-template-areas:
      'main   main   main     toggle'
      'status uptime response .'
      'strip  strip  strip    strip';
    column-gap: var(--space-4);
    row-gap: var(--space-1);
    padding-inline: var(--space-3);
  }

  .is-full .mi-row {
    grid-template-columns: auto auto minmax(0, 1fr);
    grid-template-areas:
      'main   main   main'
      'status uptime response'
      'strip  strip  strip';
  }

  .mi-strip {
    margin-top: var(--space-1);
  }

  .mi-tags .badge {
    white-space: normal;
  }

  .mi-row .mi-figure {
    flex-direction: row;
    align-items: baseline;
    gap: var(--space-1);
  }

  .is-minimal .mi-row {
    grid-template-columns: auto minmax(0, 1fr) auto 4.5rem;
    grid-template-areas: 'status name tags uptime';
    column-gap: var(--space-3);
    row-gap: 0;
  }

  .is-minimal .mi-tags .badge {
    white-space: nowrap;
  }

  .mi-details {
    padding: var(--space-3);
  }

  .mi-charts {
    grid-template-columns: minmax(0, 1fr);
  }
}
</style>
