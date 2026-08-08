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
}

export interface LoginResponse {
  accessToken: string
  refreshToken: string
  user: UserInfo
}

export interface LoginForm {
  username: string
  password: string
}

export interface PageResult<T> {
  records: T[]
  total: number
  pageNum: number
  pageSize: number
}

export interface UserVo {
  id: number
  username: string
  nickname?: string
  avatar?: string
  email?: string
  phone?: string
  status: number
  lastLoginTime?: string
  createdAt?: string
  roleIds: number[]
  roleNames: string[]
}

export interface RoleVo {
  id: number
  code: string
  name: string
  description?: string
  status: number
  sort: number
  createdAt?: string
  menuIds: number[]
}

export interface RoleOptionVo {
  id: number
  code: string
  name: string
}

export interface SearchField {
  label: string
  prop: string
  type?: 'input' | 'select'
  placeholder?: string
  options?: { label: string; value: string | number }[]
}


