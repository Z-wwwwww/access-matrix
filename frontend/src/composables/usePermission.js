import { useAuthStore } from '@/stores/auth'
import { arrayHasAny, matchPermission, hasAnyPermission as _hasAnyPermission } from '@/utils/permission'

/**
 * Role / permission probes that mirror the backend semantics.
 *
 *   - role checks are exact-match (string equality).
 *   - permission checks honour `*:*` and `resource:*` wildcards so a super-admin
 *     (`["*:*"]`) is not wrongly hidden from `v-permission='user:read'` style
 *     guards. See {@link matchPermission} for the matching rules.
 */
export function usePermission() {
  const authStore = useAuthStore()

  function hasRole(role) {
    return authStore.roles.includes(role)
  }

  function hasAnyRole(roles) {
    return arrayHasAny(authStore.roles, Array.isArray(roles) ? roles : [roles])
  }

  function hasPermission(auth) {
    return matchPermission(authStore.authorities, auth)
  }

  function hasAnyPermission(auths) {
    return _hasAnyPermission(authStore.authorities, Array.isArray(auths) ? auths : [auths])
  }

  return { hasRole, hasAnyRole, hasPermission, hasAnyPermission }
}
