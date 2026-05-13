import { request } from '@/utils/request'

export interface MediaItem {
  id: number
  xianyuAccountId: number
  fileName: string
  mediaUrl: string
  fileSize?: number
  createdTime?: string
}

export function getMediaList(data: {
  xianyuAccountId: number
  keyword?: string
  pageNum?: number
  pageSize?: number
}) {
  return request<{ list: MediaItem[]; total: number }>({
    url: '/media/list',
    method: 'POST',
    data
  })
}

export function deleteMedia(data: { id: number }) {
  return request<void>({
    url: '/media/delete',
    method: 'POST',
    data
  })
}
