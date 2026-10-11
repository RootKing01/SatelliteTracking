import type { AuthUser } from '../../api/authClient'
import type { CommunityComment } from '../../api/communityClient'

type CommunityCommentSectionProps = {
  comments: CommunityComment[]
  commentsLoading: boolean
  authUser: AuthUser
  replyToComment: CommunityComment | null
  editingCommentId: number | null
  editingBody: string
  postingComment: boolean
  newCommentBody: string
  setReplyToComment: (comment: CommunityComment | null) => void
  setEditingCommentId: (id: number | null) => void
  setEditingBody: (body: string) => void
  setNewCommentBody: (body: string) => void
  handleSubmitComment: () => void
  handleSaveEdit: (commentId: number) => void
  handleDeleteComment: (commentId: number) => void
  handleReportComment: (commentId: number) => void
}

export function CommunityCommentSection({
  comments,
  commentsLoading,
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
  handleSubmitComment,
  handleSaveEdit,
  handleDeleteComment,
  handleReportComment,
}: CommunityCommentSectionProps) {
  return (
    <>
      <div className="community-compose">
        {replyToComment ? (
          <div className="community-reply-indicator">
            Risposta a <strong>{replyToComment.authorUsername}</strong>
            <button type="button" className="community-cancel-reply" onClick={() => setReplyToComment(null)}>
              Annulla risposta
            </button>
          </div>
        ) : null}
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
            const parent = comment.parentCommentId
              ? comments.find((item) => item.id === comment.parentCommentId)
              : null

            return (
              <article
                key={comment.id}
                id={`community-comment-${comment.id}`}
                className={`community-comment-item ${comment.deleted ? 'is-deleted' : ''}`}
              >
                <div className="community-comment-head">
                  <strong>{comment.authorUsername}</strong>
                  <small>{new Date(comment.createdAt).toLocaleString('it-IT')}</small>
                </div>
                {parent ? (
                  <div className="community-reply-indicator">
                    Risposta a <strong>{parent.authorUsername}</strong>
                    <span className="community-reply-preview">
                      {parent.body.length > 60 ? `${parent.body.slice(0, 57)}...` : parent.body}
                    </span>
                  </div>
                ) : null}
                {isEditing ? (
                  <div className="community-edit-box">
                    <textarea rows={3} value={editingBody} onChange={(event) => setEditingBody(event.target.value)} />
                    <div className="community-inline-actions">
                      <button type="button" onClick={() => handleSaveEdit(comment.id)}>Salva</button>
                      <button type="button" onClick={() => { setEditingCommentId(null); setEditingBody('') }}>Annulla</button>
                    </div>
                  </div>
                ) : <p>{comment.body}</p>}
                {!comment.deleted && !isEditing ? (
                  <div className="community-inline-actions">
                    <button type="button" onClick={() => setReplyToComment(comment)}>Rispondi</button>
                    {isOwnComment ? (
                      <>
                        <button type="button" onClick={() => { setEditingCommentId(comment.id); setEditingBody(comment.body) }}>Modifica</button>
                        <button type="button" onClick={() => handleDeleteComment(comment.id)}>Elimina</button>
                      </>
                    ) : <button type="button" onClick={() => handleReportComment(comment.id)}>Segnala</button>}
                  </div>
                ) : null}
              </article>
            )
          })}
        </div>
      )}
    </>
  )
}