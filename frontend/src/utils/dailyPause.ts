/**
 * Daily pause of a monitor: no checks from `from` to `until` (HH:mm) each day, in an IANA time
 * zone. Until before from: the pause goes past midnight. The backend has the same rule.
 */

export interface DailyPauseSettings {
  from: string
  until: string
  timezone: string
}

/** The pause of the monitor, or null when it has none. Without a time zone the backend uses UTC. */
export function pauseOf(monitor: {
  dailyPauseFrom?: string | null
  dailyPauseUntil?: string | null
  dailyPauseTimezone?: string | null
}): DailyPauseSettings | null {
  if (!monitor.dailyPauseFrom || !monitor.dailyPauseUntil) return null
  return { from: monitor.dailyPauseFrom, until: monitor.dailyPauseUntil, timezone: monitor.dailyPauseTimezone || 'UTC' }
}

/** The time zone of the browser, for example Europe/Berlin. */
export function browserTimeZone(): string {
  return Intl.DateTimeFormat().resolvedOptions().timeZone || 'UTC'
}

/** All IANA time zones the browser knows, with UTC first. */
export function timeZones(): string[] {
  const intl = Intl as unknown as { supportedValuesOf?: (key: string) => string[] }
  const zones = intl.supportedValuesOf?.('timeZone') ?? []
  return ['UTC', ...zones.filter((z) => z !== 'UTC')]
}

/** The wall-clock time HH:mm in the time zone at the given instant. */
export function wallTime(timezone: string, at: Date = new Date()): string {
  const parts = new Intl.DateTimeFormat('en-GB', {
    timeZone: timezone,
    hour: '2-digit',
    minute: '2-digit',
    hourCycle: 'h23'
  }).formatToParts(at)
  const hour = parts.find((p) => p.type === 'hour')?.value ?? '00'
  const minute = parts.find((p) => p.type === 'minute')?.value ?? '00'
  return `${hour}:${minute}`
}

/** True when the instant is in the pause. From is inclusive, until is exclusive. */
export function isPaused(pause: DailyPauseSettings, at: Date = new Date()): boolean {
  const now = wallTime(pause.timezone, at)
  if (pause.from < pause.until) return now >= pause.from && now < pause.until
  return now >= pause.from || now < pause.until
}

/** Minutes from UTC of the time zone at the instant (Berlin in winter: 60). */
function offsetMinutes(timezone: string, at: Date): number {
  const parts = new Intl.DateTimeFormat('en-US', {
    timeZone: timezone,
    year: 'numeric',
    month: '2-digit',
    day: '2-digit',
    hour: '2-digit',
    minute: '2-digit',
    hourCycle: 'h23'
  }).formatToParts(at)
  const get = (type: string) => Number(parts.find((p) => p.type === type)?.value)
  const asUtc = Date.UTC(get('year'), get('month') - 1, get('day'), get('hour'), get('minute'))
  return Math.round((asUtc - Math.floor(at.getTime() / 60_000) * 60_000) / 60_000)
}

/** The instant of the wall-clock time HH:mm today in the time zone. */
function instantOf(time: string, timezone: string, today: Date): Date {
  const [h, m] = time.split(':').map(Number)
  const dayParts = new Intl.DateTimeFormat('en-US', {
    timeZone: timezone,
    year: 'numeric',
    month: '2-digit',
    day: '2-digit'
  }).formatToParts(today)
  const get = (type: string) => Number(dayParts.find((p) => p.type === type)?.value)
  const guess = Date.UTC(get('year'), get('month') - 1, get('day'), h ?? 0, m ?? 0)
  let result = guess - offsetMinutes(timezone, new Date(guess)) * 60_000
  // Second pass: the offset at the result can differ on the day of the daylight saving change
  result = guess - offsetMinutes(timezone, new Date(result)) * 60_000
  return new Date(result)
}

/** HH:mm in the browser time zone. */
function localTime(d: Date): string {
  return d.toLocaleTimeString(undefined, { hour: '2-digit', minute: '2-digit' })
}

/** "02:00–02:30 Europe/Berlin". */
export function formatPause(pause: DailyPauseSettings): string {
  return `${pause.from}–${pause.until} ${pause.timezone}`
}

/**
 * The same pause today in the time zone of the browser, for example "03:00–03:30". Null when the
 * browser has the time zone of the pause, or the offsets are the same.
 */
export function formatPauseInBrowserZone(pause: DailyPauseSettings, today: Date = new Date()): string | null {
  if (pause.timezone === browserTimeZone()) return null
  const from = instantOf(pause.from, pause.timezone, today)
  const until = instantOf(pause.until, pause.timezone, today)
  const text = `${localTime(from)}–${localTime(until)}`
  return text === `${pause.from}–${pause.until}` ? null : text
}
