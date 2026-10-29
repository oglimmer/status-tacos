import { describe, it, expect, beforeEach, vi } from 'vitest'
import { flushPromises, mount } from '@vue/test-utils'
import ApiTokens from './ApiTokens.vue'
import { apiService, type ApiToken } from '../services/api'

vi.mock('../services/api', () => ({
  apiService: {
    getApiTokens: vi.fn(),
    createApiToken: vi.fn(),
    deleteApiToken: vi.fn()
  }
}))

const usedToken: ApiToken = {
  id: 1,
  name: 'Grafana',
  prefix: 'stt_ab12',
  createdAt: '2026-10-01T10:00:00',
  lastUsedAt: '2026-10-20T08:30:00'
}

const unusedToken: ApiToken = {
  id: 2,
  name: 'Laptop',
  prefix: 'stt_cd34',
  createdAt: '2026-10-02T10:00:00',
  lastUsedAt: null
}

describe('ApiTokens', () => {
  beforeEach(() => {
    vi.mocked(apiService.getApiTokens).mockReset().mockResolvedValue([usedToken, unusedToken])
    vi.mocked(apiService.createApiToken).mockReset()
    vi.mocked(apiService.deleteApiToken).mockReset().mockResolvedValue()
  })

  it('lists the tokens with prefix and last use', async () => {
    const wrapper = mount(ApiTokens)
    await flushPromises()

    const rows = wrapper.findAll('.token-list li')
    expect(rows).toHaveLength(2)
    expect(rows[0]!.text()).toContain('Grafana')
    expect(rows[0]!.find('.token-prefix').text()).toBe('stt_ab12…')
    expect(rows[0]!.text()).not.toContain('never')
    expect(rows[1]!.text()).toContain('Last used never')
  })

  it('shows the data source URL for Grafana', async () => {
    const wrapper = mount(ApiTokens)
    await flushPromises()

    expect(wrapper.find('.grafana').text()).toContain(`${window.location.origin}/api/prom`)
  })

  it('shows a new token once, with a note that it is not shown again', async () => {
    vi.mocked(apiService.createApiToken).mockResolvedValue({
      id: 3,
      name: 'CI',
      prefix: 'stt_ef56',
      createdAt: '2026-10-29T12:00:00',
      token: 'stt_ef56secret'
    })
    const wrapper = mount(ApiTokens)
    await flushPromises()

    await wrapper.find('#api-token-name').setValue('  CI  ')
    await wrapper.find('form').trigger('submit')
    await flushPromises()

    expect(apiService.createApiToken).toHaveBeenCalledWith({ name: 'CI' })
    expect(wrapper.find('.token-value').text()).toBe('stt_ef56secret')
    expect(wrapper.find('.new-token').text()).toContain('It is not shown again')
    expect(wrapper.findAll('.token-list li')).toHaveLength(3)

    await wrapper.find('.new-token .link-btn').trigger('click')
    expect(wrapper.find('.new-token').exists()).toBe(false)
  })

  it('deletes a token only after confirmation', async () => {
    const confirmSpy = vi.spyOn(window, 'confirm').mockReturnValueOnce(false).mockReturnValueOnce(true)
    const wrapper = mount(ApiTokens)
    await flushPromises()

    await wrapper.findAll('.token-list li')[0]!.find('button').trigger('click')
    expect(apiService.deleteApiToken).not.toHaveBeenCalled()

    await wrapper.findAll('.token-list li')[0]!.find('button').trigger('click')
    await flushPromises()
    expect(apiService.deleteApiToken).toHaveBeenCalledWith(1)
    expect(wrapper.findAll('.token-list li')).toHaveLength(1)

    confirmSpy.mockRestore()
  })
})
