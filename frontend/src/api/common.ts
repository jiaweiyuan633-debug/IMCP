import request from '@/utils/request'

export interface UploadResponse {
  id?: number
  url: string
  name: string
  size: number
  contentType?: string
  category?: string
  sha256?: string
  scanStatus?: string
  contentUrl?: string
}

export interface StorageQuota {
  usedBytes: number
  limitBytes: number | null
  percent: number | null
  unlimited: boolean
}

export function uploadFile(file: File): Promise<UploadResponse> {
  const formData = new FormData()
  formData.append('file', file)
  return request.post('/common/upload', formData, {
    headers: { 'Content-Type': 'multipart/form-data' },
  })
}

export function getFileAccessToken(url: string): Promise<string> {
  return request.get('/common/file-token', { params: { url } })
}

export function getStorageQuota(): Promise<StorageQuota> {
  return request.get('/common/storage-quota')
}

