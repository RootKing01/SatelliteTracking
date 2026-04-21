import type { CommunityComment } from '../../api/communityClient'

/**
 * Form per inserire un nuovo commento o rispondere.
 * Props:
 * - value: testo del commento
 * - onChange: handler cambio testo
 * - onSubmit: handler invio
 * - loading: stato invio
 * - replyTo: commento a cui si sta rispondendo (opzionale)
 */
export function CommentForm({ value, onChange, onSubmit, loading, replyTo }: {
  value: string,
  onChange: (e: React.ChangeEvent<HTMLTextAreaElement>) => void,
  onSubmit: () => void,
  loading: boolean,
  replyTo?: CommunityComment | null
}) {
  return (
    <form className="community-comment-form" onSubmit={e => { e.preventDefault(); onSubmit(); }}>
      {replyTo && <div className="reply-to">Rispondi a: {replyTo.body}</div>}
      <textarea value={value} onChange={onChange} disabled={loading} />
      <button type="submit" disabled={loading || !value.trim()}>
        {loading ? 'Invio...' : 'Invia commento'}
      </button>
    </form>
  )
}
