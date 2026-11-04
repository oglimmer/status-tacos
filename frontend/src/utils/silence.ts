import type { SilentFor } from '../stores/monitors'

const HOUR = 3_600_000

/** The only silence durations. Bar: the length on a log scale, so 1 hour is still visible. */
export const SILENCE_OPTIONS: readonly { code: SilentFor; label: string; ms: number; bar: number }[] = [
  { code: '1h', label: '1 hour', ms: HOUR, bar: 0.22 },
  { code: '24h', label: '24 hours', ms: 24 * HOUR, bar: 0.6 },
  { code: '3d', label: '3 days', ms: 72 * HOUR, bar: 0.8 },
  { code: '7d', label: '7 days', ms: 168 * HOUR, bar: 1 }
]

export function silenceEnd(code: SilentFor, now: Date = new Date()): Date {
  const option = SILENCE_OPTIONS.find((o) => o.code === code)
  return new Date(now.getTime() + (option?.ms ?? 24 * HOUR))
}

/** "today 14:00", "tomorrow 14:00", "Thu 14:00" in the next 6 days, else "Thu 12 Nov 14:00". */
export function formatSilenceEnd(end: Date, now: Date = new Date()): string {
  const time = end.toLocaleTimeString(undefined, { hour: '2-digit', minute: '2-digit' })
  const startOfDay = (d: Date) => new Date(d.getFullYear(), d.getMonth(), d.getDate()).getTime()
  const days = Math.round((startOfDay(end) - startOfDay(now)) / (24 * HOUR))
  if (days === 0) return `today ${time}`
  if (days === 1) return `tomorrow ${time}`
  if (days > 1 && days < 7) return `${end.toLocaleDateString(undefined, { weekday: 'short' })} ${time}`
  return `${end.toLocaleDateString(undefined, { weekday: 'short', day: 'numeric', month: 'short' })} ${time}`
}

/** The full date and time, for tooltips. */
export function formatSilenceEndLong(end: Date): string {
  return end.toLocaleString(undefined, { dateStyle: 'full', timeStyle: 'short' })
}
