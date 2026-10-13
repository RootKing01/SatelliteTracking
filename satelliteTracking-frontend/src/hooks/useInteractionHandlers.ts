import { useCallback, type Dispatch, type MutableRefObject, type SetStateAction } from 'react'
import { isAxiosError } from 'axios'
import { reportSighting, type SatelliteSighting } from '../api/satelliteClient'
import { fetchSatellitePositionById, type UpcomingPass } from '../api/satelliteClient'
import type { SatelliteGlobeHandle } from '../components/SatelliteGlobe'
import type { SidebarPane } from '../components/layout/PanelSidebarButtons'
import {
  buildVisibilitySummaryInfo,
  createVisibilityErrorResetState,
  fetchVisibilityPasses,
} from '../helpers/visibilityHelpers'
import { extractAuthErrorMessage } from '../helpers/appErrorHelpers'
import type { GroupPreset } from '../helpers/groupHelpers'
import type { SearchResultItem } from '../helpers/searchHelpers'
import type { SelectedSatelliteState } from '../helpers/searchHelpers'
import type { SatelliteGroupKey } from '../api/groups/types'
import type { SatellitePosition } from '../types/satellite'

type AuthSessionSetters = {
  setAuthUser: Dispatch<SetStateAction<import('../api/authClient').AuthUser | null>>
  setAuthInfo: Dispatch<SetStateAction<string>>
  setAuthError: Dispatch<SetStateAction<string>>
  clearSessionState?: () => void
}

function handleUnauthorizedInteraction(options: AuthSessionSetters & { message: string }) {
  options.clearSessionState?.()
  options.setAuthUser(null)
  options.setAuthInfo('Sessione scaduta. Esegui di nuovo l\'accesso.')
  options.setAuthError(options.message)
}

export function useSearchResultSelect(options: {
  setSelectedPreset: Dispatch<SetStateAction<GroupPreset>>
  setEnabledGroups: Dispatch<SetStateAction<Record<SatelliteGroupKey, boolean>>>
  handlePickEntityId: (id: string | null) => void
  satelliteLookupByEntityId: Map<string, SelectedSatelliteState>
  globeRef: MutableRefObject<SatelliteGlobeHandle | null>
}) {
  const { setSelectedPreset, setEnabledGroups, handlePickEntityId, satelliteLookupByEntityId, globeRef } = options

  return useCallback(async (item: SearchResultItem) => {
    setSelectedPreset('custom')
    setEnabledGroups((prev) => (prev[item.groupKey] ? prev : { ...prev, [item.groupKey]: true }))

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
  selectedSatellite: SelectedSatelliteState | null
  reportingSighting: boolean
  sightingLatitude: number | null
  sightingLongitude: number | null
  sightingAltitude: number | null
  sightingCity: string
  setSightingsError: (s: string) => void
  setReportingSighting: (b: boolean) => void
  setSightingInfo: (s: string) => void
  setMySightings: Dispatch<SetStateAction<SatelliteSighting[]>>
  setOpenPane: Dispatch<SetStateAction<SidebarPane | null>>
} & AuthSessionSetters) {
  const {
    selectedSatellite,
    reportingSighting,
    sightingLatitude,
    sightingLongitude,
    sightingAltitude,
    sightingCity,
    setSightingsError,
    setReportingSighting,
    setSightingInfo,
    setMySightings,
    setOpenPane,
    setAuthUser,
    setAuthInfo,
    setAuthError,
    clearSessionState,
  } = options

  return useCallback(async () => {
    if (!selectedSatellite || reportingSighting) {
      return
    }

    const hasBrowserCoords = sightingLatitude !== null && sightingLongitude !== null
    const hasCity = sightingCity.trim().length > 0

    if (!hasBrowserCoords && !hasCity) {
      setSightingsError('Inserisci una città o usa la posizione del browser.')
      return
    }

    setReportingSighting(true)
    setSightingInfo('')
    setSightingsError('')

    try {
      const created = await reportSighting({
        satelliteId: selectedSatellite.satellite.satelliteId,
        city: hasBrowserCoords ? undefined : sightingCity.trim(),
        latitude: hasBrowserCoords ? sightingLatitude ?? undefined : undefined,
        longitude: hasBrowserCoords ? sightingLongitude ?? undefined : undefined,
        altitudeMeters: hasBrowserCoords ? (sightingAltitude ?? undefined) : undefined,
      })
      setMySightings((prev) => [created, ...prev])
      setSightingInfo(created.validationMessage)
      setOpenPane('sightings')
    } catch (error) {
      if (isAxiosError(error) && error.response?.status === 401) {
        handleUnauthorizedInteraction({
          setAuthUser,
          setAuthInfo,
          setAuthError,
          clearSessionState,
          message: 'Sessione non valida per registrare l\'avvistamento.',
        })
        return
      }
      setSightingsError(extractAuthErrorMessage(error, 'Errore durante la registrazione avvistamento'))
    } finally {
      setReportingSighting(false)
    }
  }, [
    selectedSatellite,
    reportingSighting,
    sightingLatitude,
    sightingLongitude,
    sightingAltitude,
    sightingCity,
    setSightingsError,
    setReportingSighting,
    setSightingInfo,
    setMySightings,
    setOpenPane,
    setAuthUser,
    setAuthInfo,
    setAuthError,
    clearSessionState,
  ])
}

export function useCalculateVisibility(options: {
  visibilityLoading: boolean
  setVisibilityLoading: (b: boolean) => void
  setVisibilityError: (s: string) => void
  setVisibilityInfo: (s: string) => void
  visibilityCity: string
  visibilityHours: number
  visibilityMinElevation: number
  visibilityLatitude: number | null
  visibilityLongitude: number | null
  visibilityAltitude: number | null
  setVisibilityAllResults: Dispatch<SetStateAction<UpcomingPass[]>>
  setVisibilityResults: Dispatch<SetStateAction<UpcomingPass[]>>
  setVisibilityOverlayOpen: (b: boolean) => void
} & AuthSessionSetters) {
  const {
    visibilityLoading,
    setVisibilityLoading,
    setVisibilityError,
    setVisibilityInfo,
    visibilityCity,
    visibilityHours,
    visibilityMinElevation,
    visibilityLatitude,
    visibilityLongitude,
    visibilityAltitude,
    setVisibilityAllResults,
    setVisibilityResults,
    setVisibilityOverlayOpen,
    setAuthUser,
    setAuthInfo,
    setAuthError,
    clearSessionState,
  } = options

  return useCallback(async () => {
    if (visibilityLoading) {
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
        latitude: visibilityLatitude,
        longitude: visibilityLongitude,
        altitude: visibilityAltitude,
      })

      setVisibilityAllResults(results)
      setVisibilityResults(results.slice(0, 30))
      if (results.length === 0) {
        setVisibilityOverlayOpen(false)
      }
      setVisibilityInfo(buildVisibilitySummaryInfo(results.length))
    } catch (error) {
      if (isAxiosError(error) && error.response?.status === 401) {
        handleUnauthorizedInteraction({
          setAuthUser,
          setAuthInfo,
          setAuthError,
          clearSessionState,
          message: 'Sessione non valida per il calcolo della visibilità.',
        })
        return
      }

      const resetState = createVisibilityErrorResetState()
      setVisibilityAllResults(resetState.allResults)
      setVisibilityResults(resetState.previewResults)
      setVisibilityOverlayOpen(!resetState.closeOverlay)
      setVisibilityError(extractAuthErrorMessage(error, 'Errore durante il calcolo della visibilità'))
    } finally {
      setVisibilityLoading(false)
    }
  }, [
    visibilityLoading,
    setVisibilityLoading,
    setVisibilityError,
    setVisibilityInfo,
    visibilityCity,
    visibilityHours,
    visibilityMinElevation,
    visibilityLatitude,
    visibilityLongitude,
    visibilityAltitude,
    setVisibilityAllResults,
    setVisibilityResults,
    setVisibilityOverlayOpen,
    setAuthUser,
    setAuthInfo,
    setAuthError,
    clearSessionState,
  ])
}

export function useFocusBySatelliteId(options: {
  liveEntityIdBySatelliteId: Map<number, string>
  satelliteLookupByEntityId: Map<string, SelectedSatelliteState>
  handlePickEntityId: (id: string | null) => void
  globeRef: MutableRefObject<SatelliteGlobeHandle | null>
  fetchSatellitePositionById: (id: number) => Promise<SatellitePosition>
  setVisibilityError: Dispatch<SetStateAction<string>>
}) {
  const { liveEntityIdBySatelliteId, satelliteLookupByEntityId, handlePickEntityId, globeRef, fetchSatellitePositionById, setVisibilityError } = options

  return useCallback((passOrId: number | UpcomingPass) => {
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

