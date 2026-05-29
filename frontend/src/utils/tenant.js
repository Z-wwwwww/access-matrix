/**
 * Tenant resolution — single source of truth for "which tenant is this browser
 * talking to right now". Used by both the OIDC config builder (to compute the
 * Keycloak realm URL) and the axios interceptor (to set X-Tenant-Id on every
 * request), so the two can never disagree.
 *
 * Resolution priority (highest wins):
 *
 *   1. `?tenant=<name>` query string — explicit override. Also stickied to
 *      localStorage so subsequent navigations on the same browser remember
 *      it. Mainly a dev convenience on localhost where there's no subdomain.
 *
 *   2. Subdomain of `window.location.hostname` — production routing. The
 *      first label of the host (e.g. "acme" in "acme.access-matrix.com")
 *      becomes the tenant. Reserved labels ("www", "app", "api", ...) and
 *      bare hostnames (apex, IPs, "localhost") fall through.
 *
 *   3. localStorage `tenant_id` — sticky value from a previous explicit pick.
 *      Lets a dev keep "acme" across reloads of `localhost:5273?tenant=acme`.
 *
 *   4. `"demo"` — final fallback. This is the conventional dev / QA tenant
 *      (the realm seeded by demo-realm.json). Production deploys should
 *      reach this branch only via misconfiguration; the subdomain path
 *      is the authoritative routing.
 *
 * Value is RFC 1035 label-shaped (lowercase, digits, hyphen; 1-63 chars,
 * must start with alphanumeric). Anything else is rejected and we fall
 * through to the next source. This matches the constraint Keycloak places
 * on realm names and the convention `tenant_id == realm_name` that the
 * backend's MyBatis tenant interceptor relies on.
 *
 * Cached for the lifetime of the page so we don't repeatedly parse the URL
 * or hit localStorage. Tenant doesn't change without a navigation, and a
 * navigation is a fresh JS context, so caching is safe.
 */

const TENANT_LS_KEY = 'tenant_id'
const TENANT_QS_KEY = 'tenant'
export const DEFAULT_TENANT = 'demo'

/**
 * Platform-ops realm — guaranteed to exist in every deployment (it hosts
 * the `ops` super-user and platform tenant management). Used as the
 * tenant-independent reachability probe target: if `system` doesn't
 * answer, KC itself is down; if it does, the user's stored tenant is
 * the thing that's broken (most commonly because the realm was deleted).
 * Kept separate from DEFAULT_TENANT (which is the fallback user-facing
 * tenant — demo) so we don't conflate "is KC alive" with "what tenant
 * should the user land in".
 */
export const SYSTEM_REALM = 'system'

/**
 * Subdomain labels that are infrastructure / reserved hosts, NOT tenants.
 * Anything matching here falls through to the next resolution source.
 * Keep this list small and explicit; the goal is to prevent obvious misroutes
 * (visiting `www.access-matrix.com` should NOT log you in to a tenant
 * called "www"), not to be an exhaustive blocklist.
 */
const RESERVED_SUBDOMAINS = new Set([
  'www', 'app', 'admin', 'api', 'static', 'cdn', 'auth', 'kc', 'sso', 'docs'
])

/** RFC 1035 label, lowercase. Matches Keycloak's realm-name constraint. */
const LABEL_RE = /^[a-z0-9](?:[a-z0-9-]{0,61}[a-z0-9])?$/

let cached = null

/**
 * Current tenant for this browser. See module header for resolution order.
 * Memoized — see also {@link clearTenantCache} if a test needs to re-resolve.
 */
export function currentTenant() {
  if (cached) return cached
  cached = resolve()
  return cached
}

/**
 * Persist `name` as the active tenant and update the in-memory cache.
 * Useful from login UI or dev tools. Throws on invalid names so a typo
 * fails loudly instead of silently routing to the wrong realm.
 */
export function setTenantOverride(name) {
  if (!isValidLabel(name)) {
    throw new Error(`invalid tenant: ${name} — must be a lowercase RFC1035 label`)
  }
  localStorage.setItem(TENANT_LS_KEY, name)
  cached = name
}

/** Drop the memoized value. Test-only — production never needs this. */
export function clearTenantCache() {
  cached = null
}

/**
 * Drop the persisted tenant override AND the in-memory cache. Used by the
 * SSO recovery path when a stored tenant points at a realm that no longer
 * exists — clearing here makes the very next currentTenant() call fall
 * through to the default, so the user can recover without DevTools.
 */
export function clearStoredTenant() {
  try { localStorage.removeItem(TENANT_LS_KEY) } catch { /* no-op */ }
  cached = null
}

/**
 * Inspect a hostname and return the tenant subdomain if one is present,
 * else null. Exported so tests can pin the host-parsing branch without
 * having to mock `window.location`.
 */
export function tenantFromHost(host) {
  if (!host || typeof host !== 'string') return null
  // Strip port, if any.
  const bare = host.includes(':') ? host.split(':')[0] : host
  if (bare === 'localhost') return null
  // IPv4 dotted-quad — no subdomain concept.
  if (/^\d{1,3}(?:\.\d{1,3}){3}$/.test(bare)) return null
  // IPv6 literals come in brackets ([::1]); just bail.
  if (bare.startsWith('[')) return null
  const parts = bare.split('.')
  // Apex (`access-matrix.com`) or 2-label (`localhost.localdomain`) — no
  // tenant subdomain. Tenants live under a 3+ label production host
  // (`<tenant>.access-matrix.com`).
  if (parts.length < 3) return null
  const first = parts[0].toLowerCase()
  if (RESERVED_SUBDOMAINS.has(first)) return null
  return isValidLabel(first) ? first : null
}

function resolve() {
  // 1. Query-string override (sticks).
  try {
    const qs = new URLSearchParams(window.location.search).get(TENANT_QS_KEY)
    if (qs && isValidLabel(qs)) {
      localStorage.setItem(TENANT_LS_KEY, qs)
      return qs
    }
  } catch { /* SSR / no window — fall through */ }

  // 2. Subdomain (production).
  try {
    const fromHost = tenantFromHost(window.location.hostname)
    if (fromHost) {
      // Persist so an XHR retry triggered before any page navigation still
      // resolves the same value via the localStorage path below — defensive,
      // since cached covers it for the normal in-tab lifecycle anyway.
      localStorage.setItem(TENANT_LS_KEY, fromHost)
      return fromHost
    }
  } catch { /* no window — fall through */ }

  // 3. Sticky localStorage value (carry-over from a previous explicit pick).
  try {
    const stored = localStorage.getItem(TENANT_LS_KEY)
    if (stored && isValidLabel(stored)) return stored
  } catch { /* no localStorage — fall through */ }

  // 4. Fallback.
  return DEFAULT_TENANT
}

function isValidLabel(name) {
  return typeof name === 'string' && LABEL_RE.test(name)
}
