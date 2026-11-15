<script setup lang="ts">
import { computed } from 'vue'

interface ChartProps {
  data: number[]
  title?: string
}

const props = defineProps<ChartProps>()

// Generate SVG path for response time chart
const generateResponseTimeChart = (data: number[]): string => {
  if (data.length === 0) return ''

  const width = 300
  const height = 30
  const maxValue = Math.max(...data)
  const minValue = Math.min(...data)
  const range = maxValue - minValue || 1

  const points = data.map((value, index) => {
    const x = (index / (data.length - 1)) * width
    const y = height - ((value - minValue) / range) * height
    return `${x},${y}`
  })

  return `M ${points.join(' L ')}`
}

// Get max response time for y-axis label
const getMaxResponseTime = (data: number[]): number => {
  return data.length > 0 ? Math.max(...data) : 0
}

// Generate time labels for x-axis (24h timeframe)
const getTimeLabels = (): string[] => {
  const now = new Date()
  const totalDuration = 24 * 60 * 60 * 1000 // 24 hours
  const startTime = new Date(now.getTime() - totalDuration)

  // Generate labels for 0%, 25%, 50%, 75%, and 100% (now)
  const positions = [0, 0.25, 0.5, 0.75, 1]
  const labels: string[] = []

  for (let i = 0; i < positions.length; i++) {
    const position = positions[i]

    if (position === 1) {
      // Far right shows "now"
      labels.push('now')
    } else {
      // Calculate time at this position
      const timeAtPosition = new Date(startTime.getTime() + (position || 0) * totalDuration)
      labels.push(timeAtPosition.toLocaleTimeString([], { hour: '2-digit', minute: '2-digit' }))
    }
  }

  return labels
}

const path = computed(() => generateResponseTimeChart(props.data))
const maxResponseTime = computed(() => getMaxResponseTime(props.data))
// The labels move with the clock: they are made again when new data arrives
const timeLabels = computed(() => {
  void props.data
  return getTimeLabels()
})
</script>

<template>
  <div class="rt-chart">
    <div class="rt-chart-head">
      <span class="rt-chart-title">{{ title || 'Response time (24h)' }}</span>
      <span v-if="data.length > 0" class="rt-chart-max num" title="Highest response time">max {{ maxResponseTime }} ms</span>
    </div>
    <div v-if="data.length === 0" class="rt-chart-empty">No data yet</div>
    <template v-else>
      <svg class="rt-chart-svg" viewBox="0 0 300 30" preserveAspectRatio="none" aria-hidden="true">
        <path
          class="rt-chart-line"
          :d="path"
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
