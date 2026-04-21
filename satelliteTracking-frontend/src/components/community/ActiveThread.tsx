import type { CommunityThread, CommunityComment } from '../../api/communityClient'

/**
 * Visualizza il thread attivo e la lista dei commenti.
 * Props:
 * - thread: thread attivo
 * - comments: array di commenti
 */
export function ActiveThread({ thread, comments }: {
  thread: CommunityThread | null,
  comments: CommunityComment[]
}) {
  if (!thread) return <div className="community-active-thread">Nessun thread selezionato.</div>
  return (
    <div className="community-active-thread">
      <h4>{thread.title}</h4>
      <ul className="community-comments-list">
        {comments.map(comment => (
          <li key={comment.id}>
            <span>{comment.body}</span>
          </li>
        ))}
      </ul>
    </div>
  )
}
