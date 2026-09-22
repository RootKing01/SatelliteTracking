
interface CommunityComposeProps {
  newThreadTitle: string
  setNewThreadTitle: (v: string) => void
  newThreadBody: string
  setNewThreadBody: (v: string) => void
  postingThread: boolean
  handleCreateGeneralThread: () => void
}

export function CommunityCompose({
  newThreadTitle,
  setNewThreadTitle,
  newThreadBody,
  setNewThreadBody,
  postingThread,
  handleCreateGeneralThread,
}: CommunityComposeProps) {
  return (
    <div className="community-thread-card">
      <strong>Nuovo thread libero</strong>
      <div className="community-compose">
        <input
          type="text"
          value={newThreadTitle}
          onChange={e => setNewThreadTitle(e.target.value)}
          placeholder="Titolo thread (es. Miglior setup per osservazione urbana)"
        />
        <textarea
          rows={3}
          value={newThreadBody}
          onChange={e => setNewThreadBody(e.target.value)}
          placeholder="Apri una discussione non legata a un satellite specifico"
        />
        <button
          type="button"
          onClick={handleCreateGeneralThread}
          disabled={postingThread || !newThreadTitle.trim() || !newThreadBody.trim()}
        >
          {postingThread ? 'Creazione...' : 'Crea thread'}
        </button>
      </div>
    </div>
  )
}
