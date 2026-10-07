<script setup lang="ts">
import { computed } from 'vue'
import type { TimeframeType } from './TimeframeSwitcher.vue'
import { getTimeframeLabel, parseUtc, timeWindow } from '../utils/uptime'

interface ResponseTimeDataPoint {
  timestamp: string
  maxResponseTimeMs: number
}

interface ChartProps {
  data: ResponseTimeDataPoint[]
  timeframe: TimeframeType
  // Window of the data (UTC, from the backend). Default: the timeframe until now.
  windowStart?: string
  windowEnd?: string
  title?: string
}

const props = defineProps<ChartProps>()

const WIDTH = 300
const HEIGHT = 30

const chartWindow = computed(() => timeWindow(props.timeframe, props.windowStart, props.windowEnd))

const maxResponseTime = computed(() =>
  props.data.length > 0 ? Math.max(...props.data.map(d => d.maxResponseTimeMs)) : 0
)

// SVG path. Each point sits at the position of its time in the window, so gaps in the data
// (for example a new monitor) do not stretch the line.
const responseTimePath = computed((): string => {
  if (props.data.length === 0) return ''

  const { startMs, endMs } = chartWindow.value
  const duration = endMs - startMs || 1
  const minValue = Math.min(...props.data.map(d => d.maxResponseTimeMs))
  const range = maxResponseTime.value - minValue || 1

  const points = props.data.map(point => {
    const offset = parseUtc(point.timestamp).getTime() - startMs
    const x = Math.min(WIDTH, Math.max(0, (offset / duration) * WIDTH))
    const y = HEIGHT - ((point.maxResponseTimeMs - minValue) / range) * HEIGHT
    return `${x},${y}`
  })

  return `M ${points.join(' L ')}`
})

// Labels at 0%, 25%, 50%, 75% and 100% (now) of the window
const timeLabels = computed((): string[] => {
  const { startMs, endMs } = chartWindow.value
  return [0, 0.25, 0.5, 0.75, 1].map(position => {
    if (position === 1) return 'now'
    const time = new Date(startMs + position * (endMs - startMs))
    return props.timeframe === '24h'
      ? time.toLocaleTimeString([], { hour: '2-digit', minute: '2-digit' })
      : time.toLocaleDateString([], { month: 'short', day: 'numeric' })
  })
})
</script>

<template>
  <div class="enhanced-response-time-chart">
    <div class="chart-header">
      <span class="chart-title">{{ title || `Response Time (${getTimeframeLabel(timeframe)})` }}</span>
    </div>
    <div class="chart-container">
      <div class="chart-y-axis">
        <span class="y-axis-label" v-if="data.length > 0">{{ maxResponseTime }}ms</span>
      </div>
      <div class="chart-main">
        <div v-if="data.length === 0" class="chart-placeholder">
          <span class="placeholder-text">No data available</span>
        </div>
        <div v-else class="chart-svg-container">
          <svg class="chart-svg" viewBox="0 0 300 30" preserveAspectRatio="none">
            <path
              :d="responseTimePath"
              fill="none"
              stroke="#007bff"
              stroke-width="2"
              vector-effect="non-scaling-stroke"
            />
          </svg>

          <!-- X-axis labels -->
          <div class="x-axis-labels">
            <span
              v-for="(label, index) in timeLabels"
              :key="index"
              class="x-axis-label"
              :style="{ left: `${(index / (timeLabels.length - 1)) * 100}%` }"
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
