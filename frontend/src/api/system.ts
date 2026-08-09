import request from '@/utils/request'
import axios from 'axios'
import type { MenuNode, PageResult, RoleOptionVo, RoleVo, UserVo } from '@/types'
import { getAccessToken } from '@/utils/auth'

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
  avatar?: string
  nickname?: string
  email?: string
  phone?: string
  status: number
  deptId?: number
  roleIds: number[]
  postIds: number[]
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
  dataScope: number
  sort: number
  menuIds: number[]
  deptIds: number[]
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

export interface DeptSaveRequest {
  id?: number
  parentId: number
  deptName: string
  orderNum: number
  leader?: string
  phone?: string
  email?: string
  status: number
}

export interface PostVo {
  id: number
  postCode: string
  postName: string
  sort: number
  status: number
  description?: string
  createdAt?: string
}

export interface PostSaveRequest {
  id?: number
  postCode: string
  postName: string
  sort: number
  status: number
  description?: string
}

export interface DictTypeVo {
  id: number
  dictName: string
  dictType: string
  status: number
  remark?: string
  createdAt?: string
}

export interface DictDataVo {
  id: number
  dictType: string
  dictLabel: string
  dictValue: string
  dictSort: number
  listClass?: string
  isDefault: number
  status: number
  remark?: string
  createdAt?: string
}

export interface ConfigVo {
  id: number
  configName: string
  configKey: string
  configValue: string
  configType: number
  remark?: string
  createdAt?: string
}

export function getUserPage(params: UserQuery): Promise<PageResult<UserVo>> {
  return request.get('/system/user', { params })
}

export function createUser(data: UserSaveRequest): Promise<number> {
  return request.post('/system/user', data)
}

export function updateUser(data: UserSaveRequest): Promise<void> {
  return request.put('/system/user', data)
}

export function deleteUser(id: number): Promise<void> {
  return request.delete(`/system/user/${id}`)
}

export function updateUserStatus(id: number, status: number): Promise<void> {
  return request.put(`/system/user/${id}/status`, { status })
}

export function assignUserRoles(id: number, roleIds: number[]): Promise<void> {
  return request.put(`/system/user/${id}/roles`, { roleIds })
}

export function getRolePage(params: RoleQuery): Promise<PageResult<RoleVo>> {
  return request.get('/system/role', { params })
}

export function getRoleOptions(): Promise<RoleOptionVo[]> {
  return request.get('/system/role/options')
}

export function createRole(data: RoleSaveRequest): Promise<number> {
  return request.post('/system/role', data)
}

export function updateRole(data: RoleSaveRequest): Promise<void> {
  return request.put('/system/role', data)
}

export function deleteRole(id: number): Promise<void> {
  return request.delete(`/system/role/${id}`)
}

export function assignRoleMenus(id: number, menuIds: number[]): Promise<void> {
  return request.put(`/system/role/${id}/menus`, { menuIds })
}

export function getMenuTree(): Promise<MenuNode[]> {
  return request.get('/system/menu/tree')
}

export function createMenu(data: MenuSaveRequest): Promise<number> {
  return request.post('/system/menu', data)
}

export function updateMenu(data: MenuSaveRequest): Promise<void> {
  return request.put('/system/menu', data)
}

export function deleteMenu(id: number): Promise<void> {
  return request.delete(`/system/menu/${id}`)
}

export function getDeptTree(): Promise<DeptVo[]> {
  return request.get('/system/dept/tree')
}

export function createDept(data: DeptSaveRequest): Promise<number> {
  return request.post('/system/dept', data)
}

export function updateDept(data: DeptSaveRequest): Promise<void> {
  return request.put('/system/dept', data)
}

export function deleteDept(id: number): Promise<void> {
  return request.delete(`/system/dept/${id}`)
}

export function getPostPage(params: Record<string, unknown>): Promise<PageResult<PostVo>> {
  return request.get('/system/post', { params })
}

export function getPostOptions(): Promise<PostOptionVo[]> {
  return request.get('/system/post/options')
}

export function createPost(data: PostSaveRequest): Promise<number> {
  return request.post('/system/post', data)
}

export function updatePost(data: PostSaveRequest): Promise<void> {
  return request.put('/system/post', data)
}

export function deletePost(id: number): Promise<void> {
  return request.delete(`/system/post/${id}`)
}

export function getDictTypePage(params: Record<string, unknown>): Promise<PageResult<DictTypeVo>> {
  return request.get('/system/dict/type', { params })
}

export function createDictType(data: Record<string, unknown>): Promise<number> {
  return request.post('/system/dict/type', data)
}

export function updateDictType(data: Record<string, unknown>): Promise<void> {
  return request.put('/system/dict/type', data)
}

export function deleteDictType(id: number): Promise<void> {
  return request.delete(`/system/dict/type/${id}`)
}

export function getDictDataPage(params: Record<string, unknown>): Promise<PageResult<DictDataVo>> {
  return request.get('/system/dict/data', { params })
}

export function getDictDataByType(dictType: string): Promise<DictDataVo[]> {
  return request.get(`/system/dict/data/type/${dictType}`)
}

export function createDictData(data: Record<string, unknown>): Promise<number> {
  return request.post('/system/dict/data', data)
}

export function updateDictData(data: Record<string, unknown>): Promise<void> {
  return request.put('/system/dict/data', data)
}

export function deleteDictData(id: number): Promise<void> {
  return request.delete(`/system/dict/data/${id}`)
}

export function getConfigPage(params: Record<string, unknown>): Promise<PageResult<ConfigVo>> {
  return request.get('/system/config', { params })
}

export function createConfig(data: Record<string, unknown>): Promise<number> {
  return request.post('/system/config', data)
}

export function updateConfig(data: Record<string, unknown>): Promise<void> {
  return request.put('/system/config', data)
}

export function deleteConfig(id: number): Promise<void> {
  return request.delete(`/system/config/${id}`)
}

export async function exportUsers(): Promise<void> {
  const baseURL = import.meta.env.VITE_API_BASE_URL || 'http://localhost:8080/api'
  const response = await axios.get(`${baseURL}/system/user/export`, {
    headers: { Authorization: `Bearer ${getAccessToken()}` },
    responseType: 'blob',
  })
  const url = URL.createObjectURL(response.data)
  const link = document.createElement('a')
  link.href = url
  link.download = '用户数据.xlsx'
  link.click()
  URL.revokeObjectURL(url)
}

export function importUsers(file: File): Promise<number> {
  const formData = new FormData()
  formData.append('file', file)
  return request.post('/system/user/import', formData, {
    headers: { 'Content-Type': 'multipart/form-data' },
  })
}

