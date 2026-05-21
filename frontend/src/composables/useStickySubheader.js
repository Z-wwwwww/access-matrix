import { ref, computed, watch, onScopeDispose } from 'vue'

/**
 * Shared registry for pages that pin a sticky sub-header directly below AppTabBar.
 * While any is registered, AppTabBar suppresses its bottom shadow (the sub-header
 * already acts as the visual boundary; stacking shadows looks noisy).
 */
const stickyCount = ref(0)

export const hasStickySubheader = computed(() => stickyCount.value > 0)

export function useStickySubheader(activeRef) {
  let registered = false
  const stop = watch(
    activeRef,
    (v) => {
      if (v && !registered) {
        stickyCount.value++
        registered = true
      } else if (!v && registered) {
        stickyCount.value--
        registered = false
      }
    },
    { immediate: true }
  )
  onScopeDispose(() => {
    stop()
    if (registered) {
      stickyCount.value--
      registered = false
    }
  })
}
