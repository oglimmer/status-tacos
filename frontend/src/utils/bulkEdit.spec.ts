import { describe, it, expect } from 'vitest'
import { runWithConcurrency, toBulkRequest } from './bulkEdit'
import type { MonitorResponse } from '../stores/monitors'

const monitor = {
  id: 7,
  name: 'API',
  url: 'https://api.example.com',
  tenantId: 3,
  tenant: { id: 3 },
  state: 'ACTIVE',
  statusCodeRegex: '^2\\d{2}$',
  prometheusKey: 'up',
  prometheusMinValue: 1,
  checkInterval: 60,
  alertingThreshold: 120
} as unknown as MonitorResponse

describe('toBulkRequest', () => {
  it('keeps the other fields and sets only the changed ones', () => {
    const request = toBulkRequest(monitor, { checkInterval: 300, state: 'SILENT' })

    expect(request).toMatchObject({
      name: 'API',
      url: 'https://api.example.com',
      tenantId: 3,
      state: 'SILENT',
      statusCodeRegex: '^2\\d{2}$',
      prometheusKey: 'up',
      prometheusMinValue: 1,
      checkInterval: 300,
      connectTimeout: 5,
      alertingThreshold: 120,
      useSystemDns: false,
      showOnPublicPage: false
    })
  })
})

describe('runWithConcurrency', () => {
  it('runs all items, never more than the limit at once, and goes on after a failure', async () => {
    let running = 0
    let maxRunning = 0
    const done: number[] = []

    await runWithConcurrency([1, 2, 3, 4, 5, 6, 7], 3, async (n) => {
      running++
      maxRunning = Math.max(maxRunning, running)
      await new Promise((resolve) => setTimeout(resolve, 5))
      running--
      if (n === 2) throw new Error('fail')
      done.push(n)
    })

    expect(maxRunning).toBe(3)
    expect(done.sort()).toEqual([1, 3, 4, 5, 6, 7])
  })

  it('starts no new items after abort', async () => {
    const controller = new AbortController()
    const started: number[] = []

    await runWithConcurrency([1, 2, 3, 4, 5], 2, async (n) => {
      started.push(n)
      if (n === 2) controller.abort()
      await Promise.resolve()
    }, controller.signal)

    expect(started).toEqual([1, 2])
  })
})
