<script setup lang="ts">
import type { TimeframeType } from './TimeframeSwitcher.vue'

interface ResponseTimeDataPoint {
  timestamp: string
  maxResponseTimeMs: number
}

interface ChartProps {
  data: ResponseTimeDataPoint[]
  timeframe: TimeframeType
  title?: string
}

defineProps<ChartProps>()

// Generate SVG path for response time chart
const generateResponseTimeChart = (data: ResponseTimeDataPoint[]): string => {
  if (data.length === 0) return ''

  const width = 300
  const height = 30
  const maxValue = Math.max(...data.map(d => d.maxResponseTimeMs))
  const minValue = Math.min(...data.map(d => d.maxResponseTimeMs))
  const range = maxValue - minValue || 1

  const points = data.map((dataPoint, index) => {
    const x = (index / (data.length - 1)) * width
    const y = height - ((dataPoint.maxResponseTimeMs - minValue) / range) * height
    return `${x},${y}`
  })

  return `M ${points.join(' L ')}`
}

// Get max response time for y-axis label
const getMaxResponseTime = (data: ResponseTimeDataPoint[]): number => {
  return data.length > 0 ? Math.max(...data.map(d => d.maxResponseTimeMs)) : 0
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
const getTimeLabels = (data: ResponseTimeDataPoint[], timeframe: TimeframeType): string[] => {
  if (data.length === 0) return []

  const labels: string[] = []
  const now = new Date()
  let totalDuration: number

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

  const startTime = new Date(now.getTime() - totalDuration)

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
          labels.push(timeAtPosition.toLocaleTimeString([], { hour: '2-digit', minute: '2-digit' }))
          break
        case '7d':
          labels.push(timeAtPosition.toLocaleDateString([], { month: 'short', day: 'numeric' }))
          break
        case '90d':
        case '365d':
          labels.push(timeAtPosition.toLocaleDateString([], { month: 'short', day: 'numeric' }))
          break
        default:
          labels.push(timeAtPosition.toLocaleTimeString([], { hour: '2-digit', minute: '2-digit' }))
      }
    }
  }

  return labels
}
</script>

<template>
  <div class="enhanced-response-time-chart">
    <div class="chart-header">
      <span class="chart-title">{{ title || `Response Time (${getTimeframeLabel(timeframe)})` }}</span>
    </div>
    <div class="chart-container">
      <div class="chart-y-axis">
        <span class="y-axis-label" v-if="data.length > 0">{{ getMaxResponseTime(data) }}ms</span>
      </div>
      <div class="chart-main">
        <div v-if="data.length === 0" class="chart-placeholder">
          <span class="placeholder-text">No data available</span>
        </div>
        <div v-else class="chart-svg-container">
          <svg class="chart-svg" viewBox="0 0 300 30" preserveAspectRatio="none">
            <path
              :d="generateResponseTimeChart(data)"
              fill="none"
              stroke="#007bff"
              stroke-width="2"
              vector-effect="non-scaling-stroke"
            />
          </svg>

          <!-- X-axis labels -->
          <div class="x-axis-labels">
            <span
              v-for="(label, index) in getTimeLabels(data, timeframe)"
              :key="index"
              class="x-axis-label"
              :style="{ left: `${(index / (getTimeLabels(data, timeframe).length - 1)) * 100}%` }"
            >
              {{ label }}
            </span>
          </div>
        </div>
      </div>
    </div>
  </div>
</template>

<style scoped>
.enhanced-response-time-chart {
  margin-bottom: 1rem;
  padding-left: 0;
  padding-right: 0;
}

.chart-header {
  margin-bottom: 0.5rem;
}

.chart-title {
  font-size: 0.8rem;
  color: #6c757d;
  font-weight: 500;
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

.placeholder-text {
  font-size: 0.7rem;
  color: #6c757d;
  font-style: italic;
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

/* Mobile styles */
@media (max-width: 768px) {
  .enhanced-response-time-chart {
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
}
</style>
