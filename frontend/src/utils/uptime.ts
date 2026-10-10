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

export interface DownColumn {
  /** Index of the first pixel column. */
  x: number
  /** Number of pixel columns (adjacent columns with the same color are merged). */
  width: number
  /** Share of the column time that was down, 0 < fraction <= 1. */
  fraction: number
  color: string
}

/**
 * Start of the checked part of the window. Before it the monitor did not exist or was paused.
 * Null when there are no checks. An older server sends no first check: then the whole window
 * counts as checked.
 */
export const checkedFromMs = (
  firstCheckAt: string | null | undefined,
  totalChecks: number | null | undefined,
  window: TimeWindow
): number | null => {
  if (firstCheckAt) return Math.max(window.startMs, parseUtc(firstCheckAt).getTime())
  return totalChecks === 0 ? null : window.startMs
}

/**
 * Splits the window into `columns` equal time slots (one per pixel) and gives the share of each
 * slot that was down. The share counts only the checked time of a slot, from `checkedFrom` on.
 * Slots without downtime are left out.
 */
export const downFractionColumns = (
  periods: Array<{ start: string; end: string }>,
  window: TimeWindow,
  columns: number,
  checkedFrom: number = window.startMs
): DownColumn[] => {
  const { startMs, endMs } = window
  const total = endMs - startMs
  if (total <= 0 || columns <= 0) return []
  const slotMs = total / columns

  const downMs = new Array<number>(columns).fill(0)
  // Merge overlaps first, so a time is never counted twice.
  const intervals = periods
    .map((p): [number, number] => [
      Math.max(startMs, parseUtc(p.start).getTime()),
      Math.min(endMs, parseUtc(p.end).getTime())
    ])
    .filter(([s, e]) => e > s)
    .sort((a, b) => a[0] - b[0])
  const merged: Array<[number, number]> = []
  for (const [s, e] of intervals) {
    const last = merged[merged.length - 1]
    if (last && s <= last[1]) last[1] = Math.max(last[1], e)
    else merged.push([s, e])
  }

  for (const [s, e] of merged) {
    const first = Math.min(columns - 1, Math.floor((s - startMs) / slotMs))
    const lastSlot = Math.min(columns - 1, Math.floor((e - startMs) / slotMs))
    for (let i = first; i <= lastSlot; i++) {
      const slotStart = startMs + i * slotMs
      const overlap = Math.min(e, slotStart + slotMs) - Math.max(s, slotStart)
      if (overlap > 0) downMs[i] = (downMs[i] ?? 0) + overlap
    }
  }

  const result: DownColumn[] = []
  downMs.forEach((ms, i) => {
    if (ms <= 0) return
    const slotStart = startMs + i * slotMs
    const checked = slotStart + slotMs - Math.max(slotStart, checkedFrom)
    const fraction = Math.min(1, ms / Math.max(checked, ms))
    const color = downFractionColor(fraction)
    const previous = result[result.length - 1]
    if (previous && previous.x + previous.width === i && previous.color === color) {
      previous.width += 1
    } else {
      result.push({ x: i, width: 1, fraction, color })
    }
  })
  return result
}

// Light yellow (a short part of the slot was down) to dark red (all of the slot was down).
const DOWN_COLOR_STOPS: Array<[number, [number, number, number]]> = [
  [0, [254, 249, 195]], // #fef9c3
  [0.25, [250, 204, 21]], // #facc15
  [0.5, [249, 115, 22]], // #f97316
  [0.75, [220, 38, 38]], // #dc2626
  [1, [127, 29, 29]] // #7f1d1d
]

export const downFractionColor = (fraction: number): string => {
  const f = Math.min(1, Math.max(0, fraction))
  const upper = Math.max(1, DOWN_COLOR_STOPS.findIndex(([position]) => f <= position))
  const [p0, c0] = DOWN_COLOR_STOPS[upper - 1]!
  const [p1, c1] = DOWN_COLOR_STOPS[upper]!
  const t = (f - p0) / (p1 - p0)
  const channel = (k: 0 | 1 | 2) => Math.round(c0[k] + (c1[k] - c0[k]) * t).toString(16).padStart(2, '0')
  return `#${channel(0)}${channel(1)}${channel(2)}`
}
