import { getFileAccessToken } from '@/api/common'
import { API_BASE_URL } from '@/utils/env'

/**
 * 文件访问 URL 统一工具（R4-1.43）：收敛 FileUpload / 文件列表 / 导入导出三处重复的
 * 「取令牌 + 拼 origin」逻辑，并统一基于 API_BASE_URL（已去尾部 /），消除行为分叉。
 */

/** 拼接部署 origin：相对路径在独立部署（VITE_API_BASE_URL 注入绝对地址）时补协议/主机；http 直链（预签名）原样返回。 */
export function absoluteFileUrl(url: string): string {
  if (url.startsWith('http')) {
    return url
  }
  if (API_BASE_URL.startsWith('http')) {
    return `${new URL(API_BASE_URL).origin}${url}`
  }
  return url
}

/**
 * 现取文件访问令牌并拼接 ?token=。列表/上传缓存的令牌 TTL(1h) 后失效必然 403，
 * 一律在渲染/点击时经 /api/common/file-token 现取；取令牌失败向上抛出，由调用方决定提示或静默。
 */
export async function withFileToken(url: string): Promise<string> {
  if (!url || url.startsWith('http')) {
    return url
  }
  const token = await getFileAccessToken(url)
  return `${absoluteFileUrl(url)}?token=${encodeURIComponent(token)}`
}
