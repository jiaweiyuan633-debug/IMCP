import request from '@/utils/request'
import axios from 'axios'
import type { MenuNode, PageResult, RoleOptionVo, RoleVo, UserVo } from '@/types'
import { getAccessToken } from '@/utils/auth'
import { API_BASE_URL } from '@/utils/env'

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

export function assignUserPosts(id: number, postIds: number[]): Promise<void> {
  return request.put(`/system/user/${id}/posts`, { postIds })
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
  const baseURL = API_BASE_URL
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

export interface NoticeVo {
  id: number
  noticeTitle: string
  noticeType: number
  noticeContent?: string
  status: number
  createdAt?: string
  updatedAt?: string
}

export function getNoticePage(params: Record<string, unknown>): Promise<PageResult<NoticeVo>> {
  return request.get('/system/notice', { params })
}

export function getLatestNotices(limit = 5): Promise<NoticeVo[]> {
  return request.get('/system/notice/latest', { params: { limit } })
}

export function getNoticeDetail(id: number): Promise<NoticeVo> {
  return request.get(`/system/notice/${id}`)
}

export function getUnreadNoticeCount(): Promise<number> {
  return request.get('/system/notice/unread-count')
}

export function markNoticeRead(id: number): Promise<void> {
  return request.put(`/system/notice/read/${id}`)
}

export function markAllNoticeRead(): Promise<void> {
  return request.put('/system/notice/read-all')
}

export function getNoticeSseTicket(): Promise<string> {
  return request.get('/system/notice/ticket')
}

export interface MessageVo {
  id: number
  messageType: string
  title: string
  content?: string
  bizType?: string
  bizId?: number
  priority?: string
  readFlag: number
  createdAt?: string
}

export interface NotificationFeedItem {
  kind: 'message' | 'notice'
  id: number
  title: string
  content?: string
  createdAt?: string
  tag?: string | null
}

export function getNotificationFeed(limit = 8): Promise<NotificationFeedItem[]> {
  return request.get('/system/message/feed', { params: { limit } })
}

export function getMessagePage(params: Record<string, unknown>): Promise<PageResult<MessageVo>> {
  return request.get('/system/message', { params })
}

export function getLatestMessages(limit = 5): Promise<MessageVo[]> {
  return request.get('/system/message/latest', { params: { limit } })
}

export function getMessageDetail(id: number): Promise<MessageVo> {
  return request.get(`/system/message/${id}`)
}

export function getUnreadMessageCount(): Promise<number> {
  return request.get('/system/message/unread-count')
}

export function markMessageRead(id: number): Promise<void> {
  return request.put(`/system/message/read/${id}`)
}

export function markAllMessageRead(): Promise<void> {
  return request.put('/system/message/read-all')
}

export function sendMessage(data: Record<string, unknown>): Promise<number> {
  return request.post('/system/message', data)
}

export function getMessageTodos(params: Record<string, unknown>): Promise<PageResult<WorkflowVo>> {
  return request.get('/system/message/todos', { params })
}

export function createNotice(data: Record<string, unknown>): Promise<number> {
  return request.post('/system/notice', data)
}

export function updateNotice(data: Record<string, unknown>): Promise<void> {
  return request.put('/system/notice', data)
}

export function deleteNotice(id: number): Promise<void> {
  return request.delete(`/system/notice/${id}`)
}

export interface TenantVo {
  id: number
  tenantName: string
  tenantCode: string
  status: number
  contactName?: string
  contactPhone?: string
  userLimit?: number
  storageLimitMb?: number
  adminUserId?: number
  createdAt?: string
}

export function getTenantPage(params: Record<string, unknown>): Promise<PageResult<TenantVo>> {
  return request.get('/system/tenant', { params })
}

export function createTenant(data: Record<string, unknown>): Promise<number> {
  return request.post('/system/tenant', data)
}

export function updateTenant(data: Record<string, unknown>): Promise<void> {
  return request.put('/system/tenant', data)
}

export function deleteTenant(id: number): Promise<void> {
  return request.delete(`/system/tenant/${id}`)
}

export interface WorkflowVo {
  id: number
  processName: string
  bizType: string
  processDefId?: number
  flowInstanceId?: number
  flowDefId?: number
  currentTaskId?: number
  currentNodeName?: string
  currentNodeIds?: string
  formData?: string
  assigneeUserId?: number
  assigneeName?: string
  applicantName?: string
  content?: string
  status: string
  remark?: string
  createdAt?: string
}

export function getWorkflowPage(params: Record<string, unknown>): Promise<PageResult<WorkflowVo>> {
  return request.get('/system/workflow-engine', { params })
}

export function getWorkflowTasks(params: Record<string, unknown>): Promise<PageResult<WorkflowVo>> {
  return request.get('/system/workflow-engine/tasks', { params })
}

export function createWorkflow(data: Record<string, unknown>): Promise<number> {
  return request.post('/system/workflow-engine', data)
}

export function approveWorkflow(id: number, remark?: string, taskId?: number, nodeId?: number): Promise<void> {
  return request.put(`/system/workflow-engine/${id}/approve`, { remark, taskId, nodeId })
}

export function rejectWorkflow(id: number, remark?: string): Promise<void> {
  return request.put(`/system/workflow-engine/${id}/reject`, { remark })
}

export function withdrawWorkflow(id: number, remark?: string): Promise<void> {
  return request.put(`/system/workflow-engine/${id}/withdraw`, { remark })
}

export function delegateWorkflow(id: number, delegateUserId: number): Promise<void> {
  return request.put(`/system/workflow-engine/${id}/delegate`, { delegateUserId })
}

export interface WorkflowLogVo {
  id: number
  workflowId: number
  action: string
  operatorName?: string
  remark?: string
  createdAt?: string
}

export function getWorkflowLogs(id: number): Promise<WorkflowLogVo[]> {
  return request.get(`/system/workflow-engine/${id}/logs`)
}

export interface ProcessDefVo {
  id: number
  defName: string
  defKey: string
  description?: string
  status: number
  createdAt?: string
}

export interface ProcessNodeVo {
  id?: number
  taskId?: number
  nodeName: string
  nodeKey: string
  nodeType?: string
  conditionExpression?: string
  timeoutHours?: number
  nodeOrder: number
  approverRoleId?: number
}

export function getWorkflowCurrentNodes(id: number): Promise<ProcessNodeVo[]> {
  return request.get(`/system/workflow-engine/${id}/nodes`)
}

export function getProcessDefPage(params: Record<string, unknown>): Promise<PageResult<ProcessDefVo>> {
  return request.get('/system/workflow-engine/def', { params })
}

export function getProcessDefOptions(): Promise<ProcessDefVo[]> {
  return request.get('/system/workflow-engine/def/options')
}

export function getProcessDefNodes(id: number): Promise<ProcessNodeVo[]> {
  return request.get(`/system/workflow-engine/def/${id}/nodes`)
}

export function createProcessDef(data: Record<string, unknown>): Promise<number> {
  return request.post('/system/workflow-engine/def', data)
}

export function updateProcessDef(data: Record<string, unknown>): Promise<void> {
  return request.put('/system/workflow-engine/def', data)
}

export function deleteProcessDef(id: number): Promise<void> {
  return request.delete(`/system/workflow-engine/def/${id}`)
}

export function publishProcessDef(id: number): Promise<void> {
  return request.put(`/system/workflow-engine/def/${id}/publish`)
}

export function unpublishProcessDef(id: number): Promise<void> {
  return request.put(`/system/workflow-engine/def/${id}/unpublish`)
}

export interface FileVo {
  id: number
  fileName: string
  originalName?: string
  url: string
  size: number
  storageType: string
  accessToken?: string
  contentType?: string
  category?: string
  sha256?: string
  scanStatus?: string
  contentUrl?: string
  createdAt?: string
}

export function getFilePage(params: Record<string, unknown>): Promise<PageResult<FileVo>> {
  return request.get('/system/file', { params })
}

export function deleteFile(id: number): Promise<void> {
  return request.delete(`/system/file/${id}`)
}

export async function downloadFile(id: number): Promise<Blob> {
  const response = await fetch(`${API_BASE_URL}/system/file/${id}/download`, {
    headers: { Authorization: `Bearer ${getAccessToken() || ''}` },
  })
  if (!response.ok) {
    throw new Error(`Download failed: ${response.status}`)
  }
  return response.blob()
}

