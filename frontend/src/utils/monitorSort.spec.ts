import { describe, expect, it } from 'vitest'
import type { MonitorResponse, MonitorStatus } from '../stores/monitors'
import { defaultDirection, isMonitorSort, sortMonitors } from './monitorSort'

const tenant = (id: number, name: string) => ({
  id,
  name,
  code: name.toLowerCase(),
  description: '',
  isActive: true,
  createdAt: '2026-01-01T00:00:00',
  updatedAt: '2026-01-01T00:00:00'
})

const monitor = (
  id: number,
  name: string,
  tenantName = 'Main',
  createdAt = '2026-01-01T00:00:00'
): MonitorResponse => ({
  id,
  name,
  url: `https://${name.toLowerCase()}.example.com`,
  tenantId: id,
  tenant: tenant(id, tenantName),
  state: 'ACTIVE',
  alertingThreshold: 0,
  createdAt,
  updatedAt: createdAt
})

const statuses =
  (lastDownAt: Record<number, string>) =>
  (id: number): MonitorStatus | undefined =>
    lastDownAt[id] ? ({ monitorId: id, lastDownAt: lastDownAt[id] } as MonitorStatus) : undefined

const names = (monitors: MonitorResponse[]) => monitors.map(m => m.name)

describe('sortMonitors', () => {
  const noStatus = () => undefined

  it('sorts by name with numbers in order', () => {
    const monitors = [
      monitor(1, 'item 10'),
      monitor(2, 'Beta'),
      monitor(3, 'alpha'),
      monitor(4, 'item 9')
    ]
    expect(names(sortMonitors(monitors, noStatus, { key: 'name', direction: 'asc' }))).toEqual([
      'alpha',
      'Beta',
      'item 9',
      'item 10'
    ])
    expect(names(sortMonitors(monitors, noStatus, { key: 'name', direction: 'desc' }))).toEqual([
      'item 10',
      'item 9',
      'Beta',
      'alpha'
    ])
  })

  it('sorts by tenant, then by name', () => {
    const monitors = [
      monitor(1, 'B', 'Zeta'),
      monitor(2, 'A', 'Zeta'),
      monitor(3, 'C', 'Alpha'),
      monitor(4, 'A', 'Alpha')
    ]
    expect(names(sortMonitors(monitors, noStatus, { key: 'tenant', direction: 'asc' }))).toEqual([
      'A',
      'C',
      'A',
      'B'
    ])
    expect(
      sortMonitors(monitors, noStatus, { key: 'tenant', direction: 'asc' }).map(m => m.id)
    ).toEqual([4, 3, 2, 1])
    expect(
      sortMonitors(monitors, noStatus, { key: 'tenant', direction: 'desc' }).map(m => m.id)
    ).toEqual([1, 2, 3, 4])
  })

  it('sorts by last downtime, never down last in both directions', () => {
    const monitors = [
      monitor(1, 'Never'),
      monitor(2, 'Old'),
      monitor(3, 'Recent'),
      monitor(4, 'Also never')
    ]
    const statusOf = statuses({ 2: '2026-10-01T10:00:00', 3: '2026-10-12T08:30:00.123456' })
    expect(names(sortMonitors(monitors, statusOf, { key: 'lastDown', direction: 'desc' }))).toEqual(
      ['Recent', 'Old', 'Never', 'Also never']
    )
    expect(names(sortMonitors(monitors, statusOf, { key: 'lastDown', direction: 'asc' }))).toEqual([
      'Old',
      'Recent',
      'Also never',
      'Never'
    ])
  })

  it('sorts by creation time', () => {
    const monitors = [
      monitor(1, 'Middle', 'Main', '2026-05-01T00:00:00'),
      monitor(2, 'First', 'Main', '2026-01-01T00:00:00'),
      monitor(3, 'Last', 'Main', '2026-09-01T00:00:00.5')
    ]
    expect(names(sortMonitors(monitors, noStatus, { key: 'created', direction: 'asc' }))).toEqual([
      'First',
      'Middle',
      'Last'
    ])
    expect(names(sortMonitors(monitors, noStatus, { key: 'created', direction: 'desc' }))).toEqual([
      'Last',
      'Middle',
      'First'
    ])
  })

  it('does not change the input', () => {
    const monitors = [monitor(1, 'B'), monitor(2, 'A')]
    sortMonitors(monitors, noStatus, { key: 'name', direction: 'asc' })
    expect(names(monitors)).toEqual(['B', 'A'])
  })
})

describe('defaultDirection', () => {
  it('shows the newest dates first and text from A', () => {
    expect(defaultDirection('name')).toBe('asc')
    expect(defaultDirection('tenant')).toBe('asc')
    expect(defaultDirection('lastDown')).toBe('desc')
    expect(defaultDirection('created')).toBe('desc')
  })
})

describe('isMonitorSort', () => {
  it('accepts only known keys and directions', () => {
    expect(isMonitorSort({ key: 'created', direction: 'desc' })).toBe(true)
    expect(isMonitorSort({ key: 'status', direction: 'asc' })).toBe(false)
    expect(isMonitorSort({ key: 'name', direction: 'up' })).toBe(false)
    expect(isMonitorSort(null)).toBe(false)
    expect(isMonitorSort('name')).toBe(false)
  })
})
