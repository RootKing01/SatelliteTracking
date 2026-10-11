import { useEffect, useState } from 'react'
import type { AuthUser } from '../api/authClient'
import {
  fetchCommunityNotifications,
  fetchUnreadCommunityNotificationCount,
  type CommunityNotification,
} from '../api/communityClient'

const COMMUNITY_NOTIFICATION_POLL_INTERVAL = 5000

type UseCommunityNotificationsOptions = {
  authUser: AuthUser | null
}

export function useCommunityNotifications({ authUser }: UseCommunityNotificationsOptions) {
  const [notificationsOpen, setNotificationsOpen] = useState(false)
  const [notifications, setNotifications] = useState<CommunityNotification[]>([])
  const [notificationCount, setNotificationCount] = useState(0)

  useEffect(() => {
    if (!authUser) {
      queueMicrotask(() => {
        setNotificationsOpen(false)
        setNotifications([])
        setNotificationCount(0)
      })
      return
    }

    let cancelled = false
    const controller = new AbortController()

    const refreshCount = () => {
      void fetchUnreadCommunityNotificationCount(controller.signal)
        .then((count) => {
          if (!cancelled) setNotificationCount(count)
        })
        .catch(() => {
          if (!cancelled) setNotificationCount(0)
        })
    }

    refreshCount()
    const interval = window.setInterval(refreshCount, COMMUNITY_NOTIFICATION_POLL_INTERVAL)

    return () => {
      cancelled = true
      controller.abort()
      window.clearInterval(interval)
    }
  }, [authUser])

  useEffect(() => {
    if (!authUser || !notificationsOpen) return

    const controller = new AbortController()
    void fetchCommunityNotifications(12, controller.signal)
      .then(setNotifications)
      .catch(() => setNotifications([]))

    return () => controller.abort()
  }, [authUser, notificationsOpen])

  return {
    notificationsOpen,
    setNotificationsOpen,
    notifications,
    setNotifications,
    notificationCount,
    setNotificationCount,
  }
}
