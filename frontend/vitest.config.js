import { defineConfig } from 'vitest/config'
import vue from '@vitejs/plugin-vue'
import { fileURLToPath, URL } from 'node:url'

export default defineConfig({
  plugins: [vue()],
  resolve: {
    alias: {
      '@': fileURLToPath(new URL('./src', import.meta.url))
    }
  },
  test: {
    environment: 'jsdom',
    globals: true,
    include: ['src/**/*.{test,spec}.{js,jsx,ts,tsx}', 'tests/components/**/*.{test,spec}.{js,ts}'],
    // tests/e2e/ holds Playwright specs (run with `npm run test:e2e`); excluding
    // the dir keeps Vitest from picking them up and failing on the fixture API.
    exclude: ['node_modules', 'dist', 'tests/e2e/**'],
    setupFiles: ['./tests/setup.js']
  }
})
