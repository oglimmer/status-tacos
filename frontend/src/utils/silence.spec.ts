import { describe, expect, it } from 'vitest'
import { SILENCE_OPTIONS, formatSilenceEnd, silenceEnd } from './silence'

describe('silence', () => {
  const now = new Date(2026, 10, 4, 10, 0) // Wed 4 Nov 2026, 10:00 local

  it('has only the four durations', () => {
    expect(SILENCE_OPTIONS.map((o) => o.code)).toEqual(['1h', '24h', '3d', '7d'])
  })

  it('adds the duration to now', () => {
    expect(silenceEnd('1h', now).getTime() - now.getTime()).toBe(3_600_000)
    expect(silenceEnd('7d', now).getTime() - now.getTime()).toBe(7 * 24 * 3_600_000)
  })

  it('names the day of the end', () => {
    expect(formatSilenceEnd(silenceEnd('1h', now), now)).toMatch(/^today /)
    expect(formatSilenceEnd(silenceEnd('24h', now), now)).toMatch(/^tomorrow /)
    expect(formatSilenceEnd(silenceEnd('3d', now), now)).not.toMatch(/today|tomorrow|Nov/)
    expect(formatSilenceEnd(silenceEnd('7d', now), now)).toMatch(/Nov/)
  })
})
