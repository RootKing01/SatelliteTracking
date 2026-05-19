import { useCallback } from 'react'
import { isAxiosError } from 'axios'
import { reportSighting } from '../api/sightingsClient'
import { fetchSatellitePositionById } from '../api/satellitePositionsClient'
import { fetchVisibilityPasses } from '../helpers/visibilityFlowHelpers'
import { createVisibilityErrorResetState, buildVisibilitySummaryInfo } from '../helpers/visibilityFlowHelpers'
import { extractAuthErrorMessage } from '../helpers/appErrorHelpers'

export function useSearchResultSelect(options: {
  setSelectedPreset: (v: any) => void
  setEnabledGroups: (updater: any) => void
  handlePickEntityId: (id: string | null) => void
  satelliteLookupByEntityId: Map<string, any>
  globeRef: React.MutableRefObject<any>
}) {
  const { setSelectedPreset, setEnabledGroups, handlePickEntityId, satelliteLookupByEntityId, globeRef } = options

  return useCallback(async (item: any) => {
    setSelectedPreset('custom')
    setEnabledGroups((prev: any) => (prev[item.groupKey] ? prev : { ...prev, [item.groupKey]: true }))

    handlePickEntityId(item.entityId)

    const liveSelected = satelliteLookupByEntityId.get(item.entityId)
    if (liveSelected) {
      globeRef.current?.focusOnSatellite(
        liveSelected.satellite.longitudeDeg,
        liveSelected.satellite.latitudeDeg,
        liveSelected.satellite.altitudeKm,
        item.entityId,
      )
      return
    }

    try {
      const fallbackPosition = await fetchSatellitePositionById(item.satelliteId)
      globeRef.current?.focusOnSatellite(
        fallbackPosition.longitudeDeg,
        fallbackPosition.latitudeDeg,
        fallbackPosition.altitudeKm,
        item.entityId,
      )
    } catch {
      // Ignore fallback errors
    }
  }, [setSelectedPreset, setEnabledGroups, handlePickEntityId, satelliteLookupByEntityId, globeRef])
}

export function useReportSighting(options: {
  getSelectedSatellite: () => any
  getReportingSighting: () => boolean
  getSightingLatitude: () => number | null
  getSightingLongitude: () => number | null
  getSightingAltitude: () => number | null
  getSightingCity: () => string
  setSightingsError: (s: string) => void
  setReportingSighting: (b: boolean) => void
  setSightingInfo: (s: string) => void
  setMySightings: (updater: (prev: any[]) => any[]) => void
  setOpenPane: (p: string) => void
  setAuthUser: (u: any) => void
  setAuthInfo: (s: string) => void
  setAuthError: (s: string) => void
}) {
  const {
    getSelectedSatellite,
    getReportingSighting,
    getSightingLatitude,
    getSightingLongitude,
    getSightingAltitude,
    getSightingCity,
    setSightingsError,
    setReportingSighting,
    setSightingInfo,
    setMySightings,
    setOpenPane,
    setAuthUser,
    setAuthInfo,
    setAuthError,
  } = options

  return useCallback(async () => {
    const selectedSatellite = getSelectedSatellite()
    if (!selectedSatellite || getReportingSighting()) {
      return
    }

    const hasBrowserCoords = getSightingLatitude() !== null && getSightingLongitude() !== null
    const hasCity = getSightingCity().trim().length > 0

    if (!hasBrowserCoords && !hasCity) {
      setSightingsError('Inserisci una citta o usa la posizione del browser.')
      return
    }

    setReportingSighting(true)
    setSightingInfo('')
    setSightingsError('')

    try {
      const created = await reportSighting({
        satelliteId: selectedSatellite.satellite.satelliteId,
        city: hasBrowserCoords ? undefined : getSightingCity().trim(),
        latitude: hasBrowserCoords ? getSightingLatitude() ?? undefined : undefined,
        longitude: hasBrowserCoords ? getSightingLongitude() ?? undefined : undefined,
        altitudeMeters: hasBrowserCoords ? (getSightingAltitude() ?? undefined) : undefined,
      } as any)
      setMySightings((prev) => [created, ...prev])
      setSightingInfo(created.validationMessage)
      setOpenPane('sightings')
    } catch (error) {
      if (isAxiosError(error) && error.response?.status === 401) {
        setAuthUser(null)
        setAuthInfo('Sessione scaduta. Esegui di nuovo l\'accesso.')
        setAuthError('Sessione non valida per registrare l\'avvistamento.')
        return
      }
      setSightingsError(extractAuthErrorMessage(error, 'Errore durante la registrazione avvistamento'))
    } finally {
      setReportingSighting(false)
    }
  }, [
    getSelectedSatellite,
    getReportingSighting,
    getSightingLatitude,
    getSightingLongitude,
    getSightingAltitude,
    getSightingCity,
    setSightingsError,
    setReportingSighting,
    setSightingInfo,
    setMySightings,
    setOpenPane,
    setAuthUser,
    setAuthInfo,
    setAuthError,
  ])
}

export function useCalculateVisibility(options: {
  getVisibilityLoading: () => boolean
  setVisibilityLoading: (b: boolean) => void
  setVisibilityError: (s: string) => void
  setVisibilityInfo: (s: string) => void
  visibilityCity: string
  visibilityHours: number
  visibilityMinElevation: number
  getVisibilityLatitude: () => number | null
  getVisibilityLongitude: () => number | null
  getVisibilityAltitude: () => number | null
  setVisibilityAllResults: (r: any[]) => void
  setVisibilityResults: (r: any[]) => void
  setVisibilityOverlayOpen: (b: boolean) => void
  setAuthUser: (u: any) => void
  setAuthInfo: (s: string) => void
  setAuthError: (s: string) => void
}) {
  const {
    getVisibilityLoading,
    setVisibilityLoading,
    setVisibilityError,
    setVisibilityInfo,
    visibilityCity,
    visibilityHours,
    visibilityMinElevation,
    getVisibilityLatitude,
    getVisibilityLongitude,
    getVisibilityAltitude,
    setVisibilityAllResults,
    setVisibilityResults,
    setVisibilityOverlayOpen,
    setAuthUser,
    setAuthInfo,
    setAuthError,
  } = options

  return useCallback(async () => {
    if (getVisibilityLoading()) {
      return
    }

    setVisibilityLoading(true)
    setVisibilityError('')
    setVisibilityInfo('')

    try {
      const results = await fetchVisibilityPasses({
        city: visibilityCity,
        hours: visibilityHours,
        minElevation: visibilityMinElevation,
        latitude: getVisibilityLatitude(),
        longitude: getVisibilityLongitude(),
        altitude: getVisibilityAltitude(),
      } as any)

      setVisibilityAllResults(results)
      setVisibilityResults(results.slice(0, 30))
      if (results.length === 0) {
        setVisibilityOverlayOpen(false)
      }
      setVisibilityInfo(buildVisibilitySummaryInfo(results.length))
    } catch (error) {
      if (isAxiosError(error) && error.response?.status === 401) {
        setAuthUser(null)
        setAuthInfo('Sessione scaduta. Esegui di nuovo l\'accesso.')
        setAuthError('Sessione non valida per il calcolo visibilita.')
        return
      }

      const resetState = createVisibilityErrorResetState()
      setVisibilityAllResults(resetState.allResults)
      setVisibilityResults(resetState.previewResults)
      setVisibilityOverlayOpen(!resetState.closeOverlay)
      setVisibilityError(extractAuthErrorMessage(error, 'Errore durante il calcolo della visibilita'))
    } finally {
      setVisibilityLoading(false)
    }
  }, [
    getVisibilityLoading,
    setVisibilityLoading,
    setVisibilityError,
    setVisibilityInfo,
    visibilityCity,
    visibilityHours,
    visibilityMinElevation,
    getVisibilityLatitude,
    getVisibilityLongitude,
    getVisibilityAltitude,
    setVisibilityAllResults,
    setVisibilityResults,
    setVisibilityOverlayOpen,
    setAuthUser,
    setAuthInfo,
    setAuthError,
  ])
}

export function useFocusBySatelliteId(options: {
  liveEntityIdBySatelliteId: Map<number, string>
  satelliteLookupByEntityId: Map<string, any>
  handlePickEntityId: (id: string | null) => void
  globeRef: React.MutableRefObject<any>
  fetchSatellitePositionById: (id: number) => Promise<any>
  setVisibilityError: (s: string) => void
}) {
  const { liveEntityIdBySatelliteId, satelliteLookupByEntityId, handlePickEntityId, globeRef, fetchSatellitePositionById, setVisibilityError } = options

  return useCallback((passOrId: any) => {
    const satelliteId = typeof passOrId === 'number' ? passOrId : passOrId.satelliteId
    const entityId = liveEntityIdBySatelliteId.get(satelliteId)
    const selected = entityId ? satelliteLookupByEntityId.get(entityId) : undefined

    if (entityId && selected) {
      setVisibilityError('')
      handlePickEntityId(entityId)
      globeRef.current?.focusOnSatellite(
        selected.satellite.longitudeDeg,
        selected.satellite.latitudeDeg,
        selected.satellite.altitudeKm,
        entityId,
      )
      return
    }

    void fetchSatellitePositionById(satelliteId)
      .then((fallbackPosition) => {
        setVisibilityError('')
        globeRef.current?.focusOnSatellite(
          fallbackPosition.longitudeDeg,
          fallbackPosition.latitudeDeg,
          fallbackPosition.altitudeKm,
          entityId,
        )
      })
      .catch(() => {
        setVisibilityError('Impossibile fare focus: satellite non disponibile nel feed live.')
      })
  }, [liveEntityIdBySatelliteId, satelliteLookupByEntityId, handlePickEntityId, globeRef, fetchSatellitePositionById, setVisibilityError])
}

export default {}
