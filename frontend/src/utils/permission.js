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
 * Mirrors the backend's PermissionMatcher wildcard semantics:
 *   - "*:*"          → grants everything (super admin)
 *   - "resource:*"   → grants every action on that resource
 *   - exact "r:a"    → grants only that pair
 *
 * Without wildcard support the front-end would hide every v-permission
 * button for super-admins who hold only `["*:*"]`.
 */
export function matchPermission(perms, want) {
  if (!Array.isArray(perms) || perms.length === 0 || !want) return false
  if (perms.includes('*:*')) return true
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
