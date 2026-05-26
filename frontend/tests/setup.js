// Vitest setup — runs once before all test files.
// Quiet noisy console output that some Vue runtime warnings produce in tests
// (component lifecycle errors are still surfaced via failed assertions).
import { config } from '@vue/test-utils'

// Make Pinia stores work in @vue/test-utils mounts without each test having
// to remember to install it.
import { createPinia, setActivePinia } from 'pinia'

beforeEach(() => {
  setActivePinia(createPinia())
})

config.global.plugins = config.global.plugins || []
