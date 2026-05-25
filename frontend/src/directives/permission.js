import { useAuthStore } from '@/stores/auth'
import { arrayHas, arrayHasAny, hasAllPermissions, hasAnyPermission } from '@/utils/permission'

function createDirective(field, checkFn) {
  return {
    mounted(el, binding) {
      const authStore = useAuthStore()
      const values = Array.isArray(binding.value) ? binding.value : [binding.value]
      if (!checkFn(authStore[field], values)) {
        el.parentNode?.removeChild(el)
      }
    }
  }
}

// Roles are exact-match (no wildcard semantics on the role side).
export const vRole = createDirective('roles', arrayHas)
export const vAnyRole = createDirective('roles', arrayHasAny)
// Permissions go through the wildcard-aware matcher so a user holding `*:*` or
// `resource:*` is not falsely rejected for a specific `resource:action` button.
export const vPermission = createDirective('authorities', hasAllPermissions)
export const vAnyPermission = createDirective('authorities', hasAnyPermission)

export function registerPermissionDirectives(app) {
  app.directive('role', vRole)
  app.directive('any-role', vAnyRole)
  app.directive('permission', vPermission)
  app.directive('any-permission', vAnyPermission)
}
