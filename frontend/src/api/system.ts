import request from '@/utils/request'
import type { MenuNode, PageResult, RoleOptionVo, RoleVo, UserVo } from '@/types'

export interface UserQuery {
  pageNum: number
  pageSize: number
  username?: string
  nickname?: string
  status?: number
}

export interface UserSaveRequest {
  id?: number
  username: string
  password?: string
  nickname?: string
  email?: string
  phone?: string
  status: number
  roleIds: number[]
}

export interface RoleQuery {
  pageNum: number
  pageSize: number
  code?: string
  name?: string
  status?: number
}

export interface RoleSaveRequest {
  id?: number
  code: string
  name: string
  description?: string
  status: number
  sort: number
  menuIds: number[]
}

export interface MenuSaveRequest {
  id?: number
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
}

export function getUserPage(params: UserQuery): Promise<PageResult<UserVo>> {
  return request.get('/api/system/user', { params })
}

export function createUser(data: UserSaveRequest): Promise<number> {
  return request.post('/api/system/user', data)
}

export function updateUser(data: UserSaveRequest): Promise<void> {
  return request.put('/api/system/user', data)
}

export function deleteUser(id: number): Promise<void> {
  return request.delete(`/api/system/user/${id}`)
}

export function updateUserStatus(id: number, status: number): Promise<void> {
  return request.put(`/api/system/user/${id}/status`, { status })
}

export function assignUserRoles(id: number, roleIds: number[]): Promise<void> {
  return request.put(`/api/system/user/${id}/roles`, { roleIds })
}

export function getRolePage(params: RoleQuery): Promise<PageResult<RoleVo>> {
  return request.get('/api/system/role', { params })
}

export function getRoleOptions(): Promise<RoleOptionVo[]> {
  return request.get('/api/system/role/options')
}

export function createRole(data: RoleSaveRequest): Promise<number> {
  return request.post('/api/system/role', data)
}

export function updateRole(data: RoleSaveRequest): Promise<void> {
  return request.put('/api/system/role', data)
}

export function deleteRole(id: number): Promise<void> {
  return request.delete(`/api/system/role/${id}`)
}

export function assignRoleMenus(id: number, menuIds: number[]): Promise<void> {
  return request.put(`/api/system/role/${id}/menus`, { menuIds })
}

export function getMenuTree(): Promise<MenuNode[]> {
  return request.get('/api/system/menu/tree')
}

export function createMenu(data: MenuSaveRequest): Promise<number> {
  return request.post('/api/system/menu', data)
}

export function updateMenu(data: MenuSaveRequest): Promise<void> {
  return request.put('/api/system/menu', data)
}

export function deleteMenu(id: number): Promise<void> {
  return request.delete(`/api/system/menu/${id}`)
}

