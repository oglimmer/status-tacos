import { describe, expect, it } from 'vitest'
import { answerConfirm, askConfirm, confirmRequest } from './confirm'

describe('confirm', () => {
  it('resolves with the answer of the user and closes', async () => {
    const answer = askConfirm({ title: 'Delete?', confirmLabel: 'Delete' })
    expect(confirmRequest.value?.title).toBe('Delete?')
    answerConfirm(true)
    await expect(answer).resolves.toBe(true)
    expect(confirmRequest.value).toBeNull()
  })

  it('cancels the open question when a new one comes', async () => {
    const first = askConfirm({ title: 'First?', confirmLabel: 'Yes' })
    const second = askConfirm({ title: 'Second?', confirmLabel: 'Yes' })
    await expect(first).resolves.toBe(false)
    answerConfirm(false)
    await expect(second).resolves.toBe(false)
  })
})
