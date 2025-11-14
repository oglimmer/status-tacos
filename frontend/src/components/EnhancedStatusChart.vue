<script setup lang="ts">
import { onBeforeUnmount, ref, watch } from 'vue'
import type { TimeframeType } from './TimeframeSwitcher.vue'

interface StatusDownPeriod {
  start: string
  end: string
}

interface StatusChartProps {
  statusDownPeriods: StatusDownPeriod[]
  timeframe: TimeframeType
  title?: string
}

defineProps<StatusChartProps>()

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

const openModal = () => {
  showModal.value = true
}

const closeModal = () => {
  showModal.value = false
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

// Generate status line data (1 for up, 0 for down)
// eslint-disable-next-line @typescript-eslint/no-unused-vars
const generateStatusLineData = (statusDownPeriods: StatusDownPeriod[], timeframe: TimeframeType): string => {
  const chartWidth = 300
  const chartHeight = 30
  let totalDuration: number
  const now = new Date()

  // Calculate total duration based on timeframe
  switch (timeframe) {
    case '24h':
      totalDuration = 24 * 60 * 60 * 1000 // 24 hours
      break
    case '7d':
      totalDuration = 7 * 24 * 60 * 60 * 1000 // 7 days
      break
    case '90d':
      totalDuration = 90 * 24 * 60 * 60 * 1000 // 90 days
      break
    case '365d':
      totalDuration = 365 * 24 * 60 * 60 * 1000 // 365 days
      break
    default:
      totalDuration = 24 * 60 * 60 * 1000
  }

  // Use UTC time for consistent comparison with backend data
  const startTime = new Date(Date.UTC(
    now.getUTCFullYear(),
    now.getUTCMonth(),
    now.getUTCDate(),
    now.getUTCHours(),
    now.getUTCMinutes(),
    now.getUTCSeconds(),
    now.getUTCMilliseconds()
  ) - totalDuration)

  // Create status timeline with sample rate
  const sampleCount = 100 // Sample points across the timeline
  const statusPoints: { x: number, y: number }[] = []

  for (let i = 0; i < sampleCount; i++) {
    const timePoint = startTime.getTime() + (i / (sampleCount - 1)) * totalDuration
    let isUp = true

    // Check if this time point falls within any down period
    for (const downPeriod of statusDownPeriods) {
      // Backend timestamps are in UTC, parse them as UTC
      const downStart = new Date(downPeriod.start + 'Z').getTime()
      const downEnd = new Date(downPeriod.end + 'Z').getTime()

      if (timePoint >= downStart && timePoint <= downEnd) {
        isUp = false
        break
      }
    }

    const x = (i / (sampleCount - 1)) * chartWidth
    const y = isUp ? 0 : chartHeight // 0 for up (top), chartHeight for down (bottom)

    statusPoints.push({ x, y })
  }

  if (statusPoints.length === 0) return ''

  const pathCommands = statusPoints.map((point, index) => {
    return index === 0 ? `M ${point.x},${point.y}` : `L ${point.x},${point.y}`
  })

  return pathCommands.join(' ')
}

// Generate down period markers for overlay
const generateDownPeriodMarkers = (statusDownPeriods: StatusDownPeriod[], timeframe: TimeframeType): { x: number, width: number }[] => {
  if (statusDownPeriods.length === 0) return []

  const chartWidth = 300
  let totalDuration: number
  const now = new Date()

  // Calculate total duration based on timeframe
  switch (timeframe) {
    case '24h':
      totalDuration = 24 * 60 * 60 * 1000 // 24 hours
      break
    case '7d':
      totalDuration = 7 * 24 * 60 * 60 * 1000 // 7 days
      break
    case '90d':
      totalDuration = 90 * 24 * 60 * 60 * 1000 // 90 days
      break
    case '365d':
      totalDuration = 365 * 24 * 60 * 60 * 1000 // 365 days
      break
    default:
      totalDuration = 24 * 60 * 60 * 1000
  }

  // Use UTC time for consistent comparison with backend data
  const startTime = new Date(Date.UTC(
    now.getUTCFullYear(),
    now.getUTCMonth(),
    now.getUTCDate(),
    now.getUTCHours(),
    now.getUTCMinutes(),
    now.getUTCSeconds(),
    now.getUTCMilliseconds()
  ) - totalDuration)
  const periods: { x: number, width: number }[] = []

  for (const downPeriod of statusDownPeriods) {
    // Backend timestamps are in UTC, parse them as UTC
    const startTime_period = new Date(downPeriod.start + 'Z')
    const endTime_period = new Date(downPeriod.end + 'Z')

    // Calculate position and width as percentage of total period
    const startOffset = Math.max(0, startTime_period.getTime() - startTime.getTime())
    const endOffset = Math.min(totalDuration, endTime_period.getTime() - startTime.getTime())

    if (startOffset < totalDuration && endOffset > 0) {
      const x = (startOffset / totalDuration) * chartWidth
      const width = Math.max(2, ((endOffset - startOffset) / totalDuration) * chartWidth) // Minimum 2px width

      if (width > 0) {
        periods.push({ x, width })
      }
    }
  }

  return periods
}

// Format timeframe for display
const getTimeframeLabel = (timeframe: TimeframeType): string => {
  switch (timeframe) {
    case '24h': return '24 Hours'
    case '7d': return '7 Days'
    case '90d': return '90 Days'
    case '365d': return '1 Year'
    default: return timeframe
  }
}

// Generate time labels for x-axis
const getTimeLabels = (timeframe: TimeframeType): string[] => {
  const now = new Date()
  let totalDuration: number

  switch (timeframe) {
    case '24h':
      totalDuration = 24 * 60 * 60 * 1000
      break
    case '7d':
      totalDuration = 7 * 24 * 60 * 60 * 1000
      break
    case '90d':
      totalDuration = 90 * 24 * 60 * 60 * 1000
      break
    case '365d':
      totalDuration = 365 * 24 * 60 * 60 * 1000
      break
    default:
      totalDuration = 24 * 60 * 60 * 1000
  }

  // Calculate startTime in local timezone for display labels
  const startTime = new Date(now.getTime() - totalDuration)
  const labels: string[] = []

  // Generate labels for 0%, 25%, 50%, 75%, and 100% (now)
  const positions = [0, 0.25, 0.5, 0.75, 1]

  for (let i = 0; i < positions.length; i++) {
    const position = positions[i]

    if (position === 1) {
      // Far right shows "now"
      labels.push('now')
    } else {
      // Calculate time at this position
      const timeAtPosition = new Date(startTime.getTime() + (position || 0) * totalDuration)

      switch (timeframe) {
        case '24h':
          labels.push(timeAtPosition.toLocaleTimeString([], {
            hour: '2-digit',
            minute: '2-digit'
          }))
          break
        case '7d':
          labels.push(timeAtPosition.toLocaleDateString([], {
            weekday: 'short'
          }))
          break
        case '90d':
        case '365d':
          labels.push(timeAtPosition.toLocaleDateString([], {
            month: 'short',
            day: 'numeric'
          }))
          break
        default:
          labels.push(timeAtPosition.toLocaleTimeString([], {
            hour: '2-digit',
            minute: '2-digit'
          }))
      }
    }
  }

  return labels
}

// Calculate uptime percentage
const calculateUptimePercentage = (statusDownPeriods: StatusDownPeriod[], timeframe: TimeframeType): number => {
  if (statusDownPeriods.length === 0) return 100

  let totalDuration: number
  const now = new Date()

  switch (timeframe) {
    case '24h':
      totalDuration = 24 * 60 * 60 * 1000
      break
    case '7d':
      totalDuration = 7 * 24 * 60 * 60 * 1000
      break
    case '90d':
      totalDuration = 90 * 24 * 60 * 60 * 1000
      break
    case '365d':
      totalDuration = 365 * 24 * 60 * 60 * 1000
      break
    default:
      totalDuration = 24 * 60 * 60 * 1000
  }

  // Use UTC time for consistent comparison with backend data
  const startTime = new Date(Date.UTC(
    now.getUTCFullYear(),
    now.getUTCMonth(),
    now.getUTCDate(),
    now.getUTCHours(),
    now.getUTCMinutes(),
    now.getUTCSeconds(),
    now.getUTCMilliseconds()
  ) - totalDuration)

  let downTime = 0

  for (const downPeriod of statusDownPeriods) {
    // Backend timestamps are in UTC, parse them as UTC
    const startTime_period = new Date(downPeriod.start + 'Z')
    const endTime_period = new Date(downPeriod.end + 'Z')

    const startOffset = Math.max(0, startTime_period.getTime() - startTime.getTime())
    const endOffset = Math.min(totalDuration, endTime_period.getTime() - startTime.getTime())

    if (startOffset < totalDuration && endOffset > 0) {
      downTime += endOffset - startOffset
    }
  }

  return Math.max(0, ((totalDuration - downTime) / totalDuration) * 100)
}

// Format downtime periods for display
const formatDowntimePeriods = (statusDownPeriods: StatusDownPeriod[]): { start: string, end: string, duration: string }[] => {
  return statusDownPeriods
    .map(period => {
      // Backend timestamps are in UTC, parse them as UTC then convert to local for display
      const startDate = new Date(period.start + 'Z')
      const endDate = new Date(period.end + 'Z')

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
    .sort((a, b) => new Date(a.start).getTime() - new Date(b.start).getTime())
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
  <div class="status-chart">
    <div class="chart-header">
      <div class="chart-title-row">
        <span class="chart-title">{{ title || `Status (${getTimeframeLabel(timeframe)})` }}</span>
        <span
          class="uptime-badge clickable"
          :class="{
            'excellent': calculateUptimePercentage(statusDownPeriods, timeframe) >= 99,
            'good': calculateUptimePercentage(statusDownPeriods, timeframe) >= 95 && calculateUptimePercentage(statusDownPeriods, timeframe) < 99,
            'warning': calculateUptimePercentage(statusDownPeriods, timeframe) >= 90 && calculateUptimePercentage(statusDownPeriods, timeframe) < 95,
            'poor': calculateUptimePercentage(statusDownPeriods, timeframe) < 90
          }"
          @click="openModal"
        >
          {{ calculateUptimePercentage(statusDownPeriods, timeframe).toFixed(2) }}% uptime
        </span>
      </div>
    </div>
    <div class="chart-container">
      <div class="chart-y-axis">
        <span class="y-axis-label">Down</span>
      </div>
      <div class="chart-main">
        <div class="chart-svg-container">
          <svg class="chart-svg" viewBox="0 0 300 30" preserveAspectRatio="none">
            <!-- Down period markers (dark red bars) -->
            <rect
              v-for="(period, index) in generateDownPeriodMarkers(statusDownPeriods, timeframe)"
              :key="`down-${index}`"
              :x="period.x"
              :y="0"
              :width="period.width"
              :height="30"
              fill="#dc2626"
            />
          </svg>

          <!-- X-axis labels -->
          <div class="x-axis-labels">
            <span
              v-for="(label, index) in getTimeLabels(timeframe)"
              :key="index"
              class="x-axis-label"
              :style="{ left: `${(index / (getTimeLabels(timeframe).length - 1)) * 100}%` }"
            >
              {{ label }}
            </span>
          </div>
        </div>
      </div>
    </div>

    <!-- Modal Dialog -->
    <Teleport to="body">
      <div v-if="showModal" class="modal-overlay" @click="closeModal">
        <div class="modal-content" @click.stop>
          <div class="modal-header">
            <h3 class="modal-title">Downtime Periods ({{ getTimeframeLabel(timeframe) }})</h3>
            <button class="modal-close" @click="closeModal">&times;</button>
          </div>
          <div class="modal-body">
            <div v-if="statusDownPeriods.length === 0" class="no-downtime">
              <p>No downtime periods recorded for this timeframe.</p>
            </div>
            <div v-else class="downtime-table-container">
              <table class="downtime-table">
                <thead>
                  <tr>
                    <th>Start</th>
                    <th>End</th>
                    <th>Duration</th>
                  </tr>
                </thead>
                <tbody>
                  <tr v-for="(period, index) in formatDowntimePeriods(statusDownPeriods)" :key="index">
                    <td>{{ period.start }}</td>
                    <td>{{ period.end }}</td>
                    <td>{{ period.duration }}</td>
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
  margin-bottom: 1rem;
  padding-left: 0;
  padding-right: 0;
}

.chart-header {
  margin-bottom: 0.5rem;
}

.chart-title-row {
  display: flex;
  justify-content: space-between;
  align-items: center;
}

.chart-title {
  font-size: 0.8rem;
  color: #6c757d;
  font-weight: 500;
}

.uptime-badge {
  font-size: 0.7rem;
  padding: 0.2rem 0.4rem;
  border-radius: 10px;
  font-weight: 500;
}

.uptime-badge.excellent {
  background: #d4edda;
  color: #155724;
}

.uptime-badge.good {
  background: #d1ecf1;
  color: #0c5460;
}

.uptime-badge.warning {
  background: #ffeaa7;
  color: #856404;
}

.uptime-badge.poor {
  background: #f8d7da;
  color: #721c24;
}

.uptime-badge.clickable {
  cursor: pointer;
  transition: opacity 0.2s ease;
}

.uptime-badge.clickable:hover {
  opacity: 0.8;
}

.chart-container {
  display: flex;
  align-items: flex-start;
  gap: 0.5rem;
}

.chart-y-axis {
  display: flex;
  align-items: flex-start;
  height: 30px;
  padding-top: 2px;
}

.y-axis-label {
  font-size: 0.7rem;
  color: #6c757d;
  font-weight: 500;
  white-space: nowrap;
  writing-mode: horizontal-tb;
}

.chart-main {
  flex: 1;
  position: relative;
}

.chart-svg-container {
  position: relative;
}

.chart-svg {
  width: 100%;
  height: 30px;
  background: linear-gradient(to bottom, #f8f9fa 0%, #ffffff 100%);
  border: 1px solid #e9ecef;
  border-radius: 4px;
}

.chart-placeholder {
  height: 30px;
  background: #f8f9fa;
  border: 1px solid #e9ecef;
  border-radius: 4px;
  display: flex;
  align-items: center;
  justify-content: center;
}

.x-axis-labels {
  position: relative;
  height: 15px;
  margin-top: 0.25rem;
}

.x-axis-label {
  position: absolute;
  font-size: 0.6rem;
  color: #6c757d;
  transform: translateX(-50%);
  white-space: nowrap;
}

.placeholder-text {
  font-size: 0.7rem;
  color: #6c757d;
  font-style: italic;
}

/* Modal styles */
.modal-overlay {
  position: fixed;
  inset: 0;
  min-height: 100vh;
  min-height: 100dvh;
  padding: 1.5rem;
  box-sizing: border-box;
  background: rgba(0, 0, 0, 0.5);
  display: flex;
  align-items: flex-start;
  justify-content: center;
  z-index: 1000;
  overflow-y: auto;
}

.modal-content {
  background: white;
  border-radius: 8px;
  box-shadow: 0 10px 25px rgba(0, 0, 0, 0.2);
  width: min(600px, 100%);
  max-height: calc(100vh - 3rem);
  max-height: calc(100dvh - 3rem);
  overflow: hidden;
  display: flex;
  flex-direction: column;
}

.modal-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 1rem 1.5rem;
  border-bottom: 1px solid #e9ecef;
  background: #f8f9fa;
}

.modal-title {
  margin: 0;
  font-size: 1.1rem;
  color: #2c3e50;
  font-weight: 600;
}

.modal-close {
  background: none;
  border: none;
  font-size: 1.5rem;
  color: #6c757d;
  cursor: pointer;
  padding: 0;
  width: 30px;
  height: 30px;
  display: flex;
  align-items: center;
  justify-content: center;
  border-radius: 4px;
  transition: background-color 0.2s ease;
}

.modal-close:hover {
  background: #e9ecef;
  color: #495057;
}

.modal-body {
  padding: 1.5rem;
  overflow-y: auto;
  flex: 1;
}

.no-downtime {
  text-align: center;
  color: #6c757d;
  font-style: italic;
  padding: 2rem 0;
}

.downtime-table-container {
  overflow-x: auto;
}

.downtime-table {
  width: 100%;
  border-collapse: collapse;
  font-size: 0.9rem;
}

.downtime-table th,
.downtime-table td {
  text-align: left;
  padding: 0.75rem;
  border-bottom: 1px solid #e9ecef;
}

.downtime-table th {
  background: #f8f9fa;
  color: #495057;
  font-weight: 600;
  position: sticky;
  top: 0;
  z-index: 1;
}

.downtime-table tr:hover {
  background: #f8f9fa;
}

.downtime-table td:first-child,
.downtime-table td:nth-child(2) {
  font-family: 'Monaco', 'Menlo', 'Ubuntu Mono', monospace;
  font-size: 0.85rem;
}

.downtime-table td:last-child {
  font-weight: 500;
  color: #dc3545;
}

/* Mobile styles */
@media (max-width: 768px) {
  .status-chart {
    margin-bottom: 0.75rem;
    padding-left: 0;
    padding-right: 0;
  }

  .chart-container {
    flex-direction: row;
    gap: 0;
    align-items: flex-start;
  }

  .chart-y-axis {
    display: none;
  }

  .y-axis-label {
    font-size: 0.65rem;
  }

  .chart-title {
    font-size: 0.75rem;
  }

  .chart-title-row {
    flex-direction: column;
    align-items: flex-start;
    gap: 0.25rem;
  }

  .uptime-badge {
    font-size: 0.65rem;
    padding: 0.15rem 0.3rem;
  }

  .chart-main {
    flex: 1;
    min-width: 0;
    overflow: hidden;
  }

  .chart-svg {
    height: 25px;
    width: 100%;
  }

  .chart-placeholder {
    height: 25px;
  }

  .x-axis-labels {
    height: 12px;
  }

  .x-axis-label {
    font-size: 0.55rem;
  }

  .placeholder-text {
    font-size: 0.65rem;
  }

  .modal-content {
    width: 100%;
    max-height: calc(100vh - 2rem);
    max-height: calc(100dvh - 2rem);
  }

  .modal-header {
    padding: 1rem;
  }

  .modal-body {
    padding: 1rem;
  }

  .downtime-table {
    font-size: 0.8rem;
  }

  .downtime-table th,
  .downtime-table td {
    padding: 0.5rem;
  }
}
</style>
