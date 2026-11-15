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
  <div class="rt-chart">
    <div class="rt-chart-head">
      <span class="rt-chart-title">{{ title || `Response time (${getTimeframeLabel(timeframe)})` }}</span>
      <span v-if="data.length > 0" class="rt-chart-max num" title="Highest response time">max {{ maxResponseTime }} ms</span>
    </div>
    <div v-if="data.length === 0" class="rt-chart-empty">No data yet</div>
    <template v-else>
      <svg class="rt-chart-svg" viewBox="0 0 300 30" preserveAspectRatio="none" aria-hidden="true">
        <path
          class="rt-chart-line"
          :d="responseTimePath"
          fill="none"
          stroke-width="1.5"
          vector-effect="non-scaling-stroke"
        />
      </svg>
      <div class="rt-chart-axis" aria-hidden="true">
        <span
          v-for="(label, index) in timeLabels"
          :key="index"
          :style="{ left: `${(index / (timeLabels.length - 1)) * 100}%` }"
        >{{ label }}</span>
      </div>
    </template>
  </div>
</template>

<style scoped>
/* A small line chart: the title and the highest value above, the time below. */
.rt-chart {
  min-width: 0;
}

.rt-chart-head {
  display: flex;
  align-items: baseline;
  justify-content: space-between;
  gap: var(--space-2);
  margin-bottom: var(--space-1);
  font-size: var(--text-xs);
  color: var(--text-muted);
}

.rt-chart-title {
  font-weight: 700;
  color: var(--text-2);
}

.rt-chart-max {
  color: var(--text-muted);
}

.rt-chart-svg,
.rt-chart-empty {
  display: block;
  width: 100%;
  height: 2.5rem;
  border: 1px solid var(--border-subtle);
  border-radius: var(--radius-sm);
  background: var(--surface-2);
}

.rt-chart-line {
  stroke: var(--brand);
}

.rt-chart-empty {
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: var(--text-xs);
  color: var(--text-muted);
}

.rt-chart-axis {
  position: relative;
  height: 1rem;
  margin-top: 0.125rem;
}

.rt-chart-axis span {
  position: absolute;
  font-family: var(--font-mono);
  font-size: 0.75rem; /* 12px: small axis labels, still readable */
  color: var(--text-muted);
  white-space: nowrap;
  transform: translateX(-50%);
}

.rt-chart-axis span:first-child {
  transform: none;
}

.rt-chart-axis span:last-child {
  transform: translateX(-100%);
}

@media (max-width: 520px) {
  /* Only start, middle and now fit on a phone. */
  .rt-chart-axis span:nth-child(2),
  .rt-chart-axis span:nth-child(4) {
    display: none;
  }
}
</style>
