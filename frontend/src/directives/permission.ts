import type { Directive } from 'vue'
import { usePermissionStore } from '@/stores/permission'

export const permission: Directive<HTMLElement, string | string[]> = {
  mounted(el, binding) {
    const permissionStore = usePermissionStore()
    const required = Array.isArray(binding.value) ? binding.value : [binding.value]
    const hasPermission = required.some((perm) => permissionStore.perms.includes(perm))
    if (!hasPermission) {
      el.parentNode?.removeChild(el)
    }
  },
}

