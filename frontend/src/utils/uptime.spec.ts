import { describe, expect, it } from 'vitest'
import { checkedFromMs, downFractionColor, downFractionColumns, formatUptime, parseUtc, timeWindow, timeframeToPeriod, uptimeLevel } from './uptime'

describe('formatUptime', () => {
  it('rounds down', () => {
    expect(formatUptime(99.996)).toBe('99.99%')
    expect(formatUptime(99.999999)).toBe('99.99%')
    expect(formatUptime(66.666)).toBe('66.66%')
  })

  it('keeps values with 2 decimals', () => {
    expect(formatUptime(99.99)).toBe('99.99%')
    expect(formatUptime(0.29)).toBe('0.29%')
    expect(formatUptime(100)).toBe('100.00%')
    expect(formatUptime(0)).toBe('0.00%')
  })

  it('shows N/A without data', () => {
    expect(formatUptime(undefined)).toBe('N/A')
    expect(formatUptime(null)).toBe('N/A')
  })
})

describe('uptimeLevel', () => {
  it('maps percentages to levels', () => {
    expect(uptimeLevel(99)).toBe('excellent')
    expect(uptimeLevel(98.99)).toBe('good')
    expect(uptimeLevel(90)).toBe('warning')
    expect(uptimeLevel(89.99)).toBe('poor')
    expect(uptimeLevel(undefined)).toBe('no-data')
  })
})

describe('timeWindow', () => {
  it('uses the window from the backend as UTC', () => {
    expect(timeWindow('7d', '2026-09-30T14:00:00', '2026-10-07T14:37:12')).toEqual({
      startMs: Date.UTC(2026, 8, 30, 14, 0, 0),
      endMs: Date.UTC(2026, 9, 7, 14, 37, 12)
    })
  })

  it('falls back to the timeframe until now', () => {
    const now = Date.UTC(2026, 9, 7, 12, 0, 0)
    expect(timeWindow('24h', undefined, undefined, now)).toEqual({
      startMs: now - 24 * 60 * 60 * 1000,
      endMs: now
    })
  })
})

describe('helpers', () => {
  it('parses backend timestamps as UTC', () => {
    expect(parseUtc('2026-10-07T12:00:00').getTime()).toBe(Date.UTC(2026, 9, 7, 12))
    expect(parseUtc('2026-10-07T12:00:00Z').getTime()).toBe(Date.UTC(2026, 9, 7, 12))
  })

  it('maps timeframes to API periods', () => {
    expect(timeframeToPeriod('7d')).toBe('seven_days')
    expect(timeframeToPeriod('90d')).toBe('ninety_days')
    expect(timeframeToPeriod('24h')).toBeNull()
  })
})

describe('downFractionColumns', () => {
  // 10 columns of 1 hour each
  const window = { startMs: Date.UTC(2026, 0, 1, 0), endMs: Date.UTC(2026, 0, 1, 10) }
  const at = (hour: number, minute = 0) => new Date(Date.UTC(2026, 0, 1, hour, minute)).toISOString().replace('Z', '')

  it('gives the down share of each column', () => {
    const columns = downFractionColumns([{ start: at(2, 0), end: at(2, 6) }], window, 10)
    expect(columns).toHaveLength(1)
    expect(columns[0]?.x).toBe(2)
    expect(columns[0]?.fraction).toBeCloseTo(0.1)
  })

  it('splits a period over columns and merges equal neighbours', () => {
    const columns = downFractionColumns([{ start: at(3, 30), end: at(7, 0) }], window, 10)
    expect(columns.map(c => [c.x, c.width, c.fraction])).toEqual([
      [3, 1, 0.5],
      [4, 3, 1]
    ])
    expect(columns[1]?.color).toBe('#7f1d1d')
  })

  it('does not count overlapping periods twice and clips to the window', () => {
    const columns = downFractionColumns(
      [
        { start: at(0, 0), end: at(0, 30) },
        { start: at(0, 15), end: at(0, 45) },
        { start: at(9, 30), end: '2026-01-02T00:00:00' }
      ],
      window,
      10
    )
    expect(columns.map(c => [c.x, c.fraction])).toEqual([
      [0, 0.75],
      [9, 0.5]
    ])
  })

  it('counts only the checked time of a column', () => {
    // A new monitor, down since its first check 15 minutes before the end: the last column is
    // all down, not a quarter.
    const columns = downFractionColumns(
      [{ start: at(9, 45), end: at(10, 0) }],
      window,
      10,
      parseUtc(at(9, 45)).getTime()
    )
    expect(columns.map(c => [c.x, c.width, c.fraction])).toEqual([[9, 1, 1]])
  })
})

describe('checkedFromMs', () => {
  const window = { startMs: Date.UTC(2026, 0, 1, 0), endMs: Date.UTC(2026, 0, 1, 10) }

  it('starts at the first check, not before the window', () => {
    expect(checkedFromMs('2026-01-01T03:00:00', 5, window)).toBe(Date.UTC(2026, 0, 1, 3))
    expect(checkedFromMs('2025-12-31T23:00:00', 5, window)).toBe(window.startMs)
  })

  it('is null without checks, and the window start for an older server', () => {
    expect(checkedFromMs(undefined, 0, window)).toBeNull()
    expect(checkedFromMs(undefined, 5, window)).toBe(window.startMs)
    expect(checkedFromMs(undefined, undefined, window)).toBe(window.startMs)
  })
})

describe('downFractionColor', () => {
  it('goes from light yellow to dark red', () => {
    expect(downFractionColor(0)).toBe('#fef9c3')
    expect(downFractionColor(0.5)).toBe('#f97316')
    expect(downFractionColor(1)).toBe('#7f1d1d')
    expect(downFractionColor(0.01)).not.toBe(downFractionColor(0.02))
  })
})
