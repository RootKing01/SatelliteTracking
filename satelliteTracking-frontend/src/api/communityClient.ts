import { httpClient } from './httpClient'

export type CommunityComment = {
  id: number
  threadId: number
  parentCommentId: number | null
  authorId: number
  authorUsername: string
  body: string
  createdAt: string
  updatedAt: string
  deleted: boolean
}

export type CommunityThread = {
  id: number
  targetType: 'SATELLITE' | 'SIGHTING' | 'PASS' | 'GENERAL'
  targetId: string
  title: string
  commentCount: number
  likesCount: number
  likedByMe: boolean
  createdAt: string
  lastCommentAt: string | null
}

export type CommunityThreadWithComments = {
  thread: CommunityThread
  comments: CommunityComment[]
}

export type CommunityFeedItem = {
  threadId: number
  targetType: 'SATELLITE' | 'SIGHTING' | 'PASS' | 'GENERAL'
  targetId: string
  title: string
  commentCount: number
  likesCount: number
  likedByMe: boolean
  lastCommentAt: string | null
  lastCommentPreview: string
}

export type CommunityThreadLike = {
  threadId: number
  likesCount: number
  likedByMe: boolean
}

export async function fetchCommunityThread(targetType: string, targetId: string, signal?: AbortSignal): Promise<CommunityThreadWithComments> {
  const response = await httpClient.get<CommunityThreadWithComments>(
    `/api/community/threads/${encodeURIComponent(targetType)}/${encodeURIComponent(targetId)}`,
    { signal },
  )
  return response.data
}

export async function createCommunityThread(payload: {
  title: string
  body: string
}): Promise<CommunityThreadWithComments> {
  const response = await httpClient.post<CommunityThreadWithComments>('/api/community/threads', payload)
  return response.data
}

export async function createCommunityComment(payload: {
  targetType: string
  targetId: string
  body: string
  parentCommentId?: number | null
}): Promise<CommunityComment> {
  const response = await httpClient.post<CommunityComment>(
    `/api/community/threads/${encodeURIComponent(payload.targetType)}/${encodeURIComponent(payload.targetId)}/comments`,
    {
      body: payload.body,
      parentCommentId: payload.parentCommentId ?? null,
    },
  )
  return response.data
}

export async function updateCommunityComment(commentId: number, body: string): Promise<CommunityComment> {
  const response = await httpClient.put<CommunityComment>(`/api/community/comments/${commentId}`, { body })
  return response.data
}

export async function deleteCommunityComment(commentId: number): Promise<void> {
  await httpClient.delete(`/api/community/comments/${commentId}`)
}

export async function reportCommunityComment(commentId: number, reason: string): Promise<void> {
  await httpClient.post(`/api/community/comments/${commentId}/reports`, { reason })
}

export async function fetchCommunityFeed(limit = 20, signal?: AbortSignal): Promise<CommunityFeedItem[]> {
  const response = await httpClient.get<CommunityFeedItem[]>('/api/community/feed', {
    params: { limit },
    signal,
  })
  return response.data
}

export async function fetchFeaturedCommunityThreads(limit = 8, signal?: AbortSignal): Promise<CommunityFeedItem[]> {
  const response = await httpClient.get<CommunityFeedItem[]>('/api/community/threads/featured', {
    params: { limit },
    signal,
  })
  return response.data
}

export async function toggleCommunityThreadLike(threadId: number): Promise<CommunityThreadLike> {
  const response = await httpClient.post<CommunityThreadLike>(`/api/community/threads/${threadId}/likes`)
  return response.data
}
