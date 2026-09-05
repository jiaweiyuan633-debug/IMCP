export interface Result<T> {
  code: number
  message: string
  data: T
  requestId?: string
}

export interface MenuNode {
  id: number
  parentId: number
  name: string
  type: 'dir' | 'menu' | 'button'
  path?: string
  component?: string
  perm?: string
  icon?: string
  sort: number
  visible: number
  status: number
  children?: MenuNode[]
}

export interface UserInfo {
  id: number
  username: string
  nickname?: string
  avatar?: string
  roles: string[]
  perms: string[]
  menus: MenuNode[]
  /** 当前用户是否处于"必须修改密码"状态（默认口令首登 / 密码过期） */
  mustChangePassword?: boolean
}

export interface LoginResponse {
  accessToken: string
  refreshToken: string
  user: UserInfo
  /** 登录后是否必须修改密码（默认口令首登 / 密码过期；生产 forcePasswordChange 开启时返回 true） */
  mustChangePassword?: boolean
}

export interface LoginForm {
  username: string
  password: string
  captchaId?: string
  captchaCode?: string
  totpCode?: string
  /** 可选租户 ID：跨租户存在同名用户时精确定位 */
  tenantId?: number
}

export interface LoginConfigVo {
  captchaEnabled: boolean
}

export interface CaptchaResponse {
  captchaId: string
  image: string
}

export interface PageResult<T> {
  records: T[]
  total: number
  pageNum: number
  pageSize: number
}

export interface UserVo {
  id: number
  deptId?: number
  deptName?: string
  avatar?: string
  username: string
  nickname?: string
  email?: string
  phone?: string
  status: number
  lastLoginTime?: string
  createdAt?: string
  roleIds: number[]
  roleNames: string[]
  postIds: number[]
  postNames: string[]
}

export interface RoleVo {
  id: number
  code: string
  name: string
  description?: string
  status: number
  dataScope: number
  sort: number
  createdAt?: string
  menuIds: number[]
  deptIds: number[]
}

export interface RoleOptionVo {
  id: number
  code: string
  name: string
}

export interface DeptVo {
  id: number
  parentId: number
  deptName: string
  orderNum: number
  leader?: string
  phone?: string
  email?: string
  status: number
  children?: DeptVo[]
}

export interface PostOptionVo {
  id: number
  postCode: string
  postName: string
}

export interface SearchField {
  label: string
  prop: string
  type?: 'input' | 'select'
  placeholder?: string
  options?: { label: string; value: string | number }[]
}


