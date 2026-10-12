import { describe, it, expect } from 'vitest'
import { mount } from '@vue/test-utils'
import MonitorSortSelect from './MonitorSortSelect.vue'

describe('MonitorSortSelect', () => {
  it('starts a new key in its usual direction', async () => {
    const wrapper = mount(MonitorSortSelect, {
      props: { modelValue: { key: 'name', direction: 'asc' } }
    })

    await wrapper.find('select').setValue('lastDown')
    await wrapper.find('select').setValue('tenant')

    expect(wrapper.emitted('update:modelValue')).toEqual([
      [{ key: 'lastDown', direction: 'desc' }],
      [{ key: 'tenant', direction: 'asc' }]
    ])
  })

  it('toggles the direction and keeps the key', async () => {
    const wrapper = mount(MonitorSortSelect, {
      props: { modelValue: { key: 'created', direction: 'desc' } }
    })

    expect(wrapper.find('button').text()).toBe('↓ Desc')
    await wrapper.find('button').trigger('click')

    expect(wrapper.emitted('update:modelValue')).toEqual([[{ key: 'created', direction: 'asc' }]])
  })
})
