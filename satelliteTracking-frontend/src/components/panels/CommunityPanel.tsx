import { useCallback, useEffect, useMemo, useRef, useState } from 'react'
import { updateMissingSatelliteNames, fetchAndMapSatelliteNames } from '../../helpers/satelliteNameHelper'
import { CommunityThreadCard } from '../community/CommunityThreadCard'
import { CommunityCompose } from '../community/CommunityCompose'
import { CommunityFeedCard } from '../community/CommunityFeedCard'
import { isAxiosError } from 'axios'
import type { AuthUser } from '../../api/authClient'
import { getCurrentUser } from '../../api/authClient'
import {
  ensureCommunityThread,
  createCommunityThread,
  createCommunityComment,
  deleteCommunityComment,
  fetchFeaturedCommunityThreads,
  fetchCommunityFeed,
  fetchCommunityThread,
  reportCommunityComment,
  toggleCommunityThreadLike,
  updateCommunityComment,
  type CommunityComment,
  type CommunityFeedItem,
  type CommunityThread,
} from '../../api/communityClient'
import '../../styles/panels/community-panel.css'
 

type CommunityPanelProps = {
  authUser: AuthUser | null
  selectedSatelliteId: number | null
  selectedSatelliteName: string | null
  onFocusSatellite: (satelliteId: number) => void
}

export function CommunityPanel({
  authUser,
  selectedSatelliteId,
  selectedSatelliteName,
  onFocusSatellite,
}: CommunityPanelProps) {
  const [satelliteNames, setSatelliteNames] = useState<Record<string, string>>({})
  // satelliteCatalog non più usato
  const [featuredThreads, setFeaturedThreads] = useState<CommunityFeedItem[]>([])
  const [allThreads, setAllThreads] = useState<CommunityFeedItem[]>([])
  // Carica tutti i nomi dei satelliti una volta sola all'avvio
  useEffect(() => {
    let cancelled = false
    fetchAndMapSatelliteNames('ALL')
      .then(({ map }) => {
        if (!cancelled) {
          setSatelliteNames(map)
        }
      })
      .catch(err => {
        console.error('Errore fetchAndMapSatelliteNames', err)
      })
    return () => { cancelled = true }
  }, [])

  // Aggiorna la mappa solo se arriva un nuovo thread satellite non presente
  useEffect(() => {
    // Prendi tutti i targetId dei thread satellite
    const allTargetIds = [
      ...featuredThreads,
      ...allThreads,
    ]
      .filter(t => t.targetType === 'SATELLITE')
      .map(t => String(t.targetId))

    // Trova i targetId non presenti nella mappa
    const missingIds = allTargetIds.filter(id => !(id in satelliteNames))
    if (missingIds.length === 0) return

    // Usa helper per aggiornare solo i satelliti mancanti
    updateMissingSatelliteNames(missingIds, setSatelliteNames)
  }, [featuredThreads, allThreads, satelliteNames])
  // ...existing code...
  const [threadsError, setThreadsError] = useState('')
  const [activeThread, setActiveThread] = useState<CommunityThread | null>(null)
  const [comments, setComments] = useState<CommunityComment[]>([])
  const [commentsLoading, setCommentsLoading] = useState(false)
  const [commentsError, setCommentsError] = useState('')
  const [newCommentBody, setNewCommentBody] = useState('')
  const [postingComment, setPostingComment] = useState(false)
  const [newThreadTitle, setNewThreadTitle] = useState('')
  const [newThreadBody, setNewThreadBody] = useState('')
  const [postingThread, setPostingThread] = useState(false)
  const [editingCommentId, setEditingCommentId] = useState<number | null>(null)
  const [editingBody, setEditingBody] = useState('')
  const [sessionVerified, setSessionVerified] = useState(false)
  const [, setCommunitySessionValid] = useState(false)
  const [replyToComment, setReplyToComment] = useState<CommunityComment | null>(null)
  const [activeThreadOpen, setActiveThreadOpen] = useState(true)
  const [featuredOpen, setFeaturedOpen] = useState(false)
  const [allOpen, setAllOpen] = useState(false)
  const [composeOpen, setComposeOpen] = useState(false)
  const activeThreadRef = useRef<HTMLDivElement | null>(null)

  const activeTarget = useMemo(() => {
    if (!selectedSatelliteId) {
      return null
    }

    return {
      targetType: 'SATELLITE',
      targetId: String(selectedSatelliteId),
      label: selectedSatelliteName ?? `SAT-${selectedSatelliteId}`,
    }
  }, [selectedSatelliteId, selectedSatelliteName])

  const isUnauthorizedError = (error: unknown) =>
    isAxiosError(error) && error.response?.status === 401

  const handleUnauthorizedSession = useCallback(() => {
    setCommunitySessionValid(false)
    setThreadsError('Sessione scaduta. Esegui di nuovo l\'accesso.')
    setCommentsError('Sessione scaduta. Esegui di nuovo l\'accesso.')
  }, [])

  const handleThreadAuthFailure = useCallback(
    async (fallbackMessage: string) => {
      try {
        const response = await getCurrentUser()
        if (response.authenticated && response.user) {
          setCommentsError(fallbackMessage)
          return
        }
      } catch {
        // Se anche /me fallisce, allora la sessione è davvero scaduta.
      }

      handleUnauthorizedSession()
    },
    [handleUnauthorizedSession],
  )

  useEffect(() => {
    if (!authUser) {
      setSessionVerified(true)
      setCommunitySessionValid(false)
      setThreadsError('')
      setCommentsError('')
      return
    }

    setSessionVerified(true)
    setCommunitySessionValid(true)
    setThreadsError('')
    setCommentsError('')
  }, [authUser])

  useEffect(() => {
    setActiveThread(null)
    setComments([])
    setCommentsError('')
    setReplyToComment(null)
    setActiveThreadOpen(false)
    setCommentsLoading(false)
  }, [activeTarget?.targetId, activeTarget?.targetType])


  // Polling per aggiornare la lista thread ogni 5 secondi

  // Polling ottimizzato: aggiorna solo se cambia
  useEffect(() => {
    if (!authUser) {
      setFeaturedThreads([])
      setAllThreads([])
      setActiveThread(null)
      setThreadsError('')
      setCommentsError('')
      return
    }

    if (!sessionVerified) {
      return
    }

    let cancelled = false
    const controller = new AbortController()
    const fetchThreads = () => {
      Promise.all([
        fetchFeaturedCommunityThreads(8, controller.signal),
        fetchCommunityFeed(40, controller.signal),
      ])
        .then(([featured, all]) => {
          if (!cancelled) {
            // Aggiorna solo se cambia il contenuto
            setFeaturedThreads(prev => JSON.stringify(prev) !== JSON.stringify(featured) ? featured : prev)
            setAllThreads(prev => JSON.stringify(prev) !== JSON.stringify(all) ? all : prev)
          }
        })
        .catch((error) => {
          if (!controller.signal.aborted) {
            if (isUnauthorizedError(error)) {
              handleUnauthorizedSession()
              return
            }
            setThreadsError('Impossibile caricare i thread community.')
          }
        })
    }

    fetchThreads()
    const interval = setInterval(fetchThreads, 5000)

    return () => {
      cancelled = true
      controller.abort()
      clearInterval(interval)
    }
  }, [authUser, sessionVerified])

  const loadThread = async (targetType: string, targetId: string) => {
    if (!authUser) {
      return
    }

    setCommentsLoading(true)
    setCommentsError('')
    try {
      // Se il thread è di tipo SATELLITE, aggiorna la mappa dei nomi
      if (targetType === 'SATELLITE') {
        const { map } = await fetchAndMapSatelliteNames('ALL')
        setSatelliteNames(map)
      }
      const payload = await fetchCommunityThread(targetType, targetId)
      setActiveThread(payload.thread)
      setComments(payload.comments)
    } catch (error) {
      if (isUnauthorizedError(error)) {
        void handleThreadAuthFailure('Impossibile caricare i commenti del thread selezionato.')
        return
      }
      // 404 means no thread exists yet for this target: treat silently
      if (isAxiosError(error) && error.response?.status === 404) {
        setActiveThread(null)
        setComments([])
      } else {
        setActiveThread(null)
        setComments([])
        setCommentsError('Impossibile caricare i commenti del thread selezionato.')
      }
    } finally {
      setCommentsLoading(false)
    }
  }

  const ensureThread = async (targetType: string, targetId: string) => {
    if (!authUser) {
      return
    }

    setCommentsLoading(true)
    setCommentsError('')
    try {
      // Se il thread è di tipo SATELLITE, aggiorna la mappa dei nomi
      if (targetType === 'SATELLITE') {
        const { map } = await fetchAndMapSatelliteNames('ALL')
        setSatelliteNames(map)
      }
      const payload = await ensureCommunityThread(targetType, targetId)
      setActiveThread(payload.thread)
      setComments(payload.comments)
      setActiveThreadOpen(true)
    } catch (error) {
      if (isUnauthorizedError(error)) {
        void handleThreadAuthFailure('Impossibile aprire o creare il thread del satellite selezionato.')
        return
      }
      setActiveThread(null)
      setComments([])
      setCommentsError('Impossibile aprire o creare il thread del satellite selezionato.')
    } finally {
      setCommentsLoading(false)
    }
  }

  useEffect(() => {
    if (!activeThread) {
      return
    }

    setActiveThreadOpen(true)
    setTimeout(() => {
      activeThreadRef.current?.scrollIntoView({ behavior: 'smooth', block: 'start' })
    }, 0)
  }, [activeThread?.id])

  const handleOpenThread = async (targetType: string, targetId: string) => {
    await loadThread(targetType, targetId)
    setActiveThreadOpen(true)
  }

  const applyLikeUpdateToCollections = (threadId: number, likesCount: number, likedByMe: boolean) => {
    const updater = (items: CommunityFeedItem[]) =>
      items.map((item) =>
        item.threadId === threadId
          ? { ...item, likesCount, likedByMe }
          : item,
      )
    setFeaturedThreads(updater)
    setAllThreads(updater)
    setActiveThread((prev) =>
      prev && prev.id === threadId
        ? { ...prev, likesCount, likedByMe }
        : prev,
    )
  }

  const handleToggleLike = async (threadId: number) => {
    try {
      const updated = await toggleCommunityThreadLike(threadId)
      applyLikeUpdateToCollections(updated.threadId, updated.likesCount, updated.likedByMe)
    } catch (error) {
      if (isUnauthorizedError(error)) {
        handleUnauthorizedSession()
        return
      }
      setThreadsError('Like non aggiornato. Riprova.')
    }
  }

  const handleCreateGeneralThread = async () => {
    if (!authUser) {
      return
    }

    if (!newThreadTitle.trim() || !newThreadBody.trim() || postingThread) {
      return
    }

    setPostingThread(true)
    setThreadsError('')
    try {
      const created = await createCommunityThread({
        title: newThreadTitle,
        body: newThreadBody,
      })

      const newItem: CommunityFeedItem = {
        threadId: created.thread.id,
        targetType: created.thread.targetType,
        targetId: created.thread.targetId,
        title: created.thread.title,
        commentCount: created.thread.commentCount,
        likesCount: created.thread.likesCount,
        likedByMe: created.thread.likedByMe,
        lastCommentAt: created.thread.lastCommentAt ?? created.thread.createdAt,
        lastCommentPreview: created.comments[0]?.body ?? '',
      }

      setAllThreads((prev) => [{ ...newItem }, ...prev])
      setActiveThread(created.thread)
      setComments(created.comments)
      setNewThreadTitle('')
      setNewThreadBody('')
    } catch (error) {
      if (isUnauthorizedError(error)) {
        handleUnauthorizedSession()
        return
      }
      setThreadsError('Creazione thread non riuscita.')
    } finally {
      setPostingThread(false)
    }
  }

  const handleSubmitComment = async () => {
    if (!authUser) {
      return
    }

    if (!activeThread || !newCommentBody.trim() || postingComment) {
      return
    }

    setPostingComment(true)
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
      setAllThreads((prev) =>
        prev.map((item) =>
          item.threadId === activeThread.id
            ? {
                ...item,
                commentCount: item.commentCount + 1,
                lastCommentAt: created.createdAt,
                lastCommentPreview: created.body,
              }
            : item,
        ),
      )
      setActiveThread((prev) =>
        prev
          ? {
              ...prev,
              commentCount: prev.commentCount + 1,
              lastCommentAt: created.createdAt,
            }
          : prev,
      )
    } catch (error) {
      if (isUnauthorizedError(error)) {
        handleUnauthorizedSession()
        return
      }
      setCommentsError('Invio commento non riuscito. Riprova.')
    } finally {
      setPostingComment(false)
    }
  }

  const handleDeleteComment = async (commentId: number) => {
    try {
      await deleteCommunityComment(commentId)
      setComments((prev) =>
        prev.map((item) =>
          item.id === commentId
            ? { ...item, deleted: true, body: '[commento rimosso]' }
            : item,
        ),
      )
    } catch (error) {
      if (isUnauthorizedError(error)) {
        handleUnauthorizedSession()
        return
      }
      setCommentsError('Impossibile eliminare il commento selezionato.')
    }
  }

  const handleSaveEdit = async (commentId: number) => {
    if (!editingBody.trim()) {
      return
    }

    try {
      const updated = await updateCommunityComment(commentId, editingBody)
      setComments((prev) => prev.map((item) => (item.id === commentId ? updated : item)))
      setEditingCommentId(null)
      setEditingBody('')
    } catch (error) {
      if (isUnauthorizedError(error)) {
        handleUnauthorizedSession()
        return
      }
      setCommentsError('Modifica commento non riuscita.')
    }
  }

  const handleReportComment = async (commentId: number) => {
    try {
      await reportCommunityComment(commentId, 'Contenuto non appropriato')
    } catch (error) {
      if (isUnauthorizedError(error)) {
        handleUnauthorizedSession()
        return
      }
      setCommentsError('Segnalazione non inviata. Riprova.')
    }
  }

  if (!authUser) {
    return (
      <section className="collapsible side-drawer community-panel" aria-label="Community">
        <h3>Community</h3>
        <p className="updated-at">Accedi per usare commenti e feed community.</p>
      </section>
    )
  }

  if (!sessionVerified) {
    return (
      <section className="collapsible side-drawer community-panel" aria-label="Community">
        <h3>Community</h3>
        <p className="updated-at">Verifica sessione in corso...</p>
      </section>
    )
  }

  return (
    <section className="collapsible side-drawer community-panel" aria-label="Community">
      <h3>Community</h3>
      <p className="updated-at">Thread in evidenza, tutti i thread, discussioni libere e like.</p>



      {/* Thread attivo in cima, ora come componente */}
      <div className="community-section" ref={activeThreadRef}>
        <button
          type="button"
          className="community-section-toggle"
          onClick={() => setActiveThreadOpen((prev) => !prev)}
        >
          {activeThread
            ? `Thread attivo: ${activeThread.title} ${activeThreadOpen ? '▾' : '▸'}`
            : `Thread attivo ${activeThreadOpen ? '▾' : '▸'}`}
        </button>
        {activeThreadOpen ? (
          <CommunityThreadCard
            activeThread={activeThread}
            activeTarget={activeTarget}
            selectedSatelliteName={selectedSatelliteName}
            comments={comments}
            commentsLoading={commentsLoading}
            commentsError={commentsError}
            authUser={authUser}
            replyToComment={replyToComment}
            editingCommentId={editingCommentId}
            editingBody={editingBody}
            postingComment={postingComment}
            newCommentBody={newCommentBody}
            setReplyToComment={setReplyToComment}
            setEditingCommentId={setEditingCommentId}
            setEditingBody={setEditingBody}
            setNewCommentBody={setNewCommentBody}
            handleToggleLike={handleToggleLike}
            handleSubmitComment={handleSubmitComment}
            handleSaveEdit={handleSaveEdit}
            handleDeleteComment={handleDeleteComment}
            handleReportComment={handleReportComment}
            ensureThread={ensureThread}
            onFocusSatellite={onFocusSatellite}
          />
        ) : null}
      </div>

      <div className="community-section">
        <button
          type="button"
          className="community-section-toggle"
          onClick={() => setComposeOpen((prev) => !prev)}
        >
          Nuovo thread generale {composeOpen ? '▾' : '▸'}
        </button>
        {composeOpen ? (
          <CommunityCompose
            newThreadTitle={newThreadTitle}
            setNewThreadTitle={setNewThreadTitle}
            newThreadBody={newThreadBody}
            setNewThreadBody={setNewThreadBody}
            postingThread={postingThread}
            handleCreateGeneralThread={handleCreateGeneralThread}
          />
        ) : null}
      </div>

      <div className="community-section">
        <button
          type="button"
          className="community-section-toggle"
          onClick={() => setFeaturedOpen((prev) => !prev)}
        >
          Thread in evidenza ({featuredThreads.length}) {featuredOpen ? '▾' : '▸'}
        </button>
        {featuredOpen ? (
          <CommunityFeedCard
            title="Thread in evidenza"
            threadsError={threadsError}
            items={featuredThreads}
            satelliteNames={satelliteNames}
            onOpenThread={handleOpenThread}
            onToggleLike={handleToggleLike}
            onFocusSatellite={onFocusSatellite}
            featured
          />
        ) : null}
      </div>

      <div className="community-section">
        <button
          type="button"
          className="community-section-toggle"
          onClick={() => setAllOpen((prev) => !prev)}
        >
          Tutti i thread ({allThreads.length}) {allOpen ? '▾' : '▸'}
        </button>
        {allOpen ? (
          <CommunityFeedCard
            title="Tutti i thread creati"
            threadsError={threadsError}
            items={allThreads}
            satelliteNames={satelliteNames}
            onOpenThread={handleOpenThread}
            onToggleLike={handleToggleLike}
            onFocusSatellite={onFocusSatellite}
          />
        ) : null}
      </div>
    </section>
  )
}
