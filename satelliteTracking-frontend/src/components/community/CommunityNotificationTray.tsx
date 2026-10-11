import type { CommunityNotification } from '../../api/communityClient'

type CommunityNotificationTrayProps = {
  open: boolean
  notifications: CommunityNotification[]
  onSelect: (notification: CommunityNotification) => void
}

export function CommunityNotificationTray({
  open,
  notifications,
  onSelect,
}: CommunityNotificationTrayProps) {
  if (!open) {
    return null
  }

  return (
    <div className="community-notification-tray overlay">
      {notifications.length === 0 ? (
        <small>Nessuna notifica disponibile.</small>
      ) : (
        notifications.map((notification) => (
          <button
            key={notification.id}
            type="button"
            className={`community-notification-item ${notification.readAt == null ? 'is-unread' : ''}`}
            onClick={() => onSelect(notification)}
          >
            <strong>{notification.sourceCommentAuthorUsername ?? 'Utente'}</strong>
            <small>{notification.threadTitle}</small>
            <span>{notification.preview}</span>
          </button>
        ))
      )}
    </div>
  )
}