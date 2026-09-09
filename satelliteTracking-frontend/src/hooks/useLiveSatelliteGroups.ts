import { startTransition, useEffect, useMemo, useRef, useState } from 'react'
import { isAxiosError } from 'axios'
import type { SatelliteGroupKey, SatelliteGroupSource } from '../api/groups/types'
import type { SatellitePosition } from '../types/satellite'

export type GroupPositionsState = Partial<Record<SatelliteGroupKey, SatellitePosition[]>>
type GroupLoadingState = Partial<Record<SatelliteGroupKey, boolean>>
type GroupErrorState = Partial<Record<SatelliteGroupKey, string>>

type UseLiveSatelliteGroupsOptions = {
  activeGroups: SatelliteGroupSource[]
  authenticated: boolean
  enabled: boolean
  compactMobileViewport: boolean
  onSessionExpired: () => void
}

function computeRefreshIntervalMs(totalVisibleCount: number) {
  if (totalVisibleCount <= 50) return 1400
  if (totalVisibleCount <= 250) return 2000
  if (totalVisibleCount <= 1000) return 3000
  if (totalVisibleCount <= 3000) return 3500
  if (totalVisibleCount <= 8000) return 4000
  if (totalVisibleCount <= 12000) return 4500
  return 5000
}

export function useLiveSatelliteGroups({
  activeGroups,
  authenticated,
  enabled,
  compactMobileViewport,
  onSessionExpired,
}: UseLiveSatelliteGroupsOptions) {
  const [groupPositions, setGroupPositions] = useState<GroupPositionsState>({})
  const [groupLoading, setGroupLoading] = useState<GroupLoadingState>({})
  const [groupErrors, setGroupErrors] = useState<GroupErrorState>({})
  const [isRefreshing, setIsRefreshing] = useState(false)
  const [hasLoadedOnce, setHasLoadedOnce] = useState(false)
  const latestRequestIdRef = useRef(0)
  const inFlightRequestRef = useRef(false)
  const latestGroupPositionsRef = useRef<GroupPositionsState>({})

  useEffect(() => {
    latestGroupPositionsRef.current = groupPositions
  }, [groupPositions])

  const totalVisibleCount = useMemo(
    () => activeGroups.reduce((total, group) => total + (groupPositions[group.key]?.length ?? 0), 0),
    [activeGroups, groupPositions],
  )
  const refreshIntervalMs = useMemo(
    () => computeRefreshIntervalMs(totalVisibleCount),
    [totalVisibleCount],
  )

  useEffect(() => {
    if (!authenticated || !enabled) {
      queueMicrotask(() => {
        setGroupPositions({})
        setGroupErrors({})
        setGroupLoading({})
        setIsRefreshing(false)
      })
      return
    }

    if (activeGroups.length === 0) {
      queueMicrotask(() => {
        setGroupPositions({})
        setGroupErrors({})
        setGroupLoading({})
      })
      return
    }

    let isMounted = true
    let refreshController: AbortController | null = null

    const pauseBetweenBatches = () =>
      new Promise<void>((resolve) => {
        window.setTimeout(resolve, 0)
      })

    const loadGroups = async (signal?: AbortSignal) => {
      setIsRefreshing(true)

      setGroupLoading((prev) => {
        const next = { ...prev }
        for (const group of activeGroups) {
          next[group.key] = true
        }
        return next
      })

      const batchSize = compactMobileViewport ? 1 : activeGroups.length >= 8 ? 2 : 3
      const nextPositions: GroupPositionsState = { ...latestGroupPositionsRef.current }
      const nextErrors: GroupErrorState = {}
      const nextLoading: GroupLoadingState = {}

      for (let index = 0; index < activeGroups.length; index += batchSize) {
        if (!isMounted || signal?.aborted) {
          return
        }

        const batch = activeGroups.slice(index, index + batchSize)
        const results = await Promise.allSettled(
          batch.map(async (group) => {
            const positions = await group.loadPositions(signal)
            return { key: group.key, positions }
          }),
        )

        if (!isMounted || signal?.aborted) {
          return
        }

        let unauthorizedDetected = false

        batch.forEach((group, batchIndex) => {
          const result = results[batchIndex]
          nextLoading[group.key] = false

          if (result.status === 'fulfilled') {
            nextPositions[group.key] = result.value.positions
            nextErrors[group.key] = ''
            return
          }

          nextPositions[group.key] = latestGroupPositionsRef.current[group.key] ?? []
          const reason = result.reason
          if (isAxiosError(reason) && reason.response?.status === 401) {
            unauthorizedDetected = true
            nextErrors[group.key] = 'Sessione scaduta'
            return
          }

          if (isAxiosError(reason)) {
            const hasPreviousData = (latestGroupPositionsRef.current[group.key]?.length ?? 0) > 0
            const isCanceled = reason.code === 'ERR_CANCELED'
            const isTimeout = reason.code === 'ECONNABORTED'
            const status = reason.response?.status
            const isTransientUpstream = status === 429 || status === 502 || status === 503 || status === 504
            const isNetworkError = !reason.response

            if (isCanceled || ((isTimeout || isTransientUpstream || isNetworkError) && hasPreviousData)) {
              nextErrors[group.key] = ''
              return
            }
          }

          nextErrors[group.key] = `Errore caricamento ${group.label}`
        })

        if (unauthorizedDetected) {
          startTransition(() => {
            onSessionExpired()
            setGroupPositions({})
            setGroupErrors({})
            setGroupLoading({})
            setHasLoadedOnce(false)
          })
          return
        }

        startTransition(() => {
          setGroupPositions({ ...nextPositions })
          setGroupErrors({ ...nextErrors })
          setGroupLoading({ ...nextLoading })
          setHasLoadedOnce(true)
        })

        if (index + batchSize < activeGroups.length) {
          await pauseBetweenBatches()
        }
      }
    }

    const finalizeLoadRequest = (requestId: number) => {
      if (latestRequestIdRef.current === requestId) {
        setIsRefreshing(false)
      }
      if (latestRequestIdRef.current <= requestId) {
        inFlightRequestRef.current = false
      }
    }

    const startLoad = () => {
      if (inFlightRequestRef.current) {
        return
      }

      const requestId = latestRequestIdRef.current + 1
      latestRequestIdRef.current = requestId
      refreshController = new AbortController()
      inFlightRequestRef.current = true

      void loadGroups(refreshController.signal)
        .catch(() => {
          // Gli errori dei singoli gruppi sono gestiti con Promise.allSettled.
        })
        .finally(() => {
          finalizeLoadRequest(requestId)
        })
    }

    startLoad()

    const refreshId = window.setInterval(startLoad, refreshIntervalMs)

    return () => {
      isMounted = false
      refreshController?.abort()
      window.clearInterval(refreshId)
      inFlightRequestRef.current = false
      setIsRefreshing(false)
    }
  }, [activeGroups, authenticated, compactMobileViewport, enabled, onSessionExpired, refreshIntervalMs])

  return { groupPositions, groupLoading, groupErrors, isRefreshing, hasLoadedOnce, refreshIntervalMs }
}
