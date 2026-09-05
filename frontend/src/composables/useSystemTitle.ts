import { computed } from 'vue'
import { useI18n } from 'vue-i18n'
import { useUserStore } from '@/stores/user'

/** 系统管理员角色编码（V1 种子角色）。 */
const ADMIN_ROLE = 'admin'

/**
 * 系统名称：未登录显示平台名，登录后按角色区分——
 * 管理员 → 后台管理系统，普通用户 → 用户个人管理系统。
 */
export function useSystemTitle() {
  const { t } = useI18n()
  const userStore = useUserStore()

  return computed(() => {
    if (!userStore.isLoggedIn) {
      return t('app.platform')
    }
    const roles = userStore.userInfo?.roles || []
    return roles.includes(ADMIN_ROLE) ? t('app.title') : t('app.userTitle')
  })
}
