import { describe, it, expect } from 'vitest'
import { mount } from '@vue/test-utils'
import AlertContactCard from './AlertContactCard.vue'
import type { AlertContactResponse } from '../stores/alertContacts'

const baseContact: AlertContactResponse = {
  id: 1,
  type: 'EMAIL',
  value: 'alerts@example.com',
  name: 'Primary Admin',
  isActive: true,
  tenant: {
    id: 1,
    name: 'Default',
    code: 'default',
    isActive: true,
    createdAt: '2026-01-01T00:00:00Z',
    updatedAt: '2026-01-01T00:00:00Z'
  },
  createdAt: '2026-01-01T00:00:00Z',
  updatedAt: '2026-01-01T00:00:00Z',
  allMonitors: true,
  monitors: []
}

const iosPushContact: AlertContactResponse = {
  ...baseContact,
  id: 2,
  type: 'IOS_PUSH',
  value: 'user:5',
  name: 'iOS push: ada@example.com',
  allMonitors: false,
  monitors: [{ id: 7, name: 'Shop' }],
  owner: { id: 5, email: 'ada@example.com', name: 'Ada Lovelace' }
}

const buttonLabels = (wrapper: ReturnType<typeof mount>) =>
  wrapper.findAll('button').map(button => button.text())

describe('AlertContactCard', () => {
  it('shows edit, test, toggle and delete buttons for regular contacts', () => {
    for (const type of ['EMAIL', 'HTTP', 'TEAMS'] as const) {
      const wrapper = mount(AlertContactCard, { props: { contact: { ...baseContact, type } } })

      expect(buttonLabels(wrapper)).toEqual(['Edit', 'Test', 'Disable', 'Delete'])
      expect(wrapper.text()).toContain('alerts@example.com')
      expect(wrapper.text()).not.toContain('Managed in the iOS app')
    }
  })

  it('shows IOS_PUSH contacts read-only with owner, scope and note', () => {
    const wrapper = mount(AlertContactCard, { props: { contact: iosPushContact } })

    expect(wrapper.findAll('button')).toHaveLength(0)
    expect(wrapper.find('.contact-type').text()).toBe('iOS Push')
    expect(wrapper.find('.contact-type').attributes('data-type')).toBe('IOS_PUSH')
    expect(wrapper.find('.contact-owner').text()).toBe('Ada Lovelace (ada@example.com)')
    expect(wrapper.find('.monitor-scope').text()).toBe('Shop')
    expect(wrapper.text()).toContain('Active')
    expect(wrapper.text()).toContain('Managed in the iOS app by its owner.')
    expect(wrapper.text()).not.toContain('user:5')
  })

  it('shows only the email when the IOS_PUSH owner has no name', () => {
    const wrapper = mount(AlertContactCard, {
      props: {
        contact: {
          ...iosPushContact,
          isActive: false,
          owner: { id: 5, email: 'ada@example.com', name: null }
        }
      }
    })

    expect(wrapper.find('.contact-owner').text()).toBe('ada@example.com')
    expect(wrapper.text()).toContain('Inactive')
  })
})
