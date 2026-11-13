import { describe, it, expect, vi } from 'vitest'
import { flushPromises, mount } from '@vue/test-utils'
import BulkEditMonitorsModal from './BulkEditMonitorsModal.vue'
import { ApiError } from '../services/apiError'
import type { MonitorRequest, MonitorResponse } from '../stores/monitors'

const updateMonitor = vi.fn()

vi.mock('../stores/monitors', () => ({
  useMonitorsStore: () => ({
    getMonitorById: () => undefined,
    updateMonitor
  })
}))

const makeMonitor = (id: number, checkInterval: number) =>
  ({
    id,
    name: `Monitor ${id}`,
    url: `https://m${id}.example.com`,
    tenantId: 1,
    tenant: { id: 1 },
    state: 'ACTIVE',
    statusCodeRegex: '^2\\d{2}$',
    checkInterval,
    alertingThreshold: 30
  }) as unknown as MonitorResponse

describe('BulkEditMonitorsModal', () => {
  it('shows the current values, sends only the ticked change, and lists the failed monitor', async () => {
    updateMonitor.mockReset().mockImplementation(async (id: number, data: MonitorRequest) => {
      if (id === 2) throw new ApiError('Some of the data is not valid.', 400)
      return { ...makeMonitor(id, 0), ...data }
    })
    const monitors = [makeMonitor(1, 15), makeMonitor(2, 15), makeMonitor(3, 60)]
    const wrapper = mount(BulkEditMonitorsModal, { props: { monitors }, attachTo: document.body })

    expect(document.body.textContent).toContain('Mixed: 15s (2) · 60s (1)')

    const checkInterval = document.querySelectorAll<HTMLInputElement>('.field-toggle input')[1]!
    checkInterval.click()
    await flushPromises()
    const input = document.querySelector<HTMLInputElement>('.input-row input')!
    input.value = '300'
    input.dispatchEvent(new Event('input'))
    document.querySelector<HTMLFormElement>('.bulk-form')!.dispatchEvent(new Event('submit'))
    await flushPromises()

    expect(updateMonitor).toHaveBeenCalledTimes(3)
    for (const [, data] of updateMonitor.mock.calls) {
      expect(data.checkInterval).toBe(300)
      expect(data.state).toBe('ACTIVE')
    }
    const text = document.body.textContent
    expect(text).toContain('2 of 3 updated')
    expect(text).toContain('1 failed')
    expect(text).toContain('Some of the data is not valid.')
    expect(text).toContain('Retry 1')
    expect(wrapper.emitted('finished')).toHaveLength(1)

    wrapper.unmount()
  })

  it('keeps the environment on other changes, and sets it on many monitors', async () => {
    updateMonitor.mockReset().mockImplementation(async (id: number, data: MonitorRequest) => ({ ...makeMonitor(id, 0), ...data }))
    const monitors = [
      { ...makeMonitor(1, 15), environment: 'PROD' },
      { ...makeMonitor(2, 15), environment: 'DEV' },
      makeMonitor(3, 15)
    ] as MonitorResponse[]

    // A change of another field keeps the environment of each monitor
    let wrapper = mount(BulkEditMonitorsModal, { props: { monitors }, attachTo: document.body })
    expect(document.body.textContent).toContain('Mixed: Production (1) · Development (1) · Not set (1)')
    document.querySelectorAll<HTMLInputElement>('.field-toggle input')[1]!.click()
    await flushPromises()
    document.querySelector<HTMLFormElement>('.bulk-form')!.dispatchEvent(new Event('submit'))
    await flushPromises()
    expect(updateMonitor.mock.calls.map(([, data]) => data.environment)).toEqual(['PROD', 'DEV', null])
    wrapper.unmount()

    // Set the environment of all
    updateMonitor.mockClear()
    wrapper = mount(BulkEditMonitorsModal, { props: { monitors }, attachTo: document.body })
    const toggles = document.querySelectorAll<HTMLInputElement>('.field-toggle input')
    toggles[toggles.length - 1]!.click()
    await flushPromises()
    const stage = [...document.querySelectorAll<HTMLLabelElement>('.segmented label')].find((l) =>
      l.textContent?.includes('Stage')
    )!
    stage.querySelector('input')!.click()
    await flushPromises()
    document.querySelector<HTMLFormElement>('.bulk-form')!.dispatchEvent(new Event('submit'))
    await flushPromises()
    expect(updateMonitor.mock.calls.map(([, data]) => data.environment)).toEqual(['STAGE', 'STAGE', 'STAGE'])
    expect(document.body.textContent).toContain('Environment → Stage')
    wrapper.unmount()
  })
})
