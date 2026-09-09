import { useCallback, useEffect, useMemo, useRef, useState } from 'react'
import { fetchAndMapSatelliteNames } from '../../helpers/satelliteNameHelper'
import { useCommunityData } from '../../helpers/useCommunityData'
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
  fetchCommunityThread,
  markCommunityNotificationAsRead,
  reportCommunityComment,
  updateCommunityThreadReadState,
  toggleCommunityThreadLike,
  updateCommunityComment,
  type CommunityComment,
  type CommunityFeedItem,
  type CommunityNotification,
  type CommunityThread,
  type CommunityThreadWithComments,
  type CommunityThreadReadState,
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
  const [notificationsOpen, setNotificationsOpen] = useState(false)
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
  const [replyToComment, setReplyToComment] = useState<CommunityComment | null>(null)
  const [activeThreadReadState, setActiveThreadReadState] = useState<CommunityThreadReadState | null>(null)
  const [pendingFocusCommentId, setPendingFocusCommentId] = useState<number | null>(null)
  const [activeThreadOpen, setActiveThreadOpen] = useState(true)
  const [featuredOpen, setFeaturedOpen] = useState(false)
  const [allOpen, setAllOpen] = useState(false)
  const [composeOpen, setComposeOpen] = useState(false)
  const activeThreadRef = useRef<HTMLDivElement | null>(null)

  const handleUnauthorizedSession = useCallback(() => {
    setCommentsError('Sessione scaduta. Esegui di nuovo l\'accesso.')
  }, [])

  const {
    satelliteNames,
    setSatelliteNames,
    featuredThreads,
    setFeaturedThreads,
    allThreads,
    setAllThreads,
    threadsError,
    setThreadsError,
    notifications,
    setNotifications,
    setNotificationCount,
  } = useCommunityData({
    authUser,
    sessionVerified: Boolean(authUser),
    notificationsOpen,
    onUnauthorized: handleUnauthorizedSession,
  })

  // Listen for global toggle events dispatched by top bar
  useEffect(() => {
    const handler = () => setNotificationsOpen((prev) => !prev)
    window.addEventListener('toggleCommunityNotifications', handler)
    return () => window.removeEventListener('toggleCommunityNotifications', handler)
  }, [])

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
    queueMicrotask(() => {
      setActiveThread(null)
      setComments([])
      setCommentsError('')
      setReplyToComment(null)
      setActiveThreadReadState(null)
      setPendingFocusCommentId(null)
      setActiveThreadOpen(false)
      setCommentsLoading(false)
    })
  }, [activeTarget?.targetId, activeTarget?.targetType])

  const loadThreadData = async (
    request: () => Promise<CommunityThreadWithComments>,
    targetType: string,
    focusCommentId: number | null | undefined,
    unauthorizedMessage: string,
    notFoundIsEmpty: boolean,
  ) => {
    if (!authUser) {
      return false
    }

    setCommentsLoading(true)
    setCommentsError('')
    try {
      if (targetType === 'SATELLITE') {
        const { map } = await fetchAndMapSatelliteNames('ALL')
        setSatelliteNames(map)
      }
      const payload = await request()
      setActiveThread(payload.thread)
      setComments(payload.comments)
      setActiveThreadReadState(payload.readState)
      setPendingFocusCommentId(focusCommentId ?? payload.readState?.lastReadCommentId ?? payload.comments.at(-1)?.id ?? null)
      return true
    } catch (error) {
      if (isUnauthorizedError(error)) {
        void handleThreadAuthFailure(unauthorizedMessage)
        return false
      }
      if (notFoundIsEmpty && isAxiosError(error) && error.response?.status === 404) {
        setActiveThread(null)
        setComments([])
      } else {
        setActiveThread(null)
        setComments([])
        setCommentsError(unauthorizedMessage)
      }
      return false
    } finally {
      setCommentsLoading(false)
    }
  }

  const loadThread = async (targetType: string, targetId: string, focusCommentId?: number | null) => {
    await loadThreadData(
      () => fetchCommunityThread(targetType, targetId),
      targetType,
      focusCommentId,
      'Impossibile caricare i commenti del thread selezionato.',
      true,
    )
  }

  const ensureThread = async (targetType: string, targetId: string, focusCommentId?: number | null) => {
    const loaded = await loadThreadData(
      () => ensureCommunityThread(targetType, targetId),
      targetType,
      focusCommentId,
      'Impossibile aprire o creare il thread del satellite selezionato.',
      false,
    )
    if (loaded) {
      setActiveThreadOpen(true)
    }
  }

  const activeThreadId = activeThread?.id

  useEffect(() => {
    if (activeThreadId == null || pendingFocusCommentId == null) {
      return
    }

    const commentElement = document.getElementById(`community-comment-${pendingFocusCommentId}`)
    if (commentElement) {
      commentElement.scrollIntoView({ behavior: 'smooth', block: 'center' })
      queueMicrotask(() => setPendingFocusCommentId(null))
    }
  }, [activeThreadId, comments, pendingFocusCommentId])

  const scrollToComment = useCallback((commentId: number | null) => {
    if (commentId == null) {
      return
    }

    const element = document.getElementById(`community-comment-${commentId}`)
    element?.scrollIntoView({ behavior: 'smooth', block: 'center' })
  }, [])

  const handleOpenThread = async (targetType: string, targetId: string, focusCommentId?: number | null) => {
    await loadThread(targetType, targetId, focusCommentId)
    setActiveThreadOpen(true)
  }

  const handleResumeReading = useCallback(() => {
    scrollToComment(activeThreadReadState?.lastReadCommentId ?? null)
  }, [activeThreadReadState?.lastReadCommentId, scrollToComment])

  const handleGoToLatest = useCallback(async () => {
    if (!activeThread || comments.length === 0) {
      return
    }

    const latestComment = comments[comments.length - 1]
    try {
      const updated = await updateCommunityThreadReadState(activeThread.id, latestComment.id)
      setActiveThreadReadState(updated)
      scrollToComment(latestComment.id)
      setPendingFocusCommentId(null)
    } catch {
      setCommentsError('Impossibile aggiornare lo stato di lettura.')
    }
  }, [activeThread, comments, scrollToComment])

  const handleOpenNotification = async (notification: CommunityNotification) => {
    try {
      if (notification.readAt == null) {
        const updated = await markCommunityNotificationAsRead(notification.id)
        setNotifications((prev) => prev.map((item) => item.id === updated.id ? updated : item))
        setNotificationCount((prev) => Math.max(0, prev - 1))
      }
      await handleOpenThread(notification.targetType, notification.targetId, notification.sourceCommentId ?? null)
      setNotificationsOpen(false)
    } catch {
      setCommentsError('Impossibile aprire la notifica selezionata.')
    }
  }

  useEffect(() => {
    if (!activeThread?.id || !activeThreadReadState) {
      return
    }

    if (pendingFocusCommentId != null) {
      return
    }

    if (activeThreadReadState.lastReadCommentId != null) {
      scrollToComment(activeThreadReadState.lastReadCommentId)
    } else if (comments.length > 0) {
      scrollToComment(comments[comments.length - 1].id)
    }
  }, [activeThread?.id, activeThreadReadState, comments, pendingFocusCommentId, scrollToComment])

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
      setActiveThreadReadState(created.readState)
      setPendingFocusCommentId(created.readState?.lastReadCommentId ?? created.comments.at(-1)?.id ?? null)
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
      setPendingFocusCommentId(created.id)
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

  if (!authUser) {
    return (
      <section className="collapsible side-drawer community-panel" aria-label="Community">
        <h3>Community</h3>
        <p className="updated-at">Verifica sessione in corso...</p>
      </section>
    )
  }

  return (
    <section className="collapsible side-drawer community-panel" aria-label="Community">
      <div className="community-panel-header">
        <h3>Community</h3>
      </div>
      <p className="updated-at">Thread in evidenza, tutti i thread, discussioni libere e like.</p>

      {notificationsOpen ? (
        <div className={`community-notification-tray ${notificationsOpen ? 'is-open overlay' : ''}`}>
          {notifications.length === 0 ? (
            <small>Nessuna notifica disponibile.</small>
          ) : (
            notifications.map((notification) => (
              <button
                key={notification.id}
                type="button"
                className={`community-notification-item ${notification.readAt == null ? 'is-unread' : ''}`}
                onClick={() => { void handleOpenNotification(notification) }}
              >
                <strong>{notification.sourceCommentAuthorUsername ?? 'Utente'}</strong>
                <small>{notification.threadTitle}</small>
                <span>{notification.preview}</span>
              </button>
            ))
          )}
        </div>
      ) : null}

      {activeThread ? (
        <AutoSaveReadState
          threadId={activeThread.id}
          comments={comments}
          activeReadState={activeThreadReadState}
          setActiveThreadReadState={setActiveThreadReadState}
        />
      ) : null}

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
            readState={activeThreadReadState}
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
            handleResumeReading={handleResumeReading}
            handleGoToLatest={handleGoToLatest}
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

function AutoSaveReadState({
  threadId,
  comments,
  activeReadState,
  setActiveThreadReadState,
}: {
  threadId: number
  comments: CommunityComment[]
  activeReadState: CommunityThreadReadState | null
  setActiveThreadReadState: (s: CommunityThreadReadState | null) => void
}) {
  const timerRef = useRef<number | null>(null)

  useEffect(() => {
    if (!threadId || comments.length === 0) return

    const elements = Array.from(document.querySelectorAll('.community-comment-item')) as HTMLElement[]
    if (elements.length === 0) return

    const observer = new IntersectionObserver((entries) => {
      const visibleIds: number[] = entries
        .filter(e => e.isIntersecting && e.intersectionRatio > 0.5)
        .map(e => {
          const m = (e.target as HTMLElement).id.match(/community-comment-(\d+)/)
          return m ? Number(m[1]) : NaN
        })
        .filter(n => Number.isFinite(n))

      if (visibleIds.length === 0) return
      const maxVisible = Math.max(...visibleIds)

      if (activeReadState?.lastReadCommentId && maxVisible <= activeReadState.lastReadCommentId) return

      if (timerRef.current) {
        window.clearTimeout(timerRef.current)
      }
      timerRef.current = window.setTimeout(async () => {
        try {
          const updated = await updateCommunityThreadReadState(threadId, maxVisible)
          setActiveThreadReadState(updated)
        } catch {
          // ignore errors silently
        }
      }, 800)
    }, { threshold: [0.5] })

    elements.forEach(el => observer.observe(el))
    return () => {
      observer.disconnect()
      if (timerRef.current) window.clearTimeout(timerRef.current)
    }
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [threadId, comments.map(c => c.id).join(','), activeReadState?.lastReadCommentId])

  return null
}