import { afterEach, describe, expect, it, vi } from 'vitest'
import { parseContentDispositionFilename, triggerBlobDownload } from '@/utils/download'

describe('parseContentDispositionFilename', () => {
  it('解析 RFC 5987 编码文件名（中文）', () => {
    const raw = `attachment; filename*=UTF-8''${encodeURIComponent('用户数据.csv')}`
    expect(parseContentDispositionFilename(raw)).toBe('用户数据.csv')
  })

  it('解析带引号的普通文件名', () => {
    expect(parseContentDispositionFilename('attachment; filename="report.xlsx"')).toBe('report.xlsx')
  })

  it('解析不带引号的裸文件名', () => {
    expect(parseContentDispositionFilename('attachment; filename=report.xlsx')).toBe('report.xlsx')
  })

  it('无 Content-Disposition 返回 null', () => {
    expect(parseContentDispositionFilename(null)).toBeNull()
    expect(parseContentDispositionFilename(undefined)).toBeNull()
    expect(parseContentDispositionFilename('')).toBeNull()
  })
})

describe('triggerBlobDownload', () => {
  afterEach(() => {
    vi.restoreAllMocks()
  })

  it('创建 a 标签触发下载并释放 URL', () => {
    const createSpy = vi.fn(() => 'blob:mock-url')
    const revokeSpy = vi.fn()
    const clickSpy = vi.spyOn(HTMLAnchorElement.prototype, 'click').mockImplementation(() => {})
    vi.stubGlobal('URL', { ...URL, createObjectURL: createSpy, revokeObjectURL: revokeSpy })

    const blob = new Blob(['content'], { type: 'text/plain' })
    triggerBlobDownload(blob, 'a.txt')

    expect(createSpy).toHaveBeenCalledWith(blob)
    // a 标签挂载后触发 click，随后移除并 revoke URL
    expect(clickSpy).toHaveBeenCalledTimes(1)
    expect(document.querySelector('a[download="a.txt"]')).toBeNull()
    expect(revokeSpy).toHaveBeenCalledWith('blob:mock-url')
  })
})
