import type { MonitorRequest, MonitorResponse } from '../stores/monitors'

/**
 * The settings that one change can set on many monitors. Name, URL, headers, body regex and
 * Prometheus are different for each endpoint, so they are not here. The tenant has its own move.
 */
export type BulkEditField =
  | 'state'
  | 'checkInterval'
  | 'connectTimeout'
  | 'alertingThreshold'
  | 'statusCodeRegex'
  | 'useSystemDns'
  | 'showOnPublicPage'

export type BulkPatch = Partial<Pick<MonitorRequest, BulkEditField>>

/** The full update request of the monitor with the changed fields. The PUT needs all fields. */
export function toBulkRequest(monitor: MonitorResponse, patch: BulkPatch): MonitorRequest {
  return {
    name: monitor.name,
    url: monitor.url,
    tenantId: monitor.tenant?.id ?? monitor.tenantId,
    state: monitor.state,
    httpHeaders: monitor.httpHeaders || {},
    statusCodeRegex: monitor.statusCodeRegex || '',
    responseBodyRegex: monitor.responseBodyRegex || '',
    prometheusKey: monitor.prometheusKey || '',
    prometheusMinValue: monitor.prometheusMinValue ?? undefined,
    prometheusMaxValue: monitor.prometheusMaxValue ?? undefined,
    checkInterval: monitor.checkInterval ?? 15,
    connectTimeout: monitor.connectTimeout ?? 5,
    useSystemDns: monitor.useSystemDns ?? false,
    alertingThreshold: monitor.alertingThreshold,
    showOnPublicPage: monitor.showOnPublicPage ?? false,
    ...patch
  }
}

/**
 * Runs the worker for each item, with at most `limit` at the same time. A failed item does not stop
 * the others. After abort, items that did not start yet do not start.
 */
export async function runWithConcurrency<T>(
  items: readonly T[],
  limit: number,
  worker: (item: T) => Promise<void>,
  signal?: AbortSignal
): Promise<void> {
  let next = 0
  const lane = async () => {
    while (next < items.length && !signal?.aborted) {
      const item = items[next++] as T
      try {
        await worker(item)
      } catch {
        // The worker records its own errors
      }
    }
  }
  await Promise.all(Array.from({ length: Math.max(1, Math.min(limit, items.length)) }, lane))
}
