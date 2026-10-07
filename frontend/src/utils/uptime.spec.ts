import { describe, expect, it } from 'vitest'
import { formatUptime, parseUtc, timeWindow, timeframeToPeriod, uptimeLevel } from './uptime'

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
