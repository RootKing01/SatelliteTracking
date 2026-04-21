import type { CommunityFeedItem } from '../../api/communityClient'

/**
 * Lista di tutti i thread della community.
 * Props:
 * - threads: array di thread
 * - onSelect: funzione chiamata al click su un thread
 */
export function AllThreadsList({ threads, onSelect }: {
  threads: CommunityFeedItem[],
  onSelect: (thread: CommunityFeedItem) => void
}) {
  return (
    <ul className="community-all-threads">
      {threads.map(thread => (
        <li key={thread.threadId} onClick={() => onSelect(thread)}>
          <strong>{thread.title}</strong>
          <span>({thread.commentCount} commenti)</span>
        </li>
      ))}
    </ul>
  )
}
