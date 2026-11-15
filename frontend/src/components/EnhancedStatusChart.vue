<script setup lang="ts">
import { computed, nextTick, onBeforeUnmount, onMounted, ref, useId, watch } from 'vue'
import MonitorIcon from './MonitorIcon.vue'
import type { TimeframeType } from './TimeframeSwitcher.vue'
import { checkedFromMs, downFractionColumns, formatUptime, getTimeframeLabel, parseUtc, timeWindow, uptimeLevel } from '../utils/uptime'

interface StatusDownPeriod {
  start: string
  end: string
}

interface StatusChartProps {
  statusDownPeriods: StatusDownPeriod[]
  timeframe: TimeframeType
  // Uptime from the backend. Missing when there are no checks.
  uptimePercentage?: number
  // Window of the data (UTC, from the backend). Default: the timeframe until now.
  windowStart?: string
  windowEnd?: string
  // First check and number of checks in the window: the time before the first check has no data.
  firstCheckAt?: string
  totalChecks?: number
  title?: string
  // Only the strip, without title and time labels: for a row of the monitor list
  compact?: boolean
}

const props = defineProps<StatusChartProps>()

const showModal = ref(false)
const hasLockedBody = ref(false)

let openModalCount = 0
let previousBodyOverflow: string | null = null

const lockBodyScroll = () => {
  if (typeof window === 'undefined') return

  if (openModalCount === 0) {
    previousBodyOverflow = document.body.style.overflow || ''
    document.body.style.overflow = 'hidden'
  }

  openModalCount += 1
}

const unlockBodyScroll = () => {
  if (typeof window === 'undefined') return
  if (openModalCount === 0) return

  openModalCount -= 1

  if (openModalCount === 0) {
    document.body.style.overflow = previousBodyOverflow ?? ''
    previousBodyOverflow = null
  }
}

const titleId = useId()
const closeButton = ref<HTMLButtonElement | null>(null)
const stripButton = ref<HTMLButtonElement | null>(null)

const openModal = async () => {
  showModal.value = true
  await nextTick()
  closeButton.value?.focus()
}

const closeModal = () => {
  showModal.value = false
  stripButton.value?.focus()
}

watch(showModal, isOpen => {
  if (typeof window === 'undefined') return

  if (isOpen && !hasLockedBody.value) {
    lockBodyScroll()
    hasLockedBody.value = true
  } else if (!isOpen && hasLockedBody.value) {
    unlockBodyScroll()
    hasLockedBody.value = false
  }
})

onBeforeUnmount(() => {
  if (hasLockedBody.value) {
    unlockBodyScroll()
    hasLockedBody.value = false
  }
})

// Real width of the chart in CSS pixels. Each pixel column shows one time slot.
const svgEl = ref<SVGSVGElement | null>(null)
const chartWidth = ref(300)
let resizeObserver: ResizeObserver | null = null

onMounted(() => {
  if (!svgEl.value) return
  chartWidth.value = Math.max(1, Math.round(svgEl.value.clientWidth)) || 300
  if (typeof ResizeObserver === 'undefined') return
  resizeObserver = new ResizeObserver(entries => {
    const width = Math.round(entries[0]?.contentRect.width ?? 0)
    if (width > 0) chartWidth.value = width
  })
  resizeObserver.observe(svgEl.value)
})

onBeforeUnmount(() => resizeObserver?.disconnect())

// "Now" of a window without backend bounds. Date.now() is not reactive, so the window moves
// only when new data arrives. Else a page left open for hours keeps its old window.
const dataLoadedAt = ref(Date.now())
watch(
  () => [props.statusDownPeriods, props.firstCheckAt, props.totalChecks],
  () => { dataLoadedAt.value = Date.now() }
)

const chartWindow = computed(() =>
  timeWindow(props.timeframe, props.windowStart, props.windowEnd, dataLoadedAt.value)
)

const checkedFrom = computed(() =>
  checkedFromMs(props.firstCheckAt, props.totalChecks, chartWindow.value)
)

// Width in pixels of the time before the first check (no data)
const uncheckedWidth = computed(() => {
  const { startMs, endMs } = chartWindow.value
  if (checkedFrom.value === null) return chartWidth.value
  return Math.round(((checkedFrom.value - startMs) / (endMs - startMs)) * chartWidth.value)
})

// Down share per pixel column: light yellow = a short part of the slot, dark red = the full slot
const downColumns = computed(() =>
  downFractionColumns(
    props.statusDownPeriods,
    chartWindow.value,
    chartWidth.value,
    checkedFrom.value ?? chartWindow.value.endMs
  )
)

const slotDuration = computed(() => {
  const { startMs, endMs } = chartWindow.value
  return (endMs - startMs) / chartWidth.value
})

// Labels at 0%, 25%, 50%, 75% and 100% (now) of the window
const timeLabels = computed((): string[] => {
  const { startMs, endMs } = chartWindow.value
  return [0, 0.25, 0.5, 0.75, 1].map(position => {
    if (position === 1) return 'now'
    const time = new Date(startMs + position * (endMs - startMs))
    switch (props.timeframe) {
      case '24h':
        return time.toLocaleTimeString([], { hour: '2-digit', minute: '2-digit' })
      case '7d':
        return time.toLocaleDateString([], { weekday: 'short' })
      case '90d':
        return time.toLocaleDateString([], { month: 'short', day: 'numeric' })
    }
  })
})

// The more of a slot was down, the stronger the red
const downOpacity = (fraction: number): number => Math.round((0.35 + 0.65 * fraction) * 100) / 100

const stripLabel = computed(
  () =>
    `Status of the ${getTimeframeLabel(props.timeframe)}: ${props.statusDownPeriods.length} downtime periods, ${formatUptime(props.uptimePercentage)} uptime. Show the downtime periods.`
)

// Format downtime periods for display. The backend sends them sorted by start.
const formatDowntimePeriods = (statusDownPeriods: StatusDownPeriod[]): { start: string, end: string, duration: string }[] => {
  return statusDownPeriods
    .map(period => {
      const startDate = parseUtc(period.start)
      const endDate = parseUtc(period.end)

      return {
        start: startDate.toLocaleString([], {
          year: 'numeric',
          month: '2-digit',
          day: '2-digit',
          hour: '2-digit',
          minute: '2-digit',
          second: '2-digit',
          timeZoneName: 'short'
        }),
        end: endDate.toLocaleString([], {
          year: 'numeric',
          month: '2-digit',
          day: '2-digit',
          hour: '2-digit',
          minute: '2-digit',
          second: '2-digit',
          timeZoneName: 'short'
        }),
        duration: formatDuration(endDate.getTime() - startDate.getTime())
      }
    })
}

// Format duration in human readable format
const formatDuration = (ms: number): string => {
  const seconds = Math.floor(ms / 1000)
  const minutes = Math.floor(seconds / 60)
  const hours = Math.floor(minutes / 60)
  const days = Math.floor(hours / 24)

  if (days > 0) return `${days}d ${hours % 24}h ${minutes % 60}m`
  if (hours > 0) return `${hours}h ${minutes % 60}m`
  if (minutes > 0) return `${minutes}m ${seconds % 60}s`
  return `${seconds}s`
}
</script>

<template>
  <div class="status-chart" :class="{ compact }">
    <div v-if="!compact" class="sc-head">
      <span class="sc-title">{{ title || `Status (${getTimeframeLabel(timeframe)})` }}</span>
      <span class="sc-uptime num" :class="`level-${uptimeLevel(uptimePercentage)}`">
        {{ formatUptime(uptimePercentage) }} uptime
      </span>
    </div>

    <!-- A click on the strip shows the list of downtime periods -->
    <button
      ref="stripButton"
      type="button"
      class="sc-strip"
      :aria-label="stripLabel"
      :title="compact ? stripLabel : undefined"
      @click.stop="openModal"
    >
      <svg ref="svgEl" class="sc-svg" :viewBox="`0 0 ${chartWidth} 30`" preserveAspectRatio="none" shape-rendering="crispEdges">
        <!-- Before the first check: no data -->
        <rect v-if="uncheckedWidth > 0" class="sc-no-data" x="0" y="0" :width="uncheckedWidth" height="30">
          <title>No checks</title>
        </rect>
        <!-- One column per pixel, the share of its time slot that was down sets the strength of the red -->
        <rect
          v-for="column in downColumns"
          :key="column.x"
          class="sc-down"
          :x="column.x"
          :y="0"
          :width="column.width"
          :height="30"
          :fill-opacity="downOpacity(column.fraction)"
        >
          <title>{{ Math.round(column.fraction * 1000) / 10 }}% down (≈{{ formatDuration(column.fraction * slotDuration) }} of {{ formatDuration(slotDuration) }})</title>
        </rect>
      </svg>
    </button>

    <div v-if="!compact" class="sc-axis" aria-hidden="true">
      <span
        v-for="(label, index) in timeLabels"
        :key="index"
        :style="{ left: `${(index / (timeLabels.length - 1)) * 100}%` }"
      >{{ label }}</span>
    </div>

    <Teleport to="body">
      <div v-if="showModal" class="sc-overlay" @click="closeModal" @keydown.esc="closeModal">
        <div class="sc-dialog" role="dialog" aria-modal="true" :aria-labelledby="titleId" @click.stop>
          <div class="sc-dialog-head">
            <h3 :id="titleId" class="sc-dialog-title">Downtime periods ({{ getTimeframeLabel(timeframe) }})</h3>
            <button ref="closeButton" type="button" class="btn btn-icon btn-sm" aria-label="Close" @click="closeModal">
              <MonitorIcon name="close" />
            </button>
          </div>
          <div class="sc-dialog-body">
            <p v-if="statusDownPeriods.length === 0" class="sc-none">No downtime in this timeframe.</p>
            <div v-else class="sc-table-wrap">
              <table class="sc-table">
                <thead>
                  <tr>
                    <th scope="col">Start</th>
                    <th scope="col">End</th>
                    <th scope="col">Duration</th>
                  </tr>
                </thead>
                <tbody>
                  <tr v-for="(period, index) in formatDowntimePeriods(statusDownPeriods)" :key="index">
                    <td class="num">{{ period.start }}</td>
                    <td class="num">{{ period.end }}</td>
                    <td class="num sc-duration">{{ period.duration }}</td>
                  </tr>
                </tbody>
              </table>
            </div>
          </div>
        </div>
      </div>
    </Teleport>
  </div>
</template>

<style scoped>
.status-chart {
  min-width: 0;
}

.sc-head {
  display: flex;
  align-items: baseline;
  justify-content: space-between;
  gap: var(--space-2);
  margin-bottom: var(--space-1);
  font-size: var(--text-xs);
}

.sc-title {
  font-weight: 700;
  color: var(--text-2);
}

.sc-uptime {
  color: var(--text-2);
}

.sc-uptime.level-warning {
  color: var(--warn);
}

.sc-uptime.level-poor {
  color: var(--down);
  font-weight: 700;
}

.sc-uptime.level-no-data {
  color: var(--text-muted);
}

/* The strip: grey is checked and up, red is down, empty is no checks. */
.sc-strip {
  display: block;
  width: 100%;
  padding: 0;
  border: 1px solid var(--border-subtle);
  border-radius: var(--radius-sm);
  background: var(--surface-3);
  overflow: hidden;
  cursor: pointer;
}

.sc-strip:hover {
  border-color: var(--input-border);
}

.sc-svg {
  display: block;
  width: 100%;
  height: 1.5rem;
}

.compact .sc-svg {
  height: 1.125rem;
}

.sc-no-data {
  fill: var(--surface);
}

.sc-down {
  fill: var(--down);
}

.sc-axis {
  position: relative;
  height: 1rem;
  margin-top: 0.125rem;
}

.sc-axis span {
  position: absolute;
  font-family: var(--font-mono);
  font-size: 0.75rem; /* 12px: small axis labels, still readable */
  color: var(--text-muted);
  white-space: nowrap;
  transform: translateX(-50%);
}

.sc-axis span:first-child {
  transform: none;
}

.sc-axis span:last-child {
  transform: translateX(-100%);
}

/* The list of downtime periods */
.sc-overlay {
  position: fixed;
  inset: 0;
  z-index: 1000;
  display: flex;
  align-items: flex-start;
  justify-content: center;
  padding: var(--space-5) var(--space-4);
  background: var(--overlay);
  overflow-y: auto;
}

.sc-dialog {
  display: flex;
  flex-direction: column;
  width: min(40rem, 100%);
  max-height: calc(100dvh - 3rem);
  border: 1px solid var(--border);
  border-radius: var(--radius-lg);
  background: var(--surface);
  box-shadow: var(--shadow-2);
  overflow: hidden;
}

.sc-dialog-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: var(--space-3);
  padding: var(--space-3) var(--space-4);
  border-bottom: 1px solid var(--border-subtle);
}

.sc-dialog-title {
  margin: 0;
  font-size: var(--text-lg);
}

.sc-dialog-body {
  padding: var(--space-3) var(--space-4) var(--space-4);
  overflow-y: auto;
}

.sc-none {
  margin: var(--space-4) 0;
  color: var(--text-muted);
  text-align: center;
}

.sc-table-wrap {
  overflow-x: auto;
}

.sc-table {
  width: 100%;
  border-collapse: collapse;
  font-size: var(--text-sm);
}

.sc-table th,
.sc-table td {
  padding: var(--space-2);
  border-bottom: 1px solid var(--border-subtle);
  text-align: left;
  white-space: nowrap;
}

.sc-table th {
  position: sticky;
  top: 0;
  background: var(--surface);
  color: var(--text-2);
  font-weight: 700;
}

.sc-duration {
  color: var(--down);
  font-weight: 700;
}

@media (max-width: 520px) {
  .sc-axis span:nth-child(2),
  .sc-axis span:nth-child(4) {
    display: none;
  }
}
</style>
