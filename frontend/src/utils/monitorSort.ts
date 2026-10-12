import type { MonitorResponse, MonitorStatus } from '../stores/monitors'
import { parseUtc } from './uptime'

export type MonitorSortKey = 'name' | 'lastDown' | 'tenant' | 'created'
export type SortDirection = 'asc' | 'desc'

export interface MonitorSort {
  key: MonitorSortKey
  direction: SortDirection
}

export const DEFAULT_MONITOR_SORT: MonitorSort = { key: 'name', direction: 'asc' }

export const MONITOR_SORT_OPTIONS: { value: MonitorSortKey; label: string }[] = [
  { value: 'name', label: 'Name' },
  { value: 'lastDown', label: 'Last downtime' },
  { value: 'tenant', label: 'Tenant, then name' },
  { value: 'created', label: 'Created' }
]

/** The direction a key starts with: dates show the newest first, text starts at A. */
export const defaultDirection = (key: MonitorSortKey): SortDirection =>
  key === 'lastDown' || key === 'created' ? 'desc' : 'asc'

export const isMonitorSort = (value: unknown): value is MonitorSort => {
  if (typeof value !== 'object' || value === null) return false
  const { key, direction } = value as Record<string, unknown>
  return (
    MONITOR_SORT_OPTIONS.some(option => option.value === key) &&
    (direction === 'asc' || direction === 'desc')
  )
}

const compareText = (a: string, b: string): number =>
  a.localeCompare(b, undefined, { numeric: true })

const timeOf = (value: string | null | undefined): number | null =>
  value ? parseUtc(value).getTime() : null

/** Null when the monitor has no value for the key: never down, or no tenant. */
const sortValue = (
  monitor: MonitorResponse,
  status: MonitorStatus | undefined,
  key: MonitorSortKey
): string | number | null => {
  switch (key) {
    case 'name':
      return monitor.name
    case 'tenant':
      return monitor.tenant?.name ?? null
    case 'lastDown':
      // The latest failed check. Inactive monitors have no status.
      return timeOf(status?.lastDownAt)
    case 'created':
      return timeOf(monitor.createdAt)
  }
}

/**
 * Sorts by the key, then by name, then by ID. Descending reverses all of it. Monitors without a
 * value for the key come last in both directions.
 */
export const sortMonitors = (
  monitors: readonly MonitorResponse[],
  statusOf: (monitorId: number) => MonitorStatus | undefined,
  sort: MonitorSort
): MonitorResponse[] => {
  const sign = sort.direction === 'asc' ? 1 : -1
  const rows = monitors.map(monitor => ({
    monitor,
    value: sortValue(monitor, statusOf(monitor.id), sort.key)
  }))
  rows.sort((a, b) => {
    if (a.value === null || b.value === null) {
      if (a.value !== b.value) return a.value === null ? 1 : -1
    } else {
      const byValue =
        typeof a.value === 'string' && typeof b.value === 'string'
          ? compareText(a.value, b.value)
          : Number(a.value) - Number(b.value)
      if (byValue !== 0) return sign * byValue
    }
    const byName = compareText(a.monitor.name, b.monitor.name)
    return sign * (byName !== 0 ? byName : a.monitor.id - b.monitor.id)
  })
  return rows.map(row => row.monitor)
}
