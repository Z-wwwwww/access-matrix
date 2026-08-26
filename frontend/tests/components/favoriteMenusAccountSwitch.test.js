import { describe, it, expect, beforeEach, vi } from 'vitest'
import { defineComponent, nextTick } from 'vue'
import { mount } from '@vue/test-utils'
import { defineStore } from 'pinia'

// Node 22+ ships an experimental built-in localStorage gated behind
// `--localstorage-file`; without it `window.localStorage` is undefined and jsdom
// does not polyfill it. Same in-memory stub utils/tenant.test.js installs.
const _store = new Map()
const localStorageStub = {
  getItem:    (k) => (_store.has(k) ? _store.get(k) : null),
  setItem:    (k, v) => { _store.set(k, String(v)) },
  removeItem: (k) => { _store.delete(k) },
  clear:      () => { _store.clear() },
  key:        (i) => Array.from(_store.keys())[i] ?? null,
  get length() { return _store.size }
}
vi.stubGlobal('localStorage', localStorageStub)
Object.defineProperty(window, 'localStorage', { value: localStorageStub, configurable: true })

/**
 * Menu favorites must follow the signed-in account, even when the component
 * that first initialised them has since unmounted.
 *
 * The bug: `useFavoriteMenus()` lazily registers a `watch(() => auth.userId)` on
 * first use, guarded by a module-level `initialized` flag. That first use is
 * `AppSidebar.setup()`, so the watcher belonged to AppSidebar's effect scope and
 * Vue stopped it on unmount — while `initialized` stayed true, so it was never
 * re-created. AppSidebar unmounts in ordinary use: `AppLayout` renders it behind
 * `v-if="!hideSidebar"`, and signing out in password mode is a client-side
 * `router.replace('/login')` that tears the layout down with no page load.
 *
 * After that, an account switch on the same page load was invisible to this
 * module. Two things went wrong at once, and the second is the serious one:
 *   1. the incoming user saw the PREVIOUS user's starred menus;
 *   2. their first star toggle called `writeToStorage(currentUserId)` with the
 *      STALE id, writing the new user's set into
 *      `menu-favorites:<previous user id>` — silently overwriting a different
 *      account's saved favorites.
 *
 * The fix moves the watcher into a detached `effectScope`, so it outlives the
 * component that happened to trigger initialisation. The module keeps singleton
 * state, so each test re-imports it fresh.
 */

vi.mock('@/stores/auth', () => {
  const useAuthStore = defineStore('auth', {
    state: () => ({ userId: 'user-A' })
  })
  return { useAuthStore }
})

/** Stand-in for AppSidebar: a component whose setup() initialises the module. */
function harness(useFavoriteMenus) {
  return defineComponent({
    setup: () => ({ api: useFavoriteMenus() }),
    template: '<div />'
  })
}

async function freshModule() {
  vi.resetModules()
  const { useFavoriteMenus } = await import('@/composables/useFavoriteMenus')
  const { useAuthStore } = await import('@/stores/auth')
  return { useFavoriteMenus, useAuthStore }
}

/** Sign in as A, tear the layout down (sign-out), then sign in as `next`. */
async function switchAccountAcrossUnmount(useFavoriteMenus, useAuthStore, next) {
  const first = mount(harness(useFavoriteMenus))
  const sawA = first.vm.api.isFavorite('menu-1')
  first.unmount()

  useAuthStore().userId = next
  await nextTick()

  const second = mount(harness(useFavoriteMenus))
  await nextTick()
  return { sawA, second }
}

describe('useFavoriteMenus — account switching', () => {
  beforeEach(() => {
    localStorage.clear()
    localStorage.setItem('menu-favorites:user-A', JSON.stringify(['menu-1']))
    localStorage.setItem('menu-favorites:user-B', JSON.stringify(['menu-2']))
  })

  it('reloads for the new account after the initialising component unmounted', async () => {
    const { useFavoriteMenus, useAuthStore } = await freshModule()

    const { sawA, second } = await switchAccountAcrossUnmount(useFavoriteMenus, useAuthStore, 'user-B')

    expect(sawA).toBe(true)                                 // A's set was loaded to begin with
    expect(second.vm.api.isFavorite('menu-2')).toBe(true)   // B's set is now in force
    expect(second.vm.api.isFavorite('menu-1')).toBe(false)  // ...and A's is gone
    second.unmount()
  })

  it("a star toggled by the new account never lands in the old account's key", async () => {
    const { useFavoriteMenus, useAuthStore } = await freshModule()

    const { second } = await switchAccountAcrossUnmount(useFavoriteMenus, useAuthStore, 'user-B')
    second.vm.api.setFavorite('menu-9', true)

    expect(JSON.parse(localStorage.getItem('menu-favorites:user-B'))).toContain('menu-9')
    expect(JSON.parse(localStorage.getItem('menu-favorites:user-A'))).toEqual(['menu-1'])
    second.unmount()
  })

  it('clears the set when there is no account left (sign-out)', async () => {
    const { useFavoriteMenus, useAuthStore } = await freshModule()

    const { second } = await switchAccountAcrossUnmount(useFavoriteMenus, useAuthStore, '')

    expect(second.vm.api.isFavorite('menu-1')).toBe(false)
    second.unmount()
  })
})
