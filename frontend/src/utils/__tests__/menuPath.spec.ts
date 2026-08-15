import { describe, expect, it } from 'vitest'
import { fullPathOf, lastSegment, resolveMenuPath } from '@/utils/menuPath'
import type { MenuNode } from '@/types'

/**
 * R4-1.33：菜单路径解析工具契约测试。
 * 该逻辑此前在 router/index、router/dynamic、BasicLayout、GlobalSearch 四处重复实现，
 * 收敛到 menuPath 后由本测试锁定语义，防止重名子目录归一等边界被后续改动破坏。
 */

describe('lastSegment', () => {
  it('取路径末段', () => {
    expect(lastSegment('/system/user')).toBe('user')
    expect(lastSegment('system/user')).toBe('user')
  })

  it('根路径与空路径返回空串', () => {
    expect(lastSegment('/')).toBe('')
    expect(lastSegment('')).toBe('')
  })
})

describe('resolveMenuPath', () => {
  it('绝对路径原样返回', () => {
    expect(resolveMenuPath('/system', '/absolute/path')).toBe('/absolute/path')
  })

  it('路径等于父路径末段时即父路径本身（重名目录归一）', () => {
    expect(resolveMenuPath('/system/user', 'user')).toBe('/system/user')
  })

  it('相对路径拼接父路径（父路径去尾部斜杠）', () => {
    expect(resolveMenuPath('/system', 'user')).toBe('/system/user')
    expect(resolveMenuPath('/', 'dashboard')).toBe('/dashboard')
    expect(resolveMenuPath('/system/', 'user')).toBe('/system/user')
  })

  it('多层嵌套递归拼接', () => {
    expect(resolveMenuPath('/system/user', 'monitor')).toBe('/system/user/monitor')
  })
})

describe('fullPathOf', () => {
  function menu(path?: string, overrides: Partial<MenuNode> = {}): MenuNode {
    return { id: 1, parentId: 0, name: 'x', path: path || '', type: 'menu', sort: 1, visible: 1, status: 1, ...overrides }
  }

  it('从菜单节点解析完整路径', () => {
    expect(fullPathOf(menu('user'), '/system')).toBe('/system/user')
    expect(fullPathOf(menu('/system/user'))).toBe('/system/user')
    expect(fullPathOf(menu('dashboard'))).toBe('/dashboard')
  })

  it('父路径默认根路径', () => {
    expect(fullPathOf(menu('user'))).toBe('/user')
  })
})
