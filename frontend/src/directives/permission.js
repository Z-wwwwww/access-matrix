import { useAuthStore } from '@/stores/auth'
import { arrayHas, arrayHasAny } from '@/utils/permission'

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

export const vRole = createDirective('roles', arrayHas)
export const vAnyRole = createDirective('roles', arrayHasAny)
export const vPermission = createDirective('authorities', arrayHas)
export const vAnyPermission = createDirective('authorities', arrayHasAny)

export function registerPermissionDirectives(app) {
  app.directive('role', vRole)
  app.directive('any-role', vAnyRole)
  app.directive('permission', vPermission)
  app.directive('any-permission', vAnyPermission)
}
