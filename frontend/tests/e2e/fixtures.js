import { test as base, expect } from '@playwright/test'

const FRONTEND = process.env.E2E_BASE_URL || 'http://127.0.0.1:5273'
const BACKEND  = process.env.E2E_API_URL  || 'http://127.0.0.1:9135/api'
const USER     = process.env.E2E_USERNAME || 'admin'
const PASS     = process.env.E2E_PASSWORD || 'admin123!A'
const TENANT   = process.env.E2E_TENANT   || 'default'

/**
 * Cheap probe: does the backend respond? We don't want to fail every test
 * when only one service is up — we just skip. Override by setting
 * E2E_REQUIRE_STACK=1 to force a hard failure when the stack is down.
 */
async function isStackUp() {
  try {
    const res = await fetch(`${BACKEND}/actuator/health`, {
      signal: AbortSignal.timeout(2000)
    })
    return res.ok
  } catch {
    return false
  }
}

/**
 * Test fixtures:
 *   - `stack` — gated probe, skips the test if the stack isn't up
 *   - `loggedInPage` — page logged in as the env-configured admin user
 */
export const test = base.extend({
  stack: async ({}, use, testInfo) => {
    const up = await isStackUp()
    if (!up) {
      if (process.env.E2E_REQUIRE_STACK === '1') {
        throw new Error(`stack not reachable at ${BACKEND}/actuator/health — set E2E_REQUIRE_STACK=0 to skip`)
      }
      testInfo.skip(true, `backend not reachable at ${BACKEND}/actuator/health`)
    }
    await use({ frontend: FRONTEND, backend: BACKEND })
  },

  loggedInPage: async ({ page, stack }, use) => {
    // Seed the tenant via localStorage before the app boots, so the request
    // interceptor (services/request.js) attaches X-Tenant-Id from the first
    // request. Matches what the production tenant-aware login does.
    await page.addInitScript((tenantId) => {
      window.localStorage.setItem('tenantId', tenantId)
    }, TENANT)

    await page.goto('/login')
    await page.getByLabel(/username|ユーザー|用户名/i).fill(USER)
    await page.getByLabel(/password|パスワード|密码/i).fill(PASS)
    await page.getByRole('button', { name: /sign in|ログイン|登录/i }).click()
    await expect(page).not.toHaveURL(/\/login/, { timeout: 10_000 })

    await use(page)
  }
})

export { expect }
export const env = { FRONTEND, BACKEND, USER, PASS, TENANT }
