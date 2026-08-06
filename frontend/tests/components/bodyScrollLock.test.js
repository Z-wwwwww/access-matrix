import { describe, it, expect, beforeEach } from 'vitest'
import { mount } from '@vue/test-utils'
import Dialog from '@/components/ui/Dialog.vue'
import Drawer from '@/components/ui/Drawer.vue'

/**
 * Dialog and Drawer both lock page scrolling by writing
 * `document.body.style.overflow = 'hidden'` while open.
 *
 * Three ways that went wrong, all of which strand the page:
 *
 *  1. **Unmounted while open.** The lock was only released by the open→false
 *     transition, so a modal that gets unmounted instead of closed never
 *     released it and the page could not scroll again until a reload. That is
 *     not exotic here: request.js pushes /login from ANY 401, so an expiring
 *     session while an edit drawer is open unmounts it mid-flight; the same goes
 *     for a notification click or a tab switch.
 *
 *  2. **Nested modals.** ConfirmDialog is a Dialog, and the app deliberately
 *     opens it from inside a Drawer (features-and-tests: "从抽屉/Dialog 内部触发
 *     删除"). Closing the inner one wrote overflow = '' while the outer was still
 *     open, so the page behind a still-open drawer became scrollable.
 *
 *  3. **Mounted already-open.** `watch` without `immediate` does not fire for
 *     the initial value, so a modal rendered with open=true from the start never
 *     locked at all.
 *
 * The fix is one shared refcounted lock; these tests pin its observable effect.
 */
describe('body scroll lock', () => {
  beforeEach(() => {
    document.body.style.overflow = ''
  })

  it('locks while open and releases on close', async () => {
    const w = mount(Dialog, { props: { open: false } })
    expect(document.body.style.overflow).toBe('')

    await w.setProps({ open: true })
    expect(document.body.style.overflow).toBe('hidden')

    await w.setProps({ open: false })
    expect(document.body.style.overflow).toBe('')
    w.unmount()
  })

  it('locks when mounted already open', () => {
    const w = mount(Dialog, { props: { open: true } })
    expect(document.body.style.overflow, 'a modal rendered open from the start must lock too')
      .toBe('hidden')
    w.unmount()
  })

  it('releases the lock when unmounted while still open', () => {
    const w = mount(Drawer, { props: { open: true } })
    expect(document.body.style.overflow).toBe('hidden')

    w.unmount()
    expect(document.body.style.overflow, 'unmounting an open modal must not strand the page')
      .toBe('')
  })

  it('a nested modal closing does not unlock the one still open', async () => {
    const outer = mount(Drawer, { props: { open: true } })
    const inner = mount(Dialog, { props: { open: true } })
    expect(document.body.style.overflow).toBe('hidden')

    await inner.setProps({ open: false })
    expect(document.body.style.overflow, 'the outer drawer is still open')
      .toBe('hidden')

    await outer.setProps({ open: false })
    expect(document.body.style.overflow).toBe('')
    inner.unmount()
    outer.unmount()
  })
})
