import { useCallback, useEffect, useState } from 'react'
import { isAxiosError } from 'axios'
import { fetchMySightings, type SatelliteSighting } from '../api/satelliteClient'
import { extractAuthErrorMessage } from '../helpers/appErrorHelpers'

type SightingsOptions = {
  authUser: unknown
  openPane: string | null
  setAuthUser: (user: null) => void
  setAuthInfo: (info: string) => void
  setAuthError: (error: string) => void
}

export function useSightings({ authUser, openPane, setAuthUser, setAuthInfo, setAuthError }: SightingsOptions) {
  const [mySightings, setMySightings] = useState<SatelliteSighting[]>([])
  const [sightingsLoading, setSightingsLoading] = useState(false)
  const [sightingsError, setSightingsError] = useState('')
  const [sightingInfo, setSightingInfo] = useState('')

  const loadAuthenticatedSightings = useCallback((signal: AbortSignal) => {
    setSightingsLoading(true)
    setSightingsError('')
    void fetchMySightings(signal)
      .then(setMySightings)
      .catch((error) => {
        if (isAxiosError(error) && error.response?.status === 401) {
          setMySightings([])
          setAuthUser(null)
          setAuthInfo('Sessione scaduta. Esegui di nuovo l\'accesso.')
          setAuthError('Sessione non valida per caricare gli avvistamenti.')
          return
        }
        setSightingsError(extractAuthErrorMessage(error, 'Errore nel caricamento avvistamenti'))
      })
      .finally(() => setSightingsLoading(false))
  }, [setAuthError, setAuthInfo, setAuthUser])

  useEffect(() => {
    if (!authUser || openPane !== 'sightings') return
    const controller = new AbortController()
    queueMicrotask(() => {
      if (!controller.signal.aborted) loadAuthenticatedSightings(controller.signal)
    })
    return () => controller.abort()
  }, [authUser, loadAuthenticatedSightings, openPane])

  return {
    mySightings, setMySightings, sightingsLoading, setSightingsLoading,
    sightingsError, setSightingsError, sightingInfo, setSightingInfo,
  }
}
