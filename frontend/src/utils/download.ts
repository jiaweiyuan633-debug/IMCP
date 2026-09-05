/**
 * 浏览器端 Blob 下载统一封装：
 * - 从后端 Content-Disposition 解析文件名（含中文与扩展名），缺失时回退默认名
 * - Safari/Firefox 需要 <a> 挂载到文档树才能触发下载
 * - 下载完成后及时 revokeObjectURL 释放内存
 */

/** 解析 Content-Disposition 中的文件名，优先 RFC 5987（filename*=UTF-8''） */
export function parseContentDispositionFilename(contentDisposition?: string | null): string | null {
  if (!contentDisposition) {
    return null
  }
  // RFC 5987：filename*=UTF-8''%E7%94%A8%E6%88%B7.csv
  const rfc5987 = /filename\*=UTF-8''([^;]+)/i.exec(contentDisposition)
  if (rfc5987) {
    try {
      return decodeURIComponent(rfc5987[1].trim().replace(/^"|"$/g, ''))
    } catch {
      // decode 失败时回退普通形式
    }
  }
  // 普通形式：filename="x.csv" 或 filename=x.csv
  const quoted = /filename="([^"]+)"/i.exec(contentDisposition)
  if (quoted) {
    return quoted[1]
  }
  const bare = /filename=([^;]+)/i.exec(contentDisposition)
  return bare ? bare[1].trim() : null
}

/** 触发浏览器下载（挂载 a 元素以兼容 Safari/Firefox，完成后 revoke） */
export function triggerBlobDownload(blob: Blob, filename: string): void {
  const url = URL.createObjectURL(blob)
  const link = document.createElement('a')
  link.href = url
  link.download = filename
  document.body.appendChild(link)
  link.click()
  document.body.removeChild(link)
  URL.revokeObjectURL(url)
}
