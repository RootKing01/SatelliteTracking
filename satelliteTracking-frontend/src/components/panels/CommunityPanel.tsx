import { useEffect, useMemo, useState } from 'react'
import type { AuthUser } from '../../api/authClient'
import {
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
}

export function CommunityPanel({
  authUser,
  selectedSatelliteId,
  selectedSatelliteName,
}: CommunityPanelProps) {
  const [featuredThreads, setFeaturedThreads] = useState<CommunityFeedItem[]>([])
  const [allThreads, setAllThreads] = useState<CommunityFeedItem[]>([])
  const [threadsLoading, setThreadsLoading] = useState(false)
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

  useEffect(() => {
    if (!authUser) {
      setFeaturedThreads([])
      setAllThreads([])
      setThreadsError('')
      setActiveThread(null)
      return
    }

    const controller = new AbortController()
    setThreadsLoading(true)
    setThreadsError('')

    void Promise.all([
      fetchFeaturedCommunityThreads(8, controller.signal),
      fetchCommunityFeed(40, controller.signal),
    ])
      .then(([featured, all]) => {
        setFeaturedThreads(featured)
        setAllThreads(all)
      })
      .catch(() => {
        if (!controller.signal.aborted) {
          setThreadsError('Impossibile caricare i thread community.')
        }
      })
      .finally(() => {
        if (!controller.signal.aborted) {
          setThreadsLoading(false)
        }
      })

    return () => {
      controller.abort()
    }
  }, [authUser])

  const loadThread = async (targetType: string, targetId: string) => {
    if (!authUser) {
      return
    }

    setCommentsLoading(true)
    setCommentsError('')
    try {
      const payload = await fetchCommunityThread(targetType, targetId)
      setActiveThread(payload.thread)
      setComments(payload.comments)
    } catch {
      setCommentsError('Impossibile caricare i commenti del thread selezionato.')
    } finally {
      setCommentsLoading(false)
    }
  }

  useEffect(() => {
    if (!activeTarget || !authUser) {
      return
    }
    void loadThread(activeTarget.targetType, activeTarget.targetId)
  }, [activeTarget?.targetId, authUser?.id])

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
    } catch {
      setThreadsError('Like non aggiornato. Riprova.')
    }
  }

  const handleCreateGeneralThread = async () => {
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

      setAllThreads((prev) => [newItem, ...prev])
      setActiveThread(created.thread)
      setComments(created.comments)
      setNewThreadTitle('')
      setNewThreadBody('')
    } catch {
      setThreadsError('Creazione thread non riuscita.')
    } finally {
      setPostingThread(false)
    }
  }

  const handleSubmitComment = async () => {
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
      })

      setComments((prev) => [...prev, created])
      setNewCommentBody('')
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
    } catch {
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
    } catch {
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
    } catch {
      setCommentsError('Modifica commento non riuscita.')
    }
  }

  const handleReportComment = async (commentId: number) => {
    try {
      await reportCommunityComment(commentId, 'Contenuto non appropriato')
    } catch {
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

  return (
    <section className="collapsible side-drawer community-panel" aria-label="Community">
      <h3>Community</h3>
      <p className="updated-at">Thread in evidenza, tutti i thread, discussioni libere e like.</p>

      <div className="community-thread-card">
        <strong>Nuovo thread libero</strong>
        <div className="community-compose">
          <input
            type="text"
            value={newThreadTitle}
            onChange={(event) => setNewThreadTitle(event.target.value)}
            placeholder="Titolo thread (es. Miglior setup per osservazione urbana)"
          />
          <textarea
            rows={3}
            value={newThreadBody}
            onChange={(event) => setNewThreadBody(event.target.value)}
            placeholder="Apri una discussione non legata a un satellite specifico"
          />
          <button
            type="button"
            onClick={() => {
              void handleCreateGeneralThread()
            }}
            disabled={postingThread || !newThreadTitle.trim() || !newThreadBody.trim()}
          >
            {postingThread ? 'Creazione...' : 'Crea thread'}
          </button>
        </div>
      </div>

      <div className="community-feed-card">
        <strong>Thread in evidenza</strong>
        {threadsLoading ? <small>Caricamento thread...</small> : null}
        {threadsError ? <small className="community-error">{threadsError}</small> : null}
        {!threadsLoading && !threadsError && featuredThreads.length === 0 ? (
          <small>Nessun thread in evidenza disponibile.</small>
        ) : (
          <div className="community-feed-list">
            {featuredThreads.map((item) => (
              <article key={`featured-${item.threadId}`} className="community-feed-item">
                <strong>{item.title}</strong>
                <small>{item.targetType} #{item.targetId}</small>
                <small>{item.commentCount} commenti · {item.likesCount} like</small>
                <small>
                  {item.lastCommentAt
                    ? new Date(item.lastCommentAt).toLocaleString('it-IT')
                    : 'Nessun commento recente'}
                </small>
                <p>{item.lastCommentPreview}</p>
                <div className="community-inline-actions">
                  <button
                    type="button"
                    onClick={() => {
                      void loadThread(item.targetType, item.targetId)
                    }}
                  >
                    Apri thread
                  </button>
                  <button
                    type="button"
                    className={item.likedByMe ? 'community-like-active' : ''}
                    onClick={() => {
                      void handleToggleLike(item.threadId)
                    }}
                  >
                    {item.likedByMe ? 'Unlike' : 'Like'} ({item.likesCount})
                  </button>
                </div>
              </article>
            ))}
          </div>
        )}
      </div>

      <div className="community-feed-card">
        <strong>Tutti i thread</strong>
        {!threadsLoading && !threadsError && allThreads.length === 0 ? (
          <small>Nessun thread disponibile.</small>
        ) : (
          <div className="community-feed-list">
            {allThreads.map((item) => (
              <article key={`all-${item.threadId}`} className="community-feed-item">
                <strong>{item.title}</strong>
                <small>{item.targetType} #{item.targetId}</small>
                <small>{item.commentCount} commenti · {item.likesCount} like</small>
                <p>{item.lastCommentPreview}</p>
                <div className="community-inline-actions">
                  <button
                    type="button"
                    onClick={() => {
                      void loadThread(item.targetType, item.targetId)
                    }}
                  >
                    Apri
                  </button>
                  <button
                    type="button"
                    className={item.likedByMe ? 'community-like-active' : ''}
                    onClick={() => {
                      void handleToggleLike(item.threadId)
                    }}
                  >
                    {item.likedByMe ? 'Unlike' : 'Like'} ({item.likesCount})
                  </button>
                </div>
              </article>
            ))}
          </div>
        )}
      </div>

      <div className="community-thread-card">
        <strong>
          {activeThread
            ? `Thread attivo: ${activeThread.title}`
            : activeTarget
              ? `Nessun thread aperto. Premi "Apri thread satellite" per ${activeTarget.label}`
              : 'Seleziona un thread dall\'elenco oppure un satellite'}
        </strong>

        {activeThread ? (
          <div className="community-inline-actions">
            <button
              type="button"
              className={activeThread.likedByMe ? 'community-like-active' : ''}
              onClick={() => {
                void handleToggleLike(activeThread.id)
              }}
            >
              {activeThread.likedByMe ? 'Unlike' : 'Like'} ({activeThread.likesCount})
            </button>
          </div>
        ) : null}

        {activeTarget ? (
          <button
            type="button"
            onClick={() => {
              void loadThread(activeTarget.targetType, activeTarget.targetId)
            }}
          >
            Apri thread satellite corrente
          </button>
        ) : null}

        {activeThread ? (
          <>
            <div className="community-compose">
              <textarea
                rows={3}
                value={newCommentBody}
                onChange={(event) => setNewCommentBody(event.target.value)}
                placeholder="Scrivi un commento costruttivo per la community"
              />
              <button type="button" onClick={() => void handleSubmitComment()} disabled={postingComment || !newCommentBody.trim()}>
                {postingComment ? 'Invio...' : 'Pubblica'}
              </button>
            </div>

            {commentsError ? <p className="community-error">{commentsError}</p> : null}
            {commentsLoading ? <p className="updated-at">Caricamento thread...</p> : null}

            {!commentsLoading && comments.length === 0 ? (
              <p className="updated-at">Nessun commento su questo target.</p>
            ) : (
              <div className="community-comment-list">
                {comments.map((comment) => {
                  const isOwnComment = comment.authorId === authUser.id
                  const isEditing = editingCommentId === comment.id

                  return (
                    <article key={comment.id} className={`community-comment-item ${comment.deleted ? 'is-deleted' : ''}`}>
                      <div className="community-comment-head">
                        <strong>{comment.authorUsername}</strong>
                        <small>{new Date(comment.createdAt).toLocaleString('it-IT')}</small>
                      </div>

                      {isEditing ? (
                        <div className="community-edit-box">
                          <textarea
                            rows={3}
                            value={editingBody}
                            onChange={(event) => setEditingBody(event.target.value)}
                          />
                          <div className="community-inline-actions">
                            <button type="button" onClick={() => void handleSaveEdit(comment.id)}>
                              Salva
                            </button>
                            <button
                              type="button"
                              onClick={() => {
                                setEditingCommentId(null)
                                setEditingBody('')
                              }}
                            >
                              Annulla
                            </button>
                          </div>
                        </div>
                      ) : (
                        <p>{comment.body}</p>
                      )}

                      {!comment.deleted && !isEditing ? (
                        <div className="community-inline-actions">
                          {isOwnComment ? (
                            <>
                              <button
                                type="button"
                                onClick={() => {
                                  setEditingCommentId(comment.id)
                                  setEditingBody(comment.body)
                                }}
                              >
                                Modifica
                              </button>
                              <button type="button" onClick={() => void handleDeleteComment(comment.id)}>
                                Elimina
                              </button>
                            </>
                          ) : (
                            <button type="button" onClick={() => void handleReportComment(comment.id)}>
                              Segnala
                            </button>
                          )}
                        </div>
                      ) : null}
                    </article>
                  )
                })}
              </div>
            )}
          </>
        ) : null}
      </div>
    </section>
  )
}
