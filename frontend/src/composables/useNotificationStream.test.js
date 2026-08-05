import { describe, it, expect, vi, beforeEach, afterEach } from 'vitest'
import { defineComponent, h } from 'vue'
import { mount } from '@vue/test-utils'
import { createPinia, setActivePinia, defineStore } from 'pinia'

// The ticket request is the awaited step we need to control the timing of.
let ticketResolve
vi.mock('@/services/notification', () => ({
  getSseTicketApi: vi.fn(() => new Promise((r) => { ticketResolve = r }))
}))

vi.mock('@/stores/auth', () => {
  const useAuthStore = defineStore('auth', {
    state: () => ({ accessToken: 'tok' }),
    getters: { isAuthenticated: (s) => !!s.accessToken }
  })
  return { useAuthStore }
})

vi.mock('@/stores/notification', () => {
  const useNotificationStore = defineStore('notification', {
    state: () => ({ unread: 0 }),
    actions: { fetchUnread() {}, setUnread(n) { this.unread = Number(n) || 0 } }
  })
  return { useNotificationStore }
})

import { useNotificationStream } from '@/composables/useNotificationStream'
import { getSseTicketApi } from '@/services/notification'

/**
 * `connect()` checks `stopped` before awaiting the SSE ticket — but the component
 * can unmount DURING that await (route change, logout). `onUnmounted` then closes
 * whatever `es` holds, which at that moment is still null, and the continuation
 * afterwards used to build an `EventSource` anyway. Nothing held a reference to
 * it, so nothing could ever close it: the browser kept the connection open and
 * the server-side `SseEmitter` stayed parked until its 30-minute timeout. Repeat
 * per navigation and the leaks accumulate.
 */
describe('useNotificationStream', () => {
  let created

  const Host = defineComponent({
    setup() {
      useNotificationStream()
      return () => h('div')
    }
  })

  beforeEach(() => {
    setActivePinia(createPinia())
    ticketResolve = undefined
    created = []
    vi.stubGlobal('EventSource', class {
      constructor(url) {
        this.url = url
        this.closed = false
        created.push(this)
      }
      addEventListener() {}
      close() { this.closed = true }
    })
  })

  afterEach(() => {
    vi.unstubAllGlobals()
    vi.clearAllMocks()
  })

  it('does not open a stream when unmounted while the ticket is in flight', async () => {
    const wrapper = mount(Host)
    expect(getSseTicketApi).toHaveBeenCalled()

    wrapper.unmount()                                   // navigate away mid-await
    ticketResolve({ data: { code: 0, data: 'TICKET-1' } })
    await Promise.resolve()
    await Promise.resolve()

    expect(created).toHaveLength(0)
  })

  it('opens a stream with the ticket when still mounted', async () => {
    const wrapper = mount(Host)

    ticketResolve({ data: { code: 0, data: 'TICKET-2' } })
    await Promise.resolve()
    await Promise.resolve()

    expect(created).toHaveLength(1)
    expect(created[0].url).toContain('ticket=TICKET-2')

    wrapper.unmount()
    expect(created[0].closed).toBe(true)                // and it IS closable
  })
})
