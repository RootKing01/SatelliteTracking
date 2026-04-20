import { useCallback, useEffect, useMemo, useState } from 'react'
import { fetchSatelliteCatalogByType } from '../../api/satelliteCatalogClient'
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
}

export function CommunityPanel({
  authUser,
  selectedSatelliteId,
  selectedSatelliteName,
}: CommunityPanelProps) {
  const [featuredThreads, setFeaturedThreads] = useState<CommunityFeedItem[]>([])
  const [allThreads, setAllThreads] = useState<CommunityFeedItem[]>([])
  const [satelliteNames, setSatelliteNames] = useState<Record<string, string>>({})
    // Carica tutti i nomi dei satelliti una volta sola
    useEffect(() => {
      let cancelled = false
      fetchSatelliteCatalogByType('ALL').then(list => {
        if (!cancelled) {
          const map: Record<string, string> = {}
          for (const sat of list) {
            map[String(sat.id)] = sat.objectName
            map[String(sat.noradCatId)] = sat.objectName
            if (sat.objectId) map[String(sat.objectId)] = sat.objectName
          }
          setSatelliteNames(map)
        }
      })
      return () => { cancelled = true }
    }, [])
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
  const [communitySessionValid, setCommunitySessionValid] = useState(false)
  const [replyToComment, setReplyToComment] = useState<CommunityComment | null>(null)

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

  useEffect(() => {
    if (!authUser) {
      setSessionVerified(true)
      setCommunitySessionValid(false)
      return
    }

    let cancelled = false
    setSessionVerified(false)

    void getCurrentUser()
      .then((response) => {
        if (cancelled) {
          return
        }

        const isValid = Boolean(response.authenticated && response.user)
        setCommunitySessionValid(isValid)
        if (!isValid) {
          setThreadsError('Sessione scaduta. Esegui di nuovo l\'accesso.')
          setCommentsError('Sessione scaduta. Esegui di nuovo l\'accesso.')
        }
      })
      .catch(() => {
        if (cancelled) {
          return
        }
        setCommunitySessionValid(false)
        setThreadsError('Sessione non verificabile. Esegui di nuovo l\'accesso.')
      })
      .finally(() => {
        if (!cancelled) {
          setSessionVerified(true)
        }
      })

    return () => {
      cancelled = true
    }
  }, [authUser])


  // Polling per aggiornare la lista thread ogni 5 secondi

  // Polling ottimizzato: aggiorna solo se cambia
  useEffect(() => {
    if (!authUser || !sessionVerified || !communitySessionValid) {
      setFeaturedThreads([])
      setAllThreads([])
      setActiveThread(null)
      if (!communitySessionValid && sessionVerified) {
        setThreadsError('Sessione scaduta. Esegui di nuovo l\'accesso.')
      } else {
        setThreadsError('')
      }
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
  }, [authUser, communitySessionValid, sessionVerified])

  const loadThread = async (targetType: string, targetId: string) => {
    if (!authUser || !communitySessionValid) {
      setCommentsError('Sessione scaduta. Esegui di nuovo l\'accesso.')
      return
    }

    setCommentsLoading(true)
    setCommentsError('')
    try {
      const payload = await fetchCommunityThread(targetType, targetId)
      setActiveThread(payload.thread)
      setComments(payload.comments)
    } catch (error) {
      if (isUnauthorizedError(error)) {
        handleUnauthorizedSession()
        return
      }
      setActiveThread(null)
      setComments([])
      setCommentsError('Impossibile caricare i commenti del thread selezionato.')
    } finally {
      setCommentsLoading(false)
    }
  }

  const ensureThread = async (targetType: string, targetId: string) => {
    if (!authUser || !communitySessionValid) {
      setCommentsError('Sessione scaduta. Esegui di nuovo l\'accesso.')
      return
    }

    setCommentsLoading(true)
    setCommentsError('')
    try {
      const payload = await ensureCommunityThread(targetType, targetId)
      setActiveThread(payload.thread)
      setComments(payload.comments)
    } catch (error) {
      if (isUnauthorizedError(error)) {
        handleUnauthorizedSession()
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
    if (!activeTarget || !authUser) {
      return
    }
    void loadThread(activeTarget.targetType, activeTarget.targetId)
  }, [activeTarget?.targetId, activeTarget?.targetType, authUser?.id])

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
    if (!communitySessionValid) {
      setThreadsError('Sessione scaduta. Esegui di nuovo l\'accesso.')
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
    if (!communitySessionValid) {
      setCommentsError('Sessione scaduta. Esegui di nuovo l\'accesso.')
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



      {/* Thread attivo in cima, identico a quello originale */}
      <div className="community-thread-card">

        <strong>
          {activeThread
            ? (
                activeThread.targetType === 'SATELLITE' && selectedSatelliteName
                  ? `Thread attivo: ${selectedSatelliteName}`
                  : `Thread attivo: ${activeThread.title}`
              )
            : activeTarget
              ? `Nessun thread aperto. Premi "Apri o crea thread satellite" per ${activeTarget.label}`
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
            disabled={commentsLoading}
            onClick={() => {
              void ensureThread(activeTarget.targetType, activeTarget.targetId)
            }}
          >
            {commentsLoading ? 'Caricamento thread...' : 'Apri o crea thread satellite corrente'}
          </button>
        ) : null}

        {commentsError ? <p className="community-error">{commentsError}</p> : null}
        {!activeThread && commentsLoading ? <p className="updated-at">Caricamento thread...</p> : null}

        {activeThread ? (
          <>
            <div className="community-compose">
              {replyToComment && (
                <div className="community-reply-indicator">
                  Risposta a <strong>{replyToComment.authorUsername}</strong>
                  <button
                    type="button"
                    className="community-cancel-reply"
                    onClick={() => setReplyToComment(null)}
                  >
                    Annulla risposta
                  </button>
                </div>
              )}
              <textarea
                rows={3}
                value={newCommentBody}
                onChange={(event) => setNewCommentBody(event.target.value)}
                placeholder={replyToComment ? `Rispondi a ${replyToComment.authorUsername}` : 'Scrivi un commento costruttivo per la community'}
              />
              <button type="button" onClick={() => void handleSubmitComment()} disabled={postingComment || !newCommentBody.trim()}>
                {postingComment ? 'Invio...' : 'Pubblica'}
              </button>
            </div>
            {commentsLoading ? <p className="updated-at">Caricamento thread...</p> : null}

            {!commentsLoading && comments.length === 0 ? (
              <p className="updated-at">Nessun commento su questo target.</p>
            ) : (
              <div className="community-comment-list">
                {comments.map((comment) => {
                  const isOwnComment = comment.authorId === authUser.id
                  const isEditing = editingCommentId === comment.id

                  // Trova il commento padre se esiste
                  let parent = null
                  if (comment.parentCommentId) {
                    parent = comments.find(c => c.id === comment.parentCommentId)
                  }

                  return (
                    <article key={comment.id} className={`community-comment-item ${comment.deleted ? 'is-deleted' : ''}`}>
                      <div className="community-comment-head">
                        <strong>{comment.authorUsername}</strong>
                        <small>{new Date(comment.createdAt).toLocaleString('it-IT')}</small>
                      </div>

                      {/* Mostra a chi si sta rispondendo */}
                      {parent && (
                        <div className="community-reply-indicator" style={{marginBottom: 4}}>
                          Risposta a <strong>{parent.authorUsername}</strong>
                          <span style={{color:'#9bbad6', fontSize:'0.75em', marginLeft:4}}>
                            “{parent.body.length > 60 ? parent.body.slice(0, 57) + '…' : parent.body}”
                          </span>
                        </div>
                      )}

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
                          <button
                            type="button"
                            onClick={() => setReplyToComment(comment)}
                          >
                            Rispondi
                          </button>
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

      {/* Nuovo thread libero sempre sotto */}
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


      {/* Thread in evidenza: ordinati per numero di messaggi */}
      <div className="community-feed-card">
        <strong>Thread in evidenza</strong>
        {/* <small>Caricamento thread...</small> */}
        {threadsError ? <small className="community-error">{threadsError}</small> : null}
        {!threadsError && featuredThreads.length === 0 ? (
          <small>Nessun thread in evidenza disponibile.</small>
        ) : (
          <div className="community-feed-list">
            {[...featuredThreads]
              .sort((a, b) => b.commentCount - a.commentCount)
              .map((item) => (
                <article key={`featured-${item.threadId}`} className="community-feed-item">
                  <strong>
                    {(item.targetType === 'SATELLITE' || item.targetType === 'SIGHTING') && satelliteNames[item.targetId]
                      ? satelliteNames[item.targetId]
                      : item.title}
                  </strong>
                  <small>
                    {(item.targetType === 'SATELLITE' || item.targetType === 'SIGHTING') && satelliteNames[item.targetId]
                      ? `${item.targetType} (${satelliteNames[item.targetId]})`
                      : `${item.targetType} #${item.targetId}`}
                  </small>
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

      {/* Tutti i thread creati */}
      <div className="community-feed-card">
        <strong>Tutti i thread creati</strong>
        {!threadsError && allThreads.length === 0 ? (
          <small>Nessun thread disponibile.</small>
        ) : (
          <div className="community-feed-list">
            {allThreads.map((item) => (
              <article key={`all-${item.threadId}`} className="community-feed-item">
                <strong>
                  {item.targetType === 'SATELLITE' && selectedSatelliteId && Number(item.targetId) === selectedSatelliteId && selectedSatelliteName
                    ? selectedSatelliteName
                    : (item.targetType === 'SATELLITE' && satelliteNames[item.targetId]
                        ? satelliteNames[item.targetId]
                        : (item.targetType === 'SIGHTING' || item.targetType === 'PASS') && satelliteNames[item.targetId]
                          ? satelliteNames[item.targetId]
                          : item.title)}
                </strong>
                <small>
                  {item.targetType === 'SATELLITE' && selectedSatelliteId && Number(item.targetId) === selectedSatelliteId && selectedSatelliteName
                    ? `SATELLITE (${selectedSatelliteName})`
                    : (item.targetType === 'SATELLITE' && satelliteNames[item.targetId]
                        ? `SATELLITE (${satelliteNames[item.targetId]})`
                        : (item.targetType === 'SIGHTING' || item.targetType === 'PASS') && satelliteNames[item.targetId]
                          ? `${item.targetType} (${satelliteNames[item.targetId]})`
                          : `${item.targetType} #${item.targetId}`)}
                </small>

                {/* Aggiorna la mappa locale se manca il nome e il thread è quello selezionato */}
                {item.targetType === 'SATELLITE' && selectedSatelliteId && Number(item.targetId) === selectedSatelliteId && selectedSatelliteName && !satelliteNames[item.targetId] && (
                  (() => { setSatelliteNames(prev => ({ ...prev, [item.targetId]: selectedSatelliteName })); return null })()
                )}
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
    </section>
  )
}
