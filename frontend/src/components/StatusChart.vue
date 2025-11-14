<script setup lang="ts">
interface StatusChangeEvent {
  timestamp: string
  isUp: boolean
}

interface StatusChartProps {
  statusChanges: StatusChangeEvent[]
  title?: string
}

defineProps<StatusChartProps>()

// Generate SVG rectangles for down periods only
const generateStatusChart = (statusChanges: StatusChangeEvent[]): { x: number, width: number }[] => {
  if (statusChanges.length === 0) return []

  const chartWidth = 300
  const totalDuration = 24 * 60 * 60 * 1000 // 24 hours in milliseconds
  const now = new Date()
  const startTime = new Date(now.getTime() - totalDuration)

  // Sort status changes by timestamp (oldest first)
  const sortedChanges = [...statusChanges].sort((a, b) =>
    new Date(a.timestamp).getTime() - new Date(b.timestamp).getTime()
  )

  const downPeriods: { x: number, width: number }[] = []

  for (let i = 0; i < sortedChanges.length; i++) {
    const currentChange = sortedChanges[i]
    if (!currentChange) continue
    const currentTime = new Date(currentChange.timestamp)

    // Only process down periods
    if (!currentChange.isUp) {
      // Find the next status change (should be up)
      const nextChange = sortedChanges[i + 1]
      const nextTime = nextChange ? new Date(nextChange.timestamp) : now

      // Calculate position and width as percentage of 24h period
      const startOffset = Math.max(0, currentTime.getTime() - startTime.getTime())
      const endOffset = Math.min(totalDuration, nextTime.getTime() - startTime.getTime())

      const x = (startOffset / totalDuration) * chartWidth
      const width = ((endOffset - startOffset) / totalDuration) * chartWidth

      if (width > 0 && startOffset < totalDuration) {
        downPeriods.push({
          x,
          width
        })
      }
    }
  }

  return downPeriods
}

// Always show "Down" as the y-axis label
const getYAxisLabel = (): string => {
  return 'Down'
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
</script>

<template>
  <div class="status-chart">
    <div class="chart-header">
      <span class="chart-title">{{ title || 'Status (24h)' }}</span>
    </div>
    <div class="chart-container">
      <div class="chart-y-axis">
        <span class="y-axis-label">{{ getYAxisLabel() }}</span>
      </div>
      <div class="chart-main">
        <div v-if="statusChanges.length === 0" class="chart-placeholder">
          <span class="placeholder-text">No data available</span>
        </div>
        <div v-else class="chart-svg-container">
          <svg class="chart-svg" viewBox="0 0 300 30" preserveAspectRatio="none">
            <rect
              v-for="(period, index) in generateStatusChart(statusChanges)"
              :key="index"
              :x="period.x"
              :y="0"
              :width="period.width"
              :height="30"
              fill="#dc3545"
            />
          </svg>

          <!-- X-axis labels -->
          <div class="x-axis-labels">
            <span
              v-for="(label, index) in getTimeLabels()"
              :key="index"
              class="x-axis-label"
              :style="{ left: `${(index / (getTimeLabels().length - 1)) * 100}%` }"
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
.status-chart {
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
  background: #f8f9fa;
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
