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

