import { useState } from 'react'
import {
  fetchCommunityThread,
  ensureCommunityThread,
  createCommunityComment,
  deleteCommunityComment,
  updateCommunityComment,
  reportCommunityComment,
  type CommunityComment,
  type CommunityThread,
} from '../api/communityClient'
import { fetchSatelliteCatalogByType } from '../api/satelliteCatalogClient'
import { buildSatelliteNameMap } from './satelliteNameHelper'

/**
 * Hook custom per gestire i commenti e il thread attivo della community.
 * Incapsula logica di caricamento, creazione, modifica, eliminazione, segnalazione commenti.
 */
export function useCommunityComments(authUser: any, communitySessionValid: boolean) {
  const [activeThread, setActiveThread] = useState<CommunityThread | null>(null)
  const [comments, setComments] = useState<CommunityComment[]>([])
  const [commentsLoading, setCommentsLoading] = useState(false)
  const [commentsError, setCommentsError] = useState('')

  // Carica thread e commenti
  const loadThread = async (targetType: string, targetId: string, setSatelliteNames: (map: Record<string, string>) => void) => {
    if (!authUser || !communitySessionValid) {
      setCommentsError('Sessione scaduta. Esegui di nuovo l\'accesso.')
      return
    }
    setCommentsLoading(true)
    setCommentsError('')
    try {
      // Aggiorna la mappa nomi se thread satellite
      if (targetType === 'SATELLITE') {
        const list = await fetchSatelliteCatalogByType('ALL')
        setSatelliteNames(buildSatelliteNameMap(list))
      }
      const payload = await fetchCommunityThread(targetType, targetId)
      setActiveThread(payload.thread)
      setComments(payload.comments)
    } catch (error) {
      setActiveThread(null)
      setComments([])
      setCommentsError('Impossibile caricare i commenti del thread selezionato.')
    } finally {
      setCommentsLoading(false)
    }
  }

  // Crea o assicura thread
  const ensureThread = async (targetType: string, targetId: string, setSatelliteNames: (map: Record<string, string>) => void) => {
    if (!authUser || !communitySessionValid) {
      setCommentsError('Sessione scaduta. Esegui di nuovo l\'accesso.')
      return
    }
    setCommentsLoading(true)
    setCommentsError('')
    try {
      if (targetType === 'SATELLITE') {
        const list = await fetchSatelliteCatalogByType('ALL')
        setSatelliteNames(buildSatelliteNameMap(list))
      }
      const payload = await ensureCommunityThread(targetType, targetId)
      setActiveThread(payload.thread)
      setComments(payload.comments)
    } catch (error) {
      setActiveThread(null)
      setComments([])
      setCommentsError('Impossibile aprire o creare il thread del satellite selezionato.')
    } finally {
      setCommentsLoading(false)
    }
  }

  // Crea commento
  const submitComment = async (activeThread: CommunityThread | null, newCommentBody: string, replyToComment: CommunityComment | null, setAllThreads: any, setNewCommentBody: any, setReplyToComment: any) => {
    if (!communitySessionValid) {
      setCommentsError('Sessione scaduta. Esegui di nuovo l\'accesso.')
      return
    }
    if (!activeThread || !newCommentBody.trim()) {
      return
    }
    setCommentsError('')
    try {
      const created = await createCommunityComment({
        targetType: activeThread.targetType,
        targetId: activeThread.targetId,
        body: newCommentBody,
        parentCommentId: replyToComment ? replyToComment.id : null,
      })
      setComments((prev) => [...prev, created])
      setNewCommentBody('')
      setReplyToComment(null)
      setAllThreads((prev: any) => prev.map((item: any) => item.threadId === activeThread.id ? { ...item, commentCount: item.commentCount + 1, lastCommentAt: created.createdAt, lastCommentPreview: created.body } : item))
      setActiveThread((prev) => prev ? { ...prev, commentCount: prev.commentCount + 1, lastCommentAt: created.createdAt } : prev)
    } catch (error) {
      setCommentsError('Invio commento non riuscito. Riprova.')
    }
  }

  // Elimina commento
  const deleteComment = async (commentId: number) => {
    try {
      await deleteCommunityComment(commentId)
      setComments((prev) => prev.map((item) => item.id === commentId ? { ...item, deleted: true, body: '[commento rimosso]' } : item))
    } catch (error) {
      setCommentsError('Impossibile eliminare il commento selezionato.')
    }
  }

  // Modifica commento
  const saveEdit = async (commentId: number, editingBody: string, setEditingCommentId: any, setEditingBody: any) => {
    if (!editingBody.trim()) return
    try {
      const updated = await updateCommunityComment(commentId, editingBody)
      setComments((prev) => prev.map((item) => (item.id === commentId ? updated : item)))
      setEditingCommentId(null)
      setEditingBody('')
    } catch (error) {
      setCommentsError('Modifica commento non riuscita.')
    }
  }

  // Segnala commento
  const reportComment = async (commentId: number) => {
    try {
      await reportCommunityComment(commentId, 'Contenuto non appropriato')
    } catch (error) {
      setCommentsError('Segnalazione non inviata. Riprova.')
    }
  }

  return {
    activeThread,
    setActiveThread,
    comments,
    setComments,
    commentsLoading,
    commentsError,
    setCommentsError,
    loadThread,
    ensureThread,
    submitComment,
    deleteComment,
    saveEdit,
    reportComment,
  }
}
