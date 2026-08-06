/**
 * Refcounted page-scroll lock shared by every modal surface (Dialog, Drawer, and
 * anything built on them such as ConfirmDialog).
 *
 * Why a shared refcount rather than each component writing
 * `document.body.style.overflow` directly:
 *
 *  - **Nesting.** ConfirmDialog is a Dialog and is deliberately opened from
 *    inside a Drawer. With direct writes, closing the inner one cleared the lock
 *    while the outer was still open, so the page behind a still-open drawer
 *    became scrollable.
 *  - **Unmount.** A modal that is unmounted instead of closed — an expiring
 *    session pushes /login from request.js while an edit drawer is open, a
 *    notification click navigates away — never ran its close branch, leaving the
 *    page permanently unscrollable until a reload.
 *
 * The original inline style is captured on the first lock and restored on the
 * last release, so a page that legitimately sets its own overflow keeps it.
 */
let locks = 0
let previousOverflow = ''

export function lockBodyScroll() {
  if (locks === 0) {
    previousOverflow = document.body.style.overflow
    document.body.style.overflow = 'hidden'
  }
  locks++
}

export function unlockBodyScroll() {
  if (locks === 0) return
  locks--
  if (locks === 0) {
    document.body.style.overflow = previousOverflow
    previousOverflow = ''
  }
}

/** Test seam: current depth. Not used by production code. */
export function bodyScrollLockCount() {
  return locks
}
