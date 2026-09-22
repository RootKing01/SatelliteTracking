import { useEffect, useState } from 'react'
import { isAxiosError } from 'axios'
import type { AuthUser } from '../api/authClient'
import {
  fetchCommunityNotifications,
  fetchFeaturedCommunityThreads,
  fetchCommunityFeed,
  fetchUnreadCommunityNotificationCount,
  type CommunityFeedItem,
  type CommunityNotification,
} from '../api/communityClient'
import { fetchAndMapSatelliteNames, updateMissingSatelliteNames } from './satelliteNameHelper'

const COMMUNITY_POLL_INTERVAL = 5000
const COMMUNITY_FEATURED_LIMIT = 8
const COMMUNITY_FEED_LIMIT = 40
const ERROR_THREAD_LOAD = 'Impossibile caricare i thread community.'

type UseCommunityDataOptions = {
  authUser: AuthUser | null
  sessionVerified: boolean
  notificationsOpen: boolean
  onUnauthorized: () => void
}

export function useCommunityData({
  authUser,
  sessionVerified,
  notificationsOpen,
  onUnauthorized,
}: UseCommunityDataOptions) {
  const [satelliteNames, setSatelliteNames] = useState<Record<string, string>>({})
  const [featuredThreads, setFeaturedThreads] = useState<CommunityFeedItem[]>([])
  const [allThreads, setAllThreads] = useState<CommunityFeedItem[]>([])
  const [threadsError, setThreadsError] = useState('')
  const [notifications, setNotifications] = useState<CommunityNotification[]>([])
  const [notificationCount, setNotificationCount] = useState(0)

  useEffect(() => {
    let cancelled = false

    void fetchAndMapSatelliteNames('ALL')
      .then(({ map }) => {
        if (!cancelled) setSatelliteNames(map)
      })
      .catch((error) => {
        console.error('Errore fetchAndMapSatelliteNames', error)
      })

    return () => {
      cancelled = true
    }
  }, [])

  useEffect(() => {
    const targetIds = [...featuredThreads, ...allThreads]
      .filter((thread) => thread.targetType === 'SATELLITE')
      .map((thread) => String(thread.targetId))
    const missingIds = targetIds.filter((id) => !(id in satelliteNames))

    if (missingIds.length > 0) {
      updateMissingSatelliteNames(missingIds, setSatelliteNames)
    }
  }, [allThreads, featuredThreads, satelliteNames])

  useEffect(() => {
    if (!authUser) {
      queueMicrotask(() => {
        setFeaturedThreads([])
        setAllThreads([])
        setThreadsError('')
        setNotificationCount(0)
      })
      return
    }

    if (!sessionVerified) return

    let cancelled = false
    const controller = new AbortController()

    const refreshUnreadCount = () => {
      fetchUnreadCommunityNotificationCount(controller.signal)
        .then((unreadCount) => {
          if (!cancelled) setNotificationCount(unreadCount)
        })
        .catch(() => {
          if (!cancelled) setNotificationCount(0)
        })
    }

    const fetchThreads = () => {
      Promise.all([
        fetchFeaturedCommunityThreads(COMMUNITY_FEATURED_LIMIT, controller.signal),
        fetchCommunityFeed(COMMUNITY_FEED_LIMIT, controller.signal),
      ])
        .then(([featured, all]) => {
          if (!cancelled) {
            setFeaturedThreads((previous) => JSON.stringify(previous) !== JSON.stringify(featured) ? featured : previous)
            setAllThreads((previous) => JSON.stringify(previous) !== JSON.stringify(all) ? all : previous)
          }
        })
        .catch((error) => {
          if (!controller.signal.aborted) {
            if (isAxiosError(error) && error.response?.status === 401) {
              setThreadsError('Sessione scaduta. Esegui di nuovo l\'accesso.')
              onUnauthorized()
              return
            }
            setThreadsError(ERROR_THREAD_LOAD)
          }
        })
        .finally(refreshUnreadCount)
    }

    fetchThreads()
    refreshUnreadCount()
    const interval = window.setInterval(fetchThreads, COMMUNITY_POLL_INTERVAL)

    return () => {
      cancelled = true
      controller.abort()
      window.clearInterval(interval)
    }
  }, [authUser, onUnauthorized, sessionVerified])

  useEffect(() => {
    try {
      window.dispatchEvent(new CustomEvent('communityNotificationsCount', { detail: notificationCount }))
    } catch {
      // ignore
    }
  }, [notificationCount])

  useEffect(() => {
    if (!notificationsOpen || !authUser) return

    fetchCommunityNotifications(12)
      .then(setNotifications)
      .catch(() => setNotifications([]))
  }, [authUser, notificationsOpen])

  return {
    satelliteNames,
    setSatelliteNames,
    featuredThreads,
    setFeaturedThreads,
    allThreads,
    setAllThreads,
    threadsError,
    setThreadsError,
    notifications,
    setNotifications,
    notificationCount,
    setNotificationCount,
  }
}
