import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'

/**
 * system API 契约测试——覆盖 CRUD 全动词 + 文件导出（axios blob）/
 * 导入（FormData）/下载（fetch）三条特殊路径，锁定 URL、方法与载荷形状。
 */

const { requestMock, axiosGet, parseCD, triggerBlob } = vi.hoisted(() => ({
  requestMock: { get: vi.fn(), post: vi.fn(), put: vi.fn(), delete: vi.fn() },
  axiosGet: vi.fn(),
  parseCD: vi.fn(),
  triggerBlob: vi.fn(),
}))

vi.mock('@/utils/request', () => ({ default: requestMock }))
vi.mock('axios', () => ({ default: { get: axiosGet } }))
vi.mock('@/utils/auth', () => ({ getAccessToken: vi.fn(() => 'at') }))
vi.mock('@/utils/env', () => ({ API_BASE_URL: 'http://test/api' }))
vi.mock('@/utils/download', () => ({
  parseContentDispositionFilename: parseCD,
  triggerBlobDownload: triggerBlob,
}))
vi.mock('@/locales', () => ({
  default: { global: { t: (key: string) => key } },
}))

import {
  assignRoleMenus,
  createUser,
  deleteFile,
  deleteUser,
  downloadFile,
  exportUsers,
  getConfigPage,
  getMenuTree,
  getNoticePage,
  getUserPage,
  importUsers,
  updateUserStatus,
} from '@/api/system'

describe('system API CRUD 契约', () => {
  beforeEach(() => {
    vi.clearAllMocks()
  })

  afterEach(() => {
    vi.unstubAllGlobals()
  })

  it('getUserPage 携带分页与筛选参数', async () => {
    requestMock.get.mockResolvedValue({ records: [], total: 0, pageNum: 1, pageSize: 10 })
    const params = { pageNum: 1, pageSize: 10, username: 'a' }

    const result = await getUserPage(params)

    expect(requestMock.get).toHaveBeenCalledWith('/system/user', { params })
    expect(result.total).toBe(0)
  })

  it('createUser POST 用户', async () => {
    requestMock.post.mockResolvedValue(42)
    const data = { username: 'u', status: 1, roleIds: [1], postIds: [] }

    const id = await createUser(data)

    expect(requestMock.post).toHaveBeenCalledWith('/system/user', data)
    expect(id).toBe(42)
  })

  it('updateUserStatus 携带路径参数与状态', async () => {
    await updateUserStatus(7, 0)

    expect(requestMock.put).toHaveBeenCalledWith('/system/user/7/status', { status: 0 })
  })

  it('deleteUser DELETE 用户', async () => {
    await deleteUser(7)

    expect(requestMock.delete).toHaveBeenCalledWith('/system/user/7')
  })

  it('getMenuTree 拉取菜单树', async () => {
    requestMock.get.mockResolvedValue([{ id: 1, parentId: 0, name: 'root', type: 'dir', sort: 1, visible: 1, status: 1 }])

    const result = await getMenuTree()

    expect(requestMock.get).toHaveBeenCalledWith('/system/menu/tree')
    expect(result[0].name).toBe('root')
  })

  it('assignRoleMenus 分配菜单权限', async () => {
    await assignRoleMenus(3, [1, 2, 3])

    expect(requestMock.put).toHaveBeenCalledWith('/system/role/3/menus', { menuIds: [1, 2, 3] })
  })

  it('getConfigPage / getNoticePage 分页查询', async () => {
    requestMock.get.mockResolvedValue({ records: [], total: 0, pageNum: 1, pageSize: 10 })

    await getConfigPage({ pageNum: 1, pageSize: 10 })
    expect(requestMock.get).toHaveBeenCalledWith('/system/config', { params: { pageNum: 1, pageSize: 10 } })

    await getNoticePage({ pageNum: 1, pageSize: 10 })
    expect(requestMock.get).toHaveBeenCalledWith('/system/notice', { params: { pageNum: 1, pageSize: 10 } })
  })

  it('deleteFile DELETE 文件', async () => {
    await deleteFile(9)

    expect(requestMock.delete).toHaveBeenCalledWith('/system/file/9')
  })
})

describe('system API 特殊路径（导出/导入/下载）', () => {
  beforeEach(() => {
    vi.clearAllMocks()
  })

  afterEach(() => {
    vi.unstubAllGlobals()
  })

  it('exportUsers 经 axios 拉取 blob 并依据 Content-Disposition 触发下载', async () => {
    axiosGet.mockResolvedValue({
      data: new Blob(['a,b'], { type: 'text/csv' }),
      headers: { 'content-disposition': "attachment; filename*=UTF-8''user.csv" },
    })
    parseCD.mockReturnValue('user.csv')

    await exportUsers()

    expect(axiosGet).toHaveBeenCalledWith(
      'http://test/api/system/user/export',
      expect.objectContaining({ responseType: 'blob', headers: { Authorization: 'Bearer at' } }),
    )
    expect(triggerBlob).toHaveBeenCalledWith(expect.any(Blob), 'user.csv')
  })

  it('exportUsers 缺失 Content-Disposition 时回退翻译文件名', async () => {
    axiosGet.mockResolvedValue({ data: new Blob(['a,b']), headers: {} })
    parseCD.mockReturnValue(null)

    await exportUsers()

    // i18n mock 返回 key 本身（未翻译），回退文件名 = t(key) + '.xlsx'
    expect(triggerBlob).toHaveBeenCalledWith(expect.any(Blob), 'common.userDataExport.xlsx')
  })

  it('importUsers 以 FormData 提交文件', async () => {
    requestMock.post.mockResolvedValue(10)
    const file = new File(['u'], 'users.csv')

    const count = await importUsers(file)

    expect(requestMock.post).toHaveBeenCalledWith(
      '/system/user/import',
      expect.any(FormData),
      expect.objectContaining({ headers: { 'Content-Type': 'multipart/form-data' } }),
    )
    expect(count).toBe(10)
  })

  it('downloadFile 经 fetch 下载并携带鉴权头', async () => {
    const fetchMock = vi.fn().mockResolvedValue({
      ok: true,
      blob: vi.fn().mockResolvedValue(new Blob(['x'])),
    })
    vi.stubGlobal('fetch', fetchMock)

    const blob = await downloadFile(1)

    expect(fetchMock).toHaveBeenCalledWith(
      'http://test/api/system/file/1/download',
      expect.objectContaining({ headers: { Authorization: 'Bearer at' } }),
    )
    expect(blob).toBeInstanceOf(Blob)
  })

  it('downloadFile 响应非 ok 时抛错', async () => {
    vi.stubGlobal('fetch', vi.fn().mockResolvedValue({ ok: false, status: 404 }))

    await expect(downloadFile(1)).rejects.toThrow('404')
  })
})
