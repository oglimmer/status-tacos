import { describe, expect, it } from 'vitest'
import { isPaused, pauseOf, wallTime } from './dailyPause'

describe('dailyPause', () => {
  const berlin = { from: '02:00', until: '02:30', timezone: 'Europe/Berlin' }

  it('reads the wall-clock time of the time zone', () => {
    // Winter: UTC+1, summer: UTC+2
    expect(wallTime('Europe/Berlin', new Date('2026-11-05T01:10:00Z'))).toBe('02:10')
    expect(wallTime('Europe/Berlin', new Date('2026-07-05T01:10:00Z'))).toBe('03:10')
  })

  it('start is inside, end is outside', () => {
    expect(isPaused(berlin, new Date('2026-11-05T00:59:00Z'))).toBe(false)
    expect(isPaused(berlin, new Date('2026-11-05T01:00:00Z'))).toBe(true)
    expect(isPaused(berlin, new Date('2026-11-05T01:29:00Z'))).toBe(true)
    expect(isPaused(berlin, new Date('2026-11-05T01:30:00Z'))).toBe(false)
  })

  it('goes past midnight when the end is before the start', () => {
    const night = { from: '23:30', until: '00:30', timezone: 'UTC' }
    expect(isPaused(night, new Date('2026-11-05T23:45:00Z'))).toBe(true)
    expect(isPaused(night, new Date('2026-11-05T00:15:00Z'))).toBe(true)
    expect(isPaused(night, new Date('2026-11-05T12:00:00Z'))).toBe(false)
  })

  it('has no pause without both times, and UTC without a time zone', () => {
    expect(pauseOf({ dailyPauseFrom: '02:00' })).toBeNull()
    expect(pauseOf({ dailyPauseFrom: '02:00', dailyPauseUntil: '03:00' })?.timezone).toBe('UTC')
  })
})
