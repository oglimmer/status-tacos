import type { TimeframeType } from '../components/TimeframeSwitcher.vue'

/** Period names of the uptime stats API. 90 days is the maximum. */
export type StatsPeriodParam = 'seven_days' | 'ninety_days'

export const timeframeToPeriod = (timeframe: TimeframeType): StatsPeriodParam | null => {
  switch (timeframe) {
    case '7d': return 'seven_days'
    case '90d': return 'ninety_days'
    default: return null
  }
}

const HOUR_MS = 60 * 60 * 1000

export const TIMEFRAME_DURATION_MS: Record<TimeframeType, number> = {
  '24h': 24 * HOUR_MS,
  '7d': 7 * 24 * HOUR_MS,
  '90d': 90 * 24 * HOUR_MS
}

export const getTimeframeLabel = (timeframe: TimeframeType): string => {
  switch (timeframe) {
    case '24h': return '24 Hours'
    case '7d': return '7 Days'
    case '90d': return '90 Days'
  }
}

/** Backend timestamps are UTC without a zone suffix. */
export const parseUtc = (value: string): Date =>
  new Date(value.endsWith('Z') ? value : value + 'Z')

export interface TimeWindow {
  startMs: number
  endMs: number
}

/**
 * The time window of a chart. The backend sends the exact window of the stats (aligned to UTC
 * hours or days). Without it, the window is the timeframe until now.
 */
export const timeWindow = (
  timeframe: TimeframeType,
  windowStart?: string,
  windowEnd?: string,
  nowMs: number = Date.now()
): TimeWindow => {
  if (windowStart && windowEnd) {
    return { startMs: parseUtc(windowStart).getTime(), endMs: parseUtc(windowEnd).getTime() }
  }
  return { startMs: nowMs - TIMEFRAME_DURATION_MS[timeframe], endMs: nowMs }
}

/**
 * Uptime with 2 decimals, rounded down: 99.996 % is shown as 99.99 %, never as 100.00 %.
 * The backend already rounds down; this keeps it that way for any value.
 */
export const formatUptime = (percentage: number | null | undefined): string => {
  if (percentage === null || percentage === undefined) return 'N/A'
  // The small epsilon keeps values like 99.99 (9998.999... after * 100) at 99.99.
  const floored = Math.floor(percentage * 100 + 1e-6) / 100
  return `${floored.toFixed(2)}%`
}

export type UptimeLevel = 'excellent' | 'good' | 'warning' | 'poor' | 'no-data'

export const uptimeLevel = (percentage: number | null | undefined): UptimeLevel => {
  if (percentage === null || percentage === undefined) return 'no-data'
  if (percentage >= 99) return 'excellent'
  if (percentage >= 95) return 'good'
  if (percentage >= 90) return 'warning'
  return 'poor'
}
