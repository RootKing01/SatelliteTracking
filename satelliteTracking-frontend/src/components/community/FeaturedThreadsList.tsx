import type { CommunityFeedItem } from '../../api/communityClient'

/**
 * Lista dei thread in evidenza della community.
 * Props:
 * - threads: array di thread in evidenza
 * - onSelect: funzione chiamata al click su un thread
 */
export function FeaturedThreadsList({ threads, onSelect }: {
  threads: CommunityFeedItem[],
  onSelect: (thread: CommunityFeedItem) => void
}) {
  return (
    <ul className="community-featured-threads">
      {threads.map(thread => (
        <li key={thread.threadId} onClick={() => onSelect(thread)}>
          <strong>{thread.title}</strong>
          <span>({thread.commentCount} commenti)</span>
        </li>
      ))}
    </ul>
  )
}
