import { beforeAll, describe, expect, it } from 'vitest'
import { flushPromises, mount } from '@vue/test-utils'
import ConfirmDialog from './ConfirmDialog.vue'
import { askConfirm } from '../services/confirm'

beforeAll(() => {
  // jsdom has no showModal() and close().
  HTMLDialogElement.prototype.showModal = function () {
    this.open = true
  }
  HTMLDialogElement.prototype.close = function () {
    this.open = false
    this.dispatchEvent(new Event('close'))
  }
})

describe('ConfirmDialog', () => {
  it('shows the question and answers true for the action button', async () => {
    const wrapper = mount(ConfirmDialog, { attachTo: document.body })
    const answer = askConfirm({
      title: 'Delete shop?',
      message: 'You cannot undo this.',
      confirmLabel: 'Delete monitor',
      danger: true,
    })
    await flushPromises()

    expect(wrapper.find('dialog').element.open).toBe(true)
    expect(wrapper.text()).toContain('Delete shop?')
    const buttons = wrapper.findAll('button')
    expect(buttons[0]!.text()).toBe('Cancel')
    expect(document.activeElement).toBe(buttons[0]!.element)
    expect(buttons[1]!.classes()).toContain('btn-danger')

    await buttons[1]!.trigger('click')
    await expect(answer).resolves.toBe(true)
    await flushPromises()
    expect(wrapper.find('dialog').element.open).toBe(false)
    wrapper.unmount()
  })

  it('answers false for Cancel', async () => {
    const wrapper = mount(ConfirmDialog, { attachTo: document.body })
    const answer = askConfirm({ title: 'Remove?', confirmLabel: 'Remove' })
    await flushPromises()
    await wrapper.findAll('button')[0]!.trigger('click')
    await expect(answer).resolves.toBe(false)
    wrapper.unmount()
  })
})
