import { test, expect, env } from './fixtures.js'

/**
 * Tenant-aware login flow (OIDC):
 *
 *   1. The login page reads the tenant from localStorage.tenant_id
 *      (set by the collapsible "tenant" field / subdomain / sticky value)
 *   2. In OIDC mode the SPA brokers login through Keycloak — it auto-redirects
 *      to that tenant's realm authorize endpoint: /realms/{tenant}/protocol/
 *      openid-connect/auth. Credentials are entered on KC's hosted page; the
 *      SPA never POSTs /auth/login (that was the legacy jwt-mode flow).
 *
 * The realm in the authorize URL IS the multi-tenant propagation. If it
 * regresses to the wrong realm, different tenants collide — the exact failure
 * mode V20 was designed to prevent.
 */

test('login redirects to the tenant realm authorize endpoint (OIDC)', async ({ page, stack }) => {
  await page.addInitScript((tenant) => {
    window.localStorage.setItem('tenant_id', tenant)
  }, env.TENANT)

  // Arm the matcher BEFORE goto: the SPA auto-redirects to Keycloak shortly
  // after the login page mounts, so the authorize request can fire on its own.
  const authReqPromise = page.waitForRequest(
    (req) => req.url().includes('/protocol/openid-connect/auth'),
    { timeout: 20_000 }
  )

  await page.goto('/login')

  const authReq = await authReqPromise
  // The realm segment carries the tenant — this is the propagation under test.
  expect(authReq.url()).toContain(`/realms/${env.TENANT}/`)
})

test('login form has a tenant field (collapsible)', async ({ page, stack }) => {
  // V20 made the tenant input visible — even if it starts collapsed.
  // Catches a regression where the tenant UI gets removed entirely
  // (multi-tenant deployment requires the user to be able to set it).
  await page.goto('/login')
  const tenantToggle = page.getByText(/tenant|テナント|租户/i)
  await expect(tenantToggle.first()).toBeVisible({ timeout: 10_000 })
})
