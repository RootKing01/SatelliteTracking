import { useEffect, useRef, useState } from 'react'
import { fetchFeaturedCommunityThreads, fetchCommunityFeed, type CommunityFeedItem } from '../api/communityClient'

/**
 * Custom hook per gestire polling e stato dei thread community.
 * @param authUser L'utente autenticato
 * @param sessionVerified Se la sessione è verificata
 * @param communitySessionValid Se la sessione community è valida
 * @returns Oggetto con featuredThreads, allThreads, threadsError
 */
export function useCommunityThreads(authUser: any, sessionVerified: boolean, communitySessionValid: boolean) {
  const [featuredThreads, setFeaturedThreads] = useState<CommunityFeedItem[]>([])
  const [allThreads, setAllThreads] = useState<CommunityFeedItem[]>([])
  const [threadsError, setThreadsError] = useState('')
  const intervalRef = useRef<NodeJS.Timeout | null>(null)

  useEffect(() => {
    if (!authUser || !sessionVerified || !communitySessionValid) {
      setFeaturedThreads([])
      setAllThreads([])
      setThreadsError(!communitySessionValid && sessionVerified ? 'Sessione scaduta. Esegui di nuovo l\'accesso.' : '')
      return
    }
    let cancelled = false
    const controller = new AbortController()
    const fetchThreads = () => {
      Promise.all([
        fetchFeaturedCommunityThreads(8, controller.signal),
        fetchCommunityFeed(40, controller.signal),
      ])
        .then(([featured, all]) => {
          if (!cancelled) {
            setFeaturedThreads(prev => JSON.stringify(prev) !== JSON.stringify(featured) ? featured : prev)
            setAllThreads(prev => JSON.stringify(prev) !== JSON.stringify(all) ? all : prev)
          }
        })
        .catch(() => {
          if (!controller.signal.aborted) {
            setThreadsError('Impossibile caricare i thread community.')
          }
        })
    }
    fetchThreads()
    intervalRef.current = setInterval(fetchThreads, 5000)
    return () => {
      cancelled = true
      controller.abort()
      if (intervalRef.current) clearInterval(intervalRef.current)
    }
  }, [authUser, communitySessionValid, sessionVerified])

  return { featuredThreads, allThreads, threadsError, setFeaturedThreads, setAllThreads, setThreadsError }
}
