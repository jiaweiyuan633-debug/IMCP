import request from '@/utils/request'

export interface UploadResponse {
  url: string
  name: string
  size: number
}

export function uploadFile(file: File): Promise<UploadResponse> {
  const formData = new FormData()
  formData.append('file', file)
  return request.post('/common/upload', formData, {
    headers: { 'Content-Type': 'multipart/form-data' },
  })
}

