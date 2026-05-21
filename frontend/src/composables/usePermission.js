import { useAuthStore } from '@/stores/auth'
import { arrayHas, arrayHasAny } from '@/utils/permission'

export function usePermission() {
  const authStore = useAuthStore()

  function hasRole(role) {
    return authStore.roles.includes(role)
  }

  function hasAnyRole(roles) {
    return arrayHasAny(authStore.roles, Array.isArray(roles) ? roles : [roles])
  }

  function hasPermission(auth) {
    return authStore.authorities.includes(auth)
  }

  function hasAnyPermission(auths) {
    return arrayHasAny(authStore.authorities, Array.isArray(auths) ? auths : [auths])
  }

  return { hasRole, hasAnyRole, hasPermission, hasAnyPermission }
}
