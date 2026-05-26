import { defineConfig, devices } from '@playwright/test'

/**
 * Playwright config for access-matrix e2e tests.
 *
 * The specs assume a running stack on the defaults below. If neither URL is
 * reachable, the tests skip via the {@code skipIfStackDown} fixture rather
 * than failing — that way contributors who only have the backend up (or
 * neither service up) can still run `npm test` without false reds.
 *
 * Override with env vars:
 *   E2E_BASE_URL   — frontend (default http://127.0.0.1:5273)
 *   E2E_API_URL    — backend  (default http://127.0.0.1:9135/api)
 *   E2E_USERNAME   — login user (default 'admin')
 *   E2E_PASSWORD   — login password (default 'admin123!A')
 *   E2E_TENANT     — tenant id sent in X-Tenant-Id header (default 'default')
 */
export default defineConfig({
  testDir: './tests/e2e',
  fullyParallel: true,
  forbidOnly: !!process.env.CI,
  retries: process.env.CI ? 2 : 0,
  workers: process.env.CI ? 1 : undefined,
  reporter: process.env.CI ? 'list' : 'html',
  use: {
    baseURL: process.env.E2E_BASE_URL || 'http://127.0.0.1:5273',
    trace: 'retain-on-failure',
    screenshot: 'only-on-failure'
  },
  projects: [
    {
      name: 'chromium',
      use: { ...devices['Desktop Chrome'] }
    }
  ]
})
