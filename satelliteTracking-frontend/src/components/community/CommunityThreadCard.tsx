import React from 'react'
import type { CommunityThread, CommunityComment } from '../../api/communityClient'
import type { AuthUser } from '../../api/authClient'

interface CommunityThreadCardProps {
  activeThread: CommunityThread | null
  activeTarget: { targetType: string; targetId: string; label: string } | null
  selectedSatelliteName: string | null
  comments: CommunityComment[]
  commentsLoading: boolean
  commentsError: string
  authUser: AuthUser
  replyToComment: CommunityComment | null
  editingCommentId: number | null
  editingBody: string
  postingComment: boolean
  newCommentBody: string
  setReplyToComment: (c: CommunityComment | null) => void
  setEditingCommentId: (id: number | null) => void
  setEditingBody: (body: string) => void
  setNewCommentBody: (body: string) => void
  handleToggleLike: (threadId: number) => void
  handleSubmitComment: () => void
  handleSaveEdit: (commentId: number) => void
  handleDeleteComment: (commentId: number) => void
  handleReportComment: (commentId: number) => void
  ensureThread: (targetType: string, targetId: string) => void
}

export function CommunityThreadCard({
  activeThread,
  activeTarget,
  selectedSatelliteName,
  comments,
  commentsLoading,
  commentsError,
  authUser,
  replyToComment,
  editingCommentId,
  editingBody,
  postingComment,
  newCommentBody,
  setReplyToComment,
  setEditingCommentId,
  setEditingBody,
  setNewCommentBody,
  handleToggleLike,
  handleSubmitComment,
  handleSaveEdit,
  handleDeleteComment,
  handleReportComment,
  ensureThread,
}: CommunityThreadCardProps) {
  return (
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
              handleToggleLike(activeThread.id)
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
            if (activeTarget) ensureThread(activeTarget.targetType, activeTarget.targetId)
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
            <button type="button" onClick={handleSubmitComment} disabled={postingComment || !newCommentBody.trim()}>
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
                          {parent.body.length > 60 ? parent.body.slice(0, 57) + '' : parent.body}
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
                          <button type="button" onClick={() => handleSaveEdit(comment.id)}>
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
                            <button type="button" onClick={() => handleDeleteComment(comment.id)}>
                              Elimina
                            </button>
                          </>
                        ) : (
                          <button type="button" onClick={() => handleReportComment(comment.id)}>
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
  )
}
