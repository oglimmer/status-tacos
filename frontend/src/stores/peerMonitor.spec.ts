import { describe, it, expect } from 'vitest'
import { isPeerMonitor } from './monitors'

describe('isPeerMonitor', () => {
  const heartbeat = { tenantId: 7, url: 'https://b.example.com/api/peer/heartbeat/' }

  it('is the heartbeat monitor in the peer tenant', () => {
    expect(isPeerMonitor(heartbeat, 7)).toBe(true)
  })

  it('needs the peer tenant', () => {
    expect(isPeerMonitor(heartbeat, null)).toBe(false)
    expect(isPeerMonitor(heartbeat, 3)).toBe(false)
  })

  it('needs the heartbeat URL', () => {
    expect(isPeerMonitor({ tenantId: 7, url: 'https://b.example.com/api/health' }, 7)).toBe(false)
    expect(isPeerMonitor({ tenantId: 7, url: 'not a url' }, 7)).toBe(false)
  })
})
