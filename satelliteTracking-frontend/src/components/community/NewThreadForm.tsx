import React from 'react'

/**
 * Form per la creazione di un nuovo thread.
 * Props:
 * - title: titolo thread
 * - body: corpo thread
 * - onTitleChange: handler cambio titolo
 * - onBodyChange: handler cambio corpo
 * - onSubmit: handler invio
 * - loading: stato invio
 */
export function NewThreadForm({ title, body, onTitleChange, onBodyChange, onSubmit, loading }: {
  title: string,
  body: string,
  onTitleChange: (e: React.ChangeEvent<HTMLInputElement>) => void,
  onBodyChange: (e: React.ChangeEvent<HTMLTextAreaElement>) => void,
  onSubmit: () => void,
  loading: boolean
}) {
  return (
    <form className="community-new-thread-form" onSubmit={e => { e.preventDefault(); onSubmit(); }}>
      <input
        type="text"
        value={title}
        onChange={onTitleChange}
        placeholder="Titolo del thread"
        disabled={loading}
      />
      <textarea
        value={body}
        onChange={onBodyChange}
        placeholder="Scrivi il primo messaggio..."
        disabled={loading}
      />
      <button type="submit" disabled={loading || !title.trim() || !body.trim()}>
        {loading ? 'Creazione...' : 'Crea thread'}
      </button>
    </form>
  )
}
