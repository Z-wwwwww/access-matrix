/**
 * Check if array has ALL specified values (exact match).
 * Used for role checks (no wildcard semantics).
 */
export function arrayHas(source, targets) {
  if (!Array.isArray(source) || !Array.isArray(targets)) return false
  return targets.every((t) => source.includes(t))
}

/**
 * Check if array has ANY of specified values (exact match).
 */
export function arrayHasAny(source, targets) {
  if (!Array.isArray(source) || !Array.isArray(targets)) return false
  return targets.some((t) => source.includes(t))
}

/**
 * Match a single permission code against a list of granted codes.
 * Mirrors the backend's PermissionMatcher wildcard semantics — keep the
 * two implementations in lockstep or buttons end up gated differently
 * from API calls.
 *
 *   - "*:*"          → PLATFORM super. Matches every "platform:*" permission;
 *                       does NOT cover business permissions like "user:read".
 *                       Held by PLATFORM_ADMIN.
 *   - "tenant:*"     → TENANT super. Matches every BUSINESS permission, i.e.
 *                       everything OUTSIDE the platform-ops reserved namespaces
 *                       ("platform:" and "opsuser:"). Held by business-tenant
 *                       SUPER_ADMIN. ("opsuser:" sits outside "platform:" so
 *                       "platform:*"/"*:*" don't auto-grant it — so tenant:*
 *                       must carve it out too, else a tenant admin inherits it.)
 *   - "resource:*"   → grants every action on that resource (e.g. "user:*")
 *   - exact "r:a"    → grants only that pair
 *
 * Without wildcard support the front-end would hide every v-permission
 * button for super-admins who hold only the super wildcards.
 */
const PLATFORM_NS = 'platform:'
const OPSUSER_NS = 'opsuser:'

// Platform-ops reserved namespaces — not reachable via the tenant:* wildcard.
function isPlatformOpsReserved(want) {
  return want.startsWith(PLATFORM_NS) || want.startsWith(OPSUSER_NS)
}

export function matchPermission(perms, want) {
  if (!Array.isArray(perms) || perms.length === 0 || !want) return false
  // Platform super matches only the platform: namespace.
  if (perms.includes('*:*') && want.startsWith(PLATFORM_NS)) return true
  // Tenant super matches every business perm — but NOT the platform-ops
  // reserved namespaces (platform: and opsuser:).
  if (perms.includes('tenant:*') && !isPlatformOpsReserved(want)) return true
  if (perms.includes(want)) return true
  const colon = want.indexOf(':')
  if (colon < 0) return false
  const resource = want.substring(0, colon)
  return perms.includes(`${resource}:*`)
}

/** Check that the user holds ALL the requested permissions (wildcard-aware). */
export function hasAllPermissions(perms, wants) {
  if (!Array.isArray(wants)) return false
  return wants.every((w) => matchPermission(perms, w))
}

/** Check that the user holds AT LEAST ONE of the requested permissions (wildcard-aware). */
export function hasAnyPermission(perms, wants) {
  if (!Array.isArray(wants)) return false
  return wants.some((w) => matchPermission(perms, w))
}
