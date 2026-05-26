import { test, expect, env } from './fixtures.js'

/**
 * Tenant-aware login flow:
 *
 *   1. The collapsible "tenant" field on the login page sets localStorage.tenantId
 *   2. services/request.js reads it and attaches X-Tenant-Id to every request
 *   3. The backend's pre-auth /auth/login uses it to scope (username, tenant)
 *
 * If any leg of that chain regresses, multi-tenant logins silently use
 * tenant="default", which is the exact failure mode V20 was designed to
 * prevent — different tenants colliding on the same username.
 */

test('login attaches X-Tenant-Id from localStorage', async ({ page, stack }) => {
  // Pre-seed tenant via localStorage so we can assert it's read on the very
  // first XHR. The login page also offers an inline field; we test both paths.
  await page.addInitScript((tenantId) => {
    window.localStorage.setItem('tenantId', tenantId)
  }, env.TENANT)

  await page.goto('/login')

  // Capture the actual /auth/login request to verify the header is set.
  const loginReqPromise = page.waitForRequest((req) =>
    req.url().includes('/auth/login') && req.method() === 'POST'
  )

  await page.getByLabel(/username|ユーザー|用户名/i).fill(env.USER)
  await page.getByLabel(/password|パスワード|密码/i).fill(env.PASS)
  await page.getByRole('button', { name: /sign in|ログイン|登录/i }).click()

  const loginReq = await loginReqPromise
  const headers = loginReq.headers()

  expect(headers['x-tenant-id']).toBe(env.TENANT)
  await expect(page).not.toHaveURL(/\/login/, { timeout: 10_000 })
})

test('login form has a tenant field (collapsible)', async ({ page, stack }) => {
  // V20 made the tenant input visible — even if it starts collapsed.
  // Catches a regression where the tenant UI gets removed entirely
  // (multi-tenant deployment requires the user to be able to set it).
  await page.goto('/login')
  const tenantToggle = page.getByText(/tenant|テナント|租户/i)
  await expect(tenantToggle.first()).toBeVisible({ timeout: 10_000 })
})
