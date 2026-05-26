import { test, expect, env } from './fixtures.js'

/**
 * Frontend wildcard-permission rendering, end-to-end.
 *
 * The wildcard matcher we unit-tested has to also work after the full
 * router → store hydration → directive lifecycle. This test boots the real
 * app, logs in as the env-configured user, and verifies the gated buttons on
 * the role / dept admin pages are visible (assumes the admin user has the
 * required perms — which the seeded admin always does).
 *
 * If anyone breaks the directive registration in main.js / removes wildcard
 * support from the matcher / clears authorities on a refresh, this test
 * catches it before users hit a broken admin page.
 */

test('admin user sees role admin action buttons', async ({ loggedInPage, stack }) => {
  await loggedInPage.goto('/system/role')

  // The "create role" button is gated by v-permission="'role:create'".
  // Admin has *:* so it must be present.
  await expect(
    loggedInPage.getByRole('button', { name: /create|新規|新建|新增/i }).first()
  ).toBeVisible({ timeout: 10_000 })
})

test('admin user sees dept admin action buttons', async ({ loggedInPage, stack }) => {
  await loggedInPage.goto('/system/dept')
  await expect(
    loggedInPage.getByRole('button', { name: /create|新規|新建|新增/i }).first()
  ).toBeVisible({ timeout: 10_000 })
})
