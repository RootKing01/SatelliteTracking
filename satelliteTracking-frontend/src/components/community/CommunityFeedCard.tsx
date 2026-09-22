import type { CommunityFeedItem } from '../../api/communityClient'

interface CommunityFeedCardProps {
  title: string
  threadsError: string
  items: CommunityFeedItem[]
  satelliteNames: Record<string, string>
  onOpenThread: (targetType: string, targetId: string) => void
  onToggleLike: (threadId: number) => void
  onFocusSatellite: (satelliteId: number) => void
  featured?: boolean
}

export function CommunityFeedCard({
  title,
  threadsError,
  items,
  satelliteNames,
  onOpenThread,
  onToggleLike,
  onFocusSatellite,
  featured = false,
}: CommunityFeedCardProps) {
  return (
    <div className="community-feed-card">
      <strong>{title}</strong>
      {threadsError ? <small className="community-error">{threadsError}</small> : null}
      {!threadsError && items.length === 0 ? (
        <small>Nessun thread disponibile.</small>
      ) : (
        <div className="community-feed-list">
          {(featured ? [...items].sort((a, b) => b.commentCount - a.commentCount) : items).map((item) => (
            <article key={`${featured ? 'featured-' : 'all-'}${item.threadId}`} className="community-feed-item">
              <strong>
                {(item.targetType === 'SATELLITE' || item.targetType === 'SIGHTING' || item.targetType === 'PASS') && satelliteNames[item.targetId]
                  ? satelliteNames[item.targetId]
                  : item.title}
              </strong>
              <small>
                {item.targetType === 'SATELLITE' && satelliteNames[item.targetId]
                  ? `SATELLITE (${satelliteNames[item.targetId]})`
                  : (item.targetType === 'SIGHTING' || item.targetType === 'PASS') && satelliteNames[item.targetId]
                    ? `${item.targetType} (${satelliteNames[item.targetId]})`
                    : item.targetType === 'GENERAL'
                      ? item.targetType
                      : `${item.targetType} #${item.targetId}`}
              </small>
              <small>{item.commentCount} commenti · {item.likesCount} like</small>
              {featured && (
                <small>
                  {item.lastCommentAt
                    ? new Date(item.lastCommentAt).toLocaleString('it-IT')
                    : 'Nessun commento recente'}
                </small>
              )}
              <p>{item.lastCommentPreview}</p>
              <div className="community-inline-actions">
                <button
                  type="button"
                  onClick={() => onOpenThread(item.targetType, item.targetId)}
                >
                  Apri thread
                </button>
                {item.targetType === 'SATELLITE' ? (
                  <button
                    type="button"
                    className="community-focus-button"
                    onClick={() => {
                      const parsedId = Number(item.targetId)
                      if (Number.isFinite(parsedId)) {
                        onFocusSatellite(parsedId)
                      }
                    }}
                  >
                    Focus sat
                  </button>
                ) : null}
                <button
                  type="button"
                  className={item.likedByMe ? 'community-like-active' : ''}
                  onClick={() => onToggleLike(item.threadId)}
                >
                  {item.likedByMe ? 'Unlike' : 'Like'} ({item.likesCount})
                </button>
              </div>
            </article>
          ))}
        </div>
      )}
    </div>
  )
}
