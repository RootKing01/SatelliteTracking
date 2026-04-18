import { useCallback, useEffect, useMemo, useRef, useState } from 'react'
import { isAxiosError } from 'axios'
import { Color, Ion } from 'cesium'
import { getCurrentUser, type AuthUser } from './api/authClient'
import { type OrekitStatusResponse } from './api/orekitStatusClient'
import { type SystemHealthResponse } from './api/systemHealthClient'
import { fetchSatelliteCatalogByType, type SatelliteCatalogItem } from './api/satelliteCatalogClient'
import { fetchAllSatellitePositions, fetchSatellitePositionById } from './api/satellitePositionsClient'
import { fetchMySightings, reportSighting, type SatelliteSighting } from './api/sightingsClient'
import { type UpcomingPass } from './api/satelliteVisibilityClient'
import { satelliteGroupSources } from './api/groups'
import type { SatelliteGroupKey, SatelliteGroupSource } from './api/groups/types'
import { extractAuthErrorMessage, extractGeolocationErrorMessage } from './helpers/appErrorHelpers'
import {
  getBrowserGeolocationPrecheckError,
  requestBrowserLocation,
} from './helpers/browserGeolocationHelpers'
import {
  executeLoginFlow,
  executeLogoutFlow,
  executeRegisterFlow,
} from './helpers/authFlowHelpers'
import { buildEnabledGroupsFromPreset, createDefaultEnabledGroups, type GroupPreset } from './helpers/groupHelpers'
import { buildGroupRows } from './helpers/groupViewHelpers'
import {
  buildLiveEntityIdBySatelliteId,
  buildSatelliteLookupByEntityId,
  type SelectedSatelliteState,
} from './helpers/satelliteSelectionHelpers'
import {
  buildSearchResultItems,
  type SatelliteSearchScope,
  type SearchResultItem,
} from './helpers/searchHelpers'
import {
  loadOrekitStatus,
  loadSystemHealth,
} from './helpers/systemStatusHelpers'
import {
  buildVisibilityQueryLocationLabel,
  downloadVisibilityResultsCsv,
  filterVisibilityResults,
} from './helpers/visibilityHelpers'
import {
  buildVisibilitySummaryInfo,
  createVisibilityErrorResetState,
  fetchVisibilityPasses,
} from './helpers/visibilityFlowHelpers'
import { AuthPanel } from './components/auth/AuthPanel'
import { PanelSidebarButtons, type SidebarPane } from './components/layout/PanelSidebarButtons'
import { PanelTopSection } from './components/layout/PanelTopSection'
import { SatelliteGlobe, type CompassState, type SatelliteGlobeHandle, type VisibleSatelliteItem } from './components/SatelliteGlobe'
import { CommunityPanel, CompassPanel, GroupsPanel, MusicFloatingPlayer, MusicPanel, MusicPlayerProvider, SatellitesPanel, SightingsPanel, VisibilityPanel } from './components/panels'
import type { SatellitePosition } from './types/satellite'
import './App.css'
import './styles/orekit-badge.css'
import './styles/mobile-smartphone.css'

const ionToken =
  import.meta.env.VITE_CESIUM_TOKEN ?? import.meta.env.VITE_CESIUM_ION_TOKEN

if (ionToken && !import.meta.env.DEV) {
  Ion.defaultAccessToken = ionToken
}

type GroupPositionsState = Partial<Record<SatelliteGroupKey, SatellitePosition[]>>
type GroupLoadingState = Partial<Record<SatelliteGroupKey, boolean>>
type GroupErrorState = Partial<Record<SatelliteGroupKey, string>>

const defaultEnabledGroups = createDefaultEnabledGroups(satelliteGroupSources)

const defaultRefreshIntervalSec = 0.8
const mediumRefreshIntervalSec = 1.4
const heavyRefreshIntervalSec = 2.1
const veryHeavyRefreshIntervalSec = 3
const refreshTuningProfiles = [
  { label: 'Aggressivo', multiplier: 0.72 },
  { label: 'Bilanciato', multiplier: 1 },
  { label: 'Stabile', multiplier: 1.28 },
] as const

type AuthMode = 'login' | 'register'

function App() {
  const allGroups = satelliteGroupSources as readonly SatelliteGroupSource[]
  const globeRef = useRef<SatelliteGlobeHandle>(null)

  const [groupPositions, setGroupPositions] = useState<GroupPositionsState>({})
  const [groupLoading, setGroupLoading] = useState<GroupLoadingState>({})
  const [groupErrors, setGroupErrors] = useState<GroupErrorState>({})
  const [isRefreshing, setIsRefreshing] = useState(false)
  const [hasLoadedOnce, setHasLoadedOnce] = useState(false)
  const [enabledGroups, setEnabledGroups] =
    useState<Record<SatelliteGroupKey, boolean>>(defaultEnabledGroups)
  const [selectedPreset, setSelectedPreset] = useState<GroupPreset>('stations')
  const [openPane, setOpenPane] = useState<SidebarPane | null>(null)
  const [searchScope, setSearchScope] = useState<SatelliteSearchScope>('enabled')
  const [searchQuery, setSearchQuery] = useState('')
  const [catalogByGroup, setCatalogByGroup] =
    useState<Partial<Record<SatelliteGroupKey, SatelliteCatalogItem[]>>>({})
  const [focusGlobeMode, setFocusGlobeMode] = useState(false)
  const [selectedSatellite, setSelectedSatellite] = useState<SelectedSatelliteState | null>(null)
  const [selectedEntityId, setSelectedEntityId] = useState<string | null>(null)
  const [autoRotate, setAutoRotate] = useState(true)
  const [showBackSideSatellites, setShowBackSideSatellites] = useState(false)
  const [compassCollapsed, setCompassCollapsed] = useState(false)
  const [refreshTuningIndex, setRefreshTuningIndex] = useState(1)
  const latestRequestIdRef = useRef(0)
  const inFlightRequestRef = useRef(false)
  const latestGroupPositionsRef = useRef<GroupPositionsState>({})
  const [compass, setCompass] = useState<CompassState>({
    headingDeg: 0,
    pitchDeg: 0,
    altitudeKm: 0,
  })
  const [authChecking, setAuthChecking] = useState(true)
  const [authSubmitting, setAuthSubmitting] = useState(false)
  const [authMode, setAuthMode] = useState<AuthMode>('login')
  const [authUser, setAuthUser] = useState<AuthUser | null>(null)
  const [authUsernameOrEmail, setAuthUsernameOrEmail] = useState('')
  const [authUsername, setAuthUsername] = useState('')
  const [authEmail, setAuthEmail] = useState('')
  const [authPassword, setAuthPassword] = useState('')
  const [authError, setAuthError] = useState('')
  const [authInfo, setAuthInfo] = useState('Accedi con il profilo base oppure registrane uno nuovo.')
  const [orekitStatus, setOrekitStatus] = useState<OrekitStatusResponse | null>(null)
  const [orekitStatusLoading, setOrekitStatusLoading] = useState(false)
  const [orekitStatusError, setOrekitStatusError] = useState('')
  const [systemHealth, setSystemHealth] = useState<SystemHealthResponse | null>(null)
  const [systemHealthLoading, setSystemHealthLoading] = useState(false)
  const [systemHealthError, setSystemHealthError] = useState('')
  const [mySightings, setMySightings] = useState<SatelliteSighting[]>([])
  const [sightingsLoading, setSightingsLoading] = useState(false)
  const [sightingsError, setSightingsError] = useState('')
  const [reportingSighting, setReportingSighting] = useState(false)
  const [sightingInfo, setSightingInfo] = useState('')
  const [sightingCity, setSightingCity] = useState('')
  const [sightingLatitude, setSightingLatitude] = useState<number | null>(null)
  const [sightingLongitude, setSightingLongitude] = useState<number | null>(null)
  const [sightingAltitude, setSightingAltitude] = useState<number | null>(null)
  const [locatingBrowser, setLocatingBrowser] = useState(false)
  const [visibilityHours, setVisibilityHours] = useState(12)
  const [visibilityMinElevation, setVisibilityMinElevation] = useState(10)
  const [visibilityLoading, setVisibilityLoading] = useState(false)
  const [visibilityError, setVisibilityError] = useState('')
  const [visibilityInfo, setVisibilityInfo] = useState('')
  const [visibilityCity, setVisibilityCity] = useState('')
  const [visibilityResults, setVisibilityResults] = useState<UpcomingPass[]>([])
  const [visibilityAllResults, setVisibilityAllResults] = useState<UpcomingPass[]>([])
  const [visibilityOverlayOpen, setVisibilityOverlayOpen] = useState(false)
  const [visibilityOverlayQuery, setVisibilityOverlayQuery] = useState('')
  const [visibilityLatitude, setVisibilityLatitude] = useState<number | null>(null)
  const [visibilityLongitude, setVisibilityLongitude] = useState<number | null>(null)
  const [visibilityAltitude, setVisibilityAltitude] = useState<number | null>(null)
  const [visibilityLocatingBrowser, setVisibilityLocatingBrowser] = useState(false)

  const groupColorMap = useMemo(
    () =>
      Object.fromEntries(
        allGroups.map((group) => [group.key, Color.fromCssColorString(group.color)]),
      ) as Record<SatelliteGroupKey, Color>,
    [allGroups],
  )

  const activeGroups = useMemo(
    () => allGroups.filter((group) => enabledGroups[group.key]),
    [allGroups, enabledGroups],
  )

  const allSelected = useMemo(
    () => allGroups.every((group) => enabledGroups[group.key]),
    [allGroups, enabledGroups],
  )

  const totalVisibleCount = useMemo(
    () =>
      activeGroups.reduce(
        (total, group) => total + (groupPositions[group.key]?.length ?? 0),
        0,
      ),
    [activeGroups, groupPositions],
  )

  const selectedRefreshTuning =
    refreshTuningProfiles[refreshTuningIndex] ?? refreshTuningProfiles[1]

  const refreshIntervalMs = useMemo(() => {
    let baseIntervalSec = defaultRefreshIntervalSec

    if (totalVisibleCount >= 2500) {
      baseIntervalSec = veryHeavyRefreshIntervalSec
    } else if (totalVisibleCount >= 1200) {
      baseIntervalSec = heavyRefreshIntervalSec
    } else if (totalVisibleCount >= 500) {
      baseIntervalSec = mediumRefreshIntervalSec
    } else if (activeGroups.length >= 8) {
      baseIntervalSec = heavyRefreshIntervalSec
    } else if (activeGroups.length >= 4) {
      baseIntervalSec = mediumRefreshIntervalSec
    }

    const tunedIntervalSec = baseIntervalSec * selectedRefreshTuning.multiplier
    const clampedIntervalSec = Math.max(0.55, Math.min(4.2, tunedIntervalSec))
    return Math.round(clampedIntervalSec * 1000)
  }, [activeGroups.length, selectedRefreshTuning.multiplier, totalVisibleCount])

  const visibleEntitySatellites = useMemo<VisibleSatelliteItem[]>(
    () =>
      activeGroups.flatMap((group) =>
        group.key === 'starlink'
          ? []
          : (groupPositions[group.key] ?? []).map((satellite) => ({
              group,
              satellite,
            })),
      ),
    [activeGroups, groupPositions],
  )

  const starlinkSatellites = useMemo(
    () => (enabledGroups.starlink ? groupPositions.starlink ?? [] : []),
    [enabledGroups.starlink, groupPositions.starlink],
  )

  const satelliteLookupByEntityId = useMemo(
    () => buildSatelliteLookupByEntityId(allGroups, groupPositions),
    [allGroups, groupPositions],
  )

  const liveEntityIdBySatelliteId = useMemo(
    () => buildLiveEntityIdBySatelliteId(allGroups, groupPositions),
    [allGroups, groupPositions],
  )

  const visibilityQueryLocationLabel = useMemo(() => {
    return buildVisibilityQueryLocationLabel(visibilityCity, visibilityLatitude, visibilityLongitude)
  }, [visibilityCity, visibilityLatitude, visibilityLongitude])

  const visibilityOverlayFilteredResults = useMemo(() => {
    return filterVisibilityResults(visibilityAllResults, visibilityOverlayQuery)
  }, [visibilityAllResults, visibilityOverlayQuery])

  const groupRows = useMemo(
    () => buildGroupRows(allGroups, enabledGroups, groupPositions, groupLoading, groupErrors),
    [allGroups, enabledGroups, groupErrors, groupLoading, groupPositions],
  )

  useEffect(() => {
    latestGroupPositionsRef.current = groupPositions
  }, [groupPositions])

  const searchResultItems = useMemo(
    () =>
      buildSearchResultItems({
        allGroups,
        enabledGroups,
        groupPositions,
        catalogByGroup,
        searchScope,
        searchQuery,
      }),
    [allGroups, catalogByGroup, enabledGroups, groupPositions, searchQuery, searchScope],
  )

  const handlePickEntityId = useCallback(
    (entityId: string | null) => {
      setSelectedEntityId(entityId)

      if (!entityId) {
        setSelectedSatellite(null)
        return
      }

      const selected = satelliteLookupByEntityId.get(entityId)
      setSelectedSatellite(selected ?? null)
    },
    [satelliteLookupByEntityId],
  )

  const closeSelectedSatellite = useCallback(() => {
    handlePickEntityId(null)
  }, [handlePickEntityId])

  const handleSearchResultSelect = useCallback(
    async (item: SearchResultItem) => {
      setSelectedPreset('custom')
      setEnabledGroups((prev) =>
        prev[item.groupKey] ? prev : { ...prev, [item.groupKey]: true },
      )

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
        // Ignore fallback errors: la selezione rimane attiva e il focus avverra al refresh live.
      }
    },
    [handlePickEntityId, satelliteLookupByEntityId],
  )

  useEffect(() => {
    if (!selectedEntityId || selectedSatellite) {
      return
    }

    const liveSelected = satelliteLookupByEntityId.get(selectedEntityId)
    if (!liveSelected) {
      return
    }

    setSelectedSatellite(liveSelected)
    globeRef.current?.focusOnSatellite(
      liveSelected.satellite.longitudeDeg,
      liveSelected.satellite.latitudeDeg,
      liveSelected.satellite.altitudeKm,
      selectedEntityId,
    )
  }, [satelliteLookupByEntityId, selectedEntityId, selectedSatellite])

  useEffect(() => {
    if (searchScope === 'enabled') {
      return
    }

    const groupsToLoad =
      searchScope === 'all'
        ? allGroups
        : allGroups.filter((group) => group.key === searchScope)

    const missingGroups = groupsToLoad.filter((group) => !catalogByGroup[group.key])
    if (missingGroups.length === 0) {
      return
    }

    const controller = new AbortController()

    void Promise.allSettled(
      missingGroups.map(async (group) => {
        const catalog = await fetchSatelliteCatalogByType(group.type, controller.signal)
        return { key: group.key, catalog }
      }),
    ).then((results) => {
      setCatalogByGroup((prev) => {
        const next = { ...prev }
        for (const result of results) {
          if (result.status === 'fulfilled') {
            next[result.value.key] = result.value.catalog
          }
        }
        return next
      })
    })

    return () => {
      controller.abort()
    }
  }, [allGroups, catalogByGroup, searchScope])

  const handleCompassChange = useCallback((nextCompass: CompassState) => {
    setCompass(nextCompass)
  }, [])

  const resetAuthFields = () => {
    setAuthUsernameOrEmail('')
    setAuthUsername('')
    setAuthEmail('')
    setAuthPassword('')
  }

  useEffect(() => {
    const controller = new AbortController()

    void getCurrentUser(controller.signal)
      .then((response) => {
        if (response.authenticated && response.user) {
          setAuthUser(response.user)
          setAuthInfo(`Sessione attiva: ${response.user.username}`)
          return
        }

        setAuthUser(null)
      })
      .catch(() => {
        setAuthUser(null)
      })
      .finally(() => {
        setAuthChecking(false)
      })

    return () => {
      controller.abort()
    }
  }, [])

  useEffect(() => {
    if (!authUser) {
      setOrekitStatus(null)
      setOrekitStatusError('')
      setOrekitStatusLoading(false)
      setSystemHealth(null)
      setSystemHealthError('')
      setSystemHealthLoading(false)
      return
    }

    const orekitController = new AbortController()
    const healthController = new AbortController()
    setOrekitStatusLoading(true)
    setSystemHealthLoading(true)

    void loadOrekitStatus(orekitController.signal)
      .then(({ status, error }) => {
        setOrekitStatus(status)
        setOrekitStatusError(error)
      })
      .finally(() => {
        setOrekitStatusLoading(false)
      })

    void loadSystemHealth(healthController.signal)
      .then(({ status, error }) => {
        setSystemHealth(status)
        setSystemHealthError(error)
      })
      .finally(() => {
        setSystemHealthLoading(false)
      })

    return () => {
      orekitController.abort()
      healthController.abort()
    }
  }, [authUser])

  const submitLogin = async () => {
    if (authSubmitting) {
      return
    }

    setAuthSubmitting(true)
    setAuthError('')

    try {
      const result = await executeLoginFlow({
        usernameOrEmail: authUsernameOrEmail,
        password: authPassword,
      })

      if (!result.user) {
        setAuthUser(null)
        setAuthError(result.error)
        return
      }

      setAuthUser(result.user)
      setAuthInfo(result.info)
      resetAuthFields()
    } finally {
      setAuthSubmitting(false)
    }
  }

  const submitRegister = async () => {
    if (authSubmitting) {
      return
    }

    setAuthSubmitting(true)
    setAuthError('')

    try {
      const result = await executeRegisterFlow({
        username: authUsername,
        email: authEmail,
        password: authPassword,
      })

      if (!result.user) {
        setAuthUser(null)
        setAuthError(result.error)
        return
      }

      setAuthUser(result.user)
      setAuthInfo(result.info)
      resetAuthFields()
    } finally {
      setAuthSubmitting(false)
    }
  }

  const handleLogout = async () => {
    if (authSubmitting) {
      return
    }

    setAuthSubmitting(true)
    setAuthError('')

    try {
      const result = await executeLogoutFlow()
      if (result.error) {
        setAuthError(result.error)
        return
      }

      setAuthUser(null)
      setMySightings([])
      setAuthInfo('Sessione chiusa, esegui un nuovo accesso.')
    } finally {
      setAuthSubmitting(false)
    }
  }

  useEffect(() => {
    if (!authUser) {
      setMySightings([])
      setSightingsError('')
      setSightingsLoading(false)
      return
    }

    if (openPane !== 'sightings') {
      return
    }

    const controller = new AbortController()
    setSightingsLoading(true)
    setSightingsError('')

    void fetchMySightings(controller.signal)
      .then((items) => {
        setMySightings(items)
      })
      .catch((error) => {
        if (isAxiosError(error) && error.response?.status === 401) {
          setAuthUser(null)
          setAuthInfo('Sessione scaduta. Esegui di nuovo l\'accesso.')
          setAuthError('Sessione non valida per caricare gli avvistamenti.')
          return
        }

        setSightingsError(extractAuthErrorMessage(error, 'Errore nel caricamento avvistamenti'))
      })
      .finally(() => {
        setSightingsLoading(false)
      })

    return () => {
      controller.abort()
    }
  }, [authUser, openPane])

  const handleReportSighting = async () => {
    if (!selectedSatellite || reportingSighting) {
      return
    }

    const hasBrowserCoords = sightingLatitude !== null && sightingLongitude !== null
    const hasCity = sightingCity.trim().length > 0

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
        setAuthUser(null)
        setAuthInfo('Sessione scaduta. Esegui di nuovo l\'accesso.')
        setAuthError('Sessione non valida per registrare l\'avvistamento.')
        return
      }
      setSightingsError(extractAuthErrorMessage(error, 'Errore durante la registrazione avvistamento'))
    } finally {
      setReportingSighting(false)
    }
  }

  const handleUseBrowserLocation = () => {
    if (locatingBrowser) {
      return
    }

    const precheckError = getBrowserGeolocationPrecheckError(
      Boolean(navigator.geolocation),
      window.isSecureContext,
    )
    if (precheckError) {
      setSightingsError(precheckError)
      return
    }

    const geolocation = navigator.geolocation
    if (!geolocation) {
      setSightingsError('Geolocalizzazione non disponibile nel browser.')
      return
    }

    setLocatingBrowser(true)
    setSightingsError('')

    void requestBrowserLocation(geolocation)
      .then((location) => {
        setSightingLatitude(location.latitude)
        setSightingLongitude(location.longitude)
        setSightingAltitude(location.altitude)
        setSightingInfo('Posizione browser acquisita.')
      })
      .catch((error) => {
        setSightingsError(extractGeolocationErrorMessage(error as GeolocationPositionError))
      })
      .finally(() => {
        setLocatingBrowser(false)
      })
  }

  const handleUseBrowserLocationForVisibility = () => {
    if (visibilityLocatingBrowser) {
      return
    }

    const precheckError = getBrowserGeolocationPrecheckError(
      Boolean(navigator.geolocation),
      window.isSecureContext,
    )
    if (precheckError) {
      setVisibilityError(precheckError)
      return
    }

    const geolocation = navigator.geolocation
    if (!geolocation) {
      setVisibilityError('Geolocalizzazione non disponibile nel browser.')
      return
    }

    setVisibilityLocatingBrowser(true)
    setVisibilityError('')

    void requestBrowserLocation(geolocation)
      .then((location) => {
        setVisibilityLatitude(location.latitude)
        setVisibilityLongitude(location.longitude)
        setVisibilityAltitude(location.altitude)
        setVisibilityInfo('Posizione browser attiva per il calcolo visibilita.')
      })
      .catch((error) => {
        setVisibilityError(extractGeolocationErrorMessage(error as GeolocationPositionError))
      })
      .finally(() => {
        setVisibilityLocatingBrowser(false)
      })
  }

  const handleCalculateVisibility = async () => {
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
  }

  const openVisibilityFullResultsOverlay = useCallback(() => {
    if (visibilityAllResults.length === 0) {
      setVisibilityError('Nessun risultato completo disponibile da mostrare.')
      return
    }
    setVisibilityOverlayQuery('')
    setVisibilityOverlayOpen(true)
  }, [visibilityAllResults.length])

  const exportVisibilityOverlayCsv = useCallback(() => {
    if (visibilityOverlayFilteredResults.length === 0) {
      setVisibilityError('Nessun risultato da esportare in CSV.')
      return
    }

    downloadVisibilityResultsCsv(visibilityOverlayFilteredResults)
  }, [visibilityOverlayFilteredResults])

  const handleFocusFromVisibility = (pass: UpcomingPass) => {
    const entityId = liveEntityIdBySatelliteId.get(pass.satelliteId)
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

    void fetchSatellitePositionById(pass.satelliteId)
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
        setVisibilityError('Impossibile fare focus: posizione live non disponibile per questo satellite.')
      })
  }

  useEffect(() => {
    if (!authUser) {
      setGroupPositions({})
      setGroupErrors({})
      setGroupLoading({})
      setIsRefreshing(false)
      return
    }

    if (activeGroups.length === 0) {
      setGroupPositions({})
      setGroupErrors({})
      setGroupLoading({})
      return
    }

    let isMounted = true
    let refreshController: AbortController | null = null

    const loadGroups = async (requestId: number, signal?: AbortSignal) => {
      setIsRefreshing(true)

      for (const group of activeGroups) {
        if (!isMounted) {
          if (latestRequestIdRef.current === requestId) {
            setIsRefreshing(false)
          }
          return
        }
        setGroupLoading((prev) => ({ ...prev, [group.key]: true }))
      }

      let results: PromiseSettledResult<{
        key: SatelliteGroupKey
        positions: SatellitePosition[]
      }>[]

      if (activeGroups.length > 1) {
        try {
          const allPositions = await fetchAllSatellitePositions(signal)
          const positionsByType = new Map<string, SatellitePosition[]>()

          for (const position of allPositions) {
            const typeKey = (position.satelliteType ?? '').toLowerCase()
            if (!typeKey) {
              continue
            }

            const bucket = positionsByType.get(typeKey)
            if (bucket) {
              bucket.push(position)
            } else {
              positionsByType.set(typeKey, [position])
            }
          }

          results = activeGroups.map((group) => ({
            status: 'fulfilled',
            value: {
              key: group.key,
              positions: positionsByType.get(group.type.toLowerCase()) ?? [],
            },
          }))
        } catch (error) {
          results = activeGroups.map(() => ({
            status: 'rejected',
            reason: error,
          }))
        }
      } else {
        results = await Promise.allSettled(
          activeGroups.map(async (group) => {
            const positions = await group.loadPositions(signal)
            return { key: group.key, positions }
          }),
        )
      }

      if (!isMounted || signal?.aborted) {
        return
      }

      const nextPositions: GroupPositionsState = {}
      const nextErrors: GroupErrorState = {}
      const nextLoading: GroupLoadingState = {}
      let unauthorizedDetected = false

      activeGroups.forEach((group, index) => {
        const result = results[index]
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

          // Con molti gruppi attivi, timeout/cancel/rete possono capitare: evitiamo falsi allarmi
          // se abbiamo gia dati precedenti da mostrare.
          if (isCanceled || ((isTimeout || isTransientUpstream || isNetworkError) && hasPreviousData)) {
            nextErrors[group.key] = ''
            return
          }
        }

        nextErrors[group.key] = `Errore caricamento ${group.label}`
      })

      if (unauthorizedDetected) {
        setAuthUser(null)
        setAuthError('Sessione scaduta. Esegui di nuovo l\'accesso.')
        setAuthInfo('Sessione non valida per le API live.')
        setGroupPositions({})
        setGroupErrors({})
        setGroupLoading({})
        setHasLoadedOnce(false)
        return
      }

      setGroupPositions(nextPositions)
      setGroupErrors(nextErrors)
      setGroupLoading(nextLoading)
      setHasLoadedOnce(true)
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

      void loadGroups(requestId, refreshController.signal)
        .catch(() => {
          // Errore gestito dai singoli gruppi con Promise.allSettled.
        })
        .finally(() => {
          finalizeLoadRequest(requestId)
        })
    }

    startLoad()

    const refreshId = window.setInterval(() => {
      startLoad()
    }, refreshIntervalMs)

    return () => {
      isMounted = false
      refreshController?.abort()
      window.clearInterval(refreshId)
      inFlightRequestRef.current = false
      setIsRefreshing(false)
    }
  }, [activeGroups, authUser, refreshIntervalMs])

  const toggleGroup = (key: SatelliteGroupKey) => {
    setSelectedPreset('custom')
    setEnabledGroups((prev) => ({
      ...prev,
      [key]: !prev[key],
    }))
  }

  const toggleAllGroups = () => {
    const nextValue = !allSelected
    setSelectedPreset('custom')
    setEnabledGroups(
      Object.fromEntries(allGroups.map((group) => [group.key, nextValue])) as Record<
        SatelliteGroupKey,
        boolean
      >,
    )
  }

  const applyGroupPreset = (preset: GroupPreset) => {
    const nextEnabled = buildEnabledGroupsFromPreset(allGroups, preset)
    if (!nextEnabled) {
      return
    }

    setEnabledGroups(nextEnabled)
  }

  const panelWidth = 470

  if (authChecking || !authUser) {
    return (
      <AuthPanel
        authChecking={authChecking}
        authMode={authMode}
        authInfo={authInfo}
        authError={authError}
        authSubmitting={authSubmitting}
        authUsernameOrEmail={authUsernameOrEmail}
        authUsername={authUsername}
        authEmail={authEmail}
        authPassword={authPassword}
        onSwitchMode={(mode) => {
          setAuthMode(mode)
          setAuthError('')
        }}
        onAuthUsernameOrEmailChange={setAuthUsernameOrEmail}
        onAuthUsernameChange={setAuthUsername}
        onAuthEmailChange={setAuthEmail}
        onAuthPasswordChange={setAuthPassword}
        onSubmit={() => {
          if (authMode === 'login') {
            void submitLogin()
            return
          }
          void submitRegister()
        }}
      />
    )
  }

  if (!ionToken) {
    return (
      <MusicPlayerProvider>
        <main className="app-shell">
          <aside className="panel-section">
            <section className="panel-component panel-component-token">
              <h1>Cesium token mancante</h1>
              <p>
                Aggiungi <strong>VITE_CESIUM_TOKEN</strong> nel file .env in root e riavvia il
                frontend.
              </p>
            </section>
          </aside>
          <section className="viewer-section" />
        </main>
      </MusicPlayerProvider>
    )
  }

  return (
    <MusicPlayerProvider>
      <main className={`app-shell ${focusGlobeMode ? 'focus-mode' : ''}`}>
        {!focusGlobeMode ? (
          <aside className="panel-section">


            <section className="panel-component panel-component-top">
              <PanelTopSection
                username={authUser.username}
                orekitStatus={orekitStatus}
                orekitStatusLoading={orekitStatusLoading}
                orekitStatusError={orekitStatusError}
                systemHealth={systemHealth}
                systemHealthLoading={systemHealthLoading}
                systemHealthError={systemHealthError}
                onLogout={() => {
                  void handleLogout()
                }}
                onPingSystemHealth={() => {
                  setSystemHealthLoading(true)
                  setSystemHealthError('')
                  void loadSystemHealth()
                    .then(({ status, error }) => {
                      setSystemHealth(status)
                      setSystemHealthError(error)
                      // Logga il risultato in console
                      console.log('[HEALTH CHECK]', status, error)
                    })
                    .finally(() => {
                      setSystemHealthLoading(false)
                    })
                }}
              />
            </section>

            <section className="panel-component panel-component-main">
              <div className="panel-layout">
                <section className="panel-left">
                  <p className="updated-at">Live feed /api/satellites/positions</p>
                  <p className="updated-at">Visibili: {totalVisibleCount} satelliti</p>

                  <div className="sidebar-split">
                    <section className={`sidebar-buttons-shell ${openPane ? 'expanded' : ''}`}>
                      <PanelSidebarButtons
                        openPane={openPane}
                        onTogglePane={(pane) => {
                          setOpenPane((prev) => (prev === pane ? null : pane))
                        }}
                      />

                      {openPane ? (
                        <section className="sidebar-collapsible-panel side-drawer">
                        {openPane === 'groups' ? (
                          <div id="panel-groups">
                            <GroupsPanel
                              allSelected={allSelected}
                              selectedPreset={selectedPreset}
                              searchScope={searchScope}
                              searchQuery={searchQuery}
                              searchResultItems={searchResultItems}
                              searchScopeOptions={allGroups.map((group) => ({
                                key: group.key,
                                label: group.label,
                              }))}
                              groupRows={groupRows}
                              onToggleAll={toggleAllGroups}
                              onPresetChange={(presetValue) => {
                                const preset = presetValue as GroupPreset
                                setSelectedPreset(preset)
                                applyGroupPreset(preset)
                              }}
                              onSearchScopeChange={(scopeValue) => {
                                setSearchScope(scopeValue)
                              }}
                              onSearchQueryChange={setSearchQuery}
                              onSearchResultSelect={(item) => {
                                void handleSearchResultSelect(item)
                              }}
                              onToggleGroup={(groupKey) => {
                                toggleGroup(groupKey)
                              }}
                            />
                          </div>
                        ) : null}

                        {openPane === 'satellites' ? (
                          <div id="panel-satellites">
                            <SatellitesPanel
                              autoRotate={autoRotate}
                              showBackSideSatellites={showBackSideSatellites}
                              hasLoadedOnce={hasLoadedOnce}
                              isRefreshing={isRefreshing}
                              refreshIntervalMs={refreshIntervalMs}
                              refreshProfileLabel={selectedRefreshTuning.label}
                              refreshTuningIndex={refreshTuningIndex}
                              compass={compass}
                              onZoomIn={() => globeRef.current?.zoomIn()}
                              onZoomOut={() => globeRef.current?.zoomOut()}
                              onGoHome={() => globeRef.current?.goToInitialView()}
                              onAlignAxis={() => globeRef.current?.alignToEarthAxis()}
                              onToggleAutoRotate={() => setAutoRotate((prev) => !prev)}
                              onToggleBackSideSatellites={() =>
                                setShowBackSideSatellites((prev) => !prev)
                              }
                              onRefreshTuningIndexChange={setRefreshTuningIndex}
                            />
                          </div>
                        ) : null}

                        {openPane === 'visibility' ? (
                          <div id="panel-visibility">
                            <VisibilityPanel
                              visibilityHours={visibilityHours}
                              visibilityMinElevation={visibilityMinElevation}
                              visibilityCity={visibilityCity}
                              visibilityLocatingBrowser={visibilityLocatingBrowser}
                              visibilityLoading={visibilityLoading}
                              visibilityLatitude={visibilityLatitude}
                              visibilityLongitude={visibilityLongitude}
                              visibilityInfo={visibilityInfo}
                              visibilityError={visibilityError}
                              visibilityResults={visibilityResults}
                              visibilityResultsTotal={visibilityAllResults.length}
                              onVisibilityHoursChange={setVisibilityHours}
                              onVisibilityMinElevationChange={setVisibilityMinElevation}
                              onVisibilityCityChange={setVisibilityCity}
                              onUseBrowserLocation={handleUseBrowserLocationForVisibility}
                              onCalculateVisibility={() => {
                                void handleCalculateVisibility()
                              }}
                              onOpenFullResults={openVisibilityFullResultsOverlay}
                              onFocusFromVisibility={handleFocusFromVisibility}
                            />
                          </div>
                        ) : null}

                        {openPane === 'sightings' ? (
                          <div id="panel-sightings">
                            <SightingsPanel
                              sightingInfo={sightingInfo}
                              sightingsError={sightingsError}
                              sightingsLoading={sightingsLoading}
                              mySightings={mySightings}
                            />
                          </div>
                        ) : null}

                        {openPane === 'community' ? (
                          <div id="panel-community">
                            <CommunityPanel
                              authUser={authUser}
                              selectedSatelliteId={selectedSatellite?.satellite.satelliteId ?? null}
                              selectedSatelliteName={selectedSatellite?.satellite.satelliteName ?? null}
                            />
                          </div>
                        ) : null}

                        {openPane === 'music' ? (
                          <div id="panel-music">
                            <MusicPanel />
                          </div>
                        ) : null}
                        </section>
                      ) : null}
                    </section>
                  </div>
                </section>
              </div>
            </section>
          </aside>
        ) : null}

        <section className="viewer-section">
          <MusicFloatingPlayer floatingStyle={{ left: focusGlobeMode ? 10 : panelWidth + 20, top: 58 }} />

          <CompassPanel
            headingDeg={compass.headingDeg}
            hideOnMobile={!selectedSatellite}
            collapsed={compassCollapsed}
            onToggleCollapsed={() => setCompassCollapsed((prev) => !prev)}
          />

          <button
            type="button"
            className="focus-toggle"
            style={{ left: focusGlobeMode ? 10 : panelWidth + 20, right: 'auto' }}
            onClick={() => setFocusGlobeMode((prev) => !prev)}
          >
            {focusGlobeMode ? 'Mostra pannello dati' : 'Focus Globe'}
          </button>
          {selectedSatellite ? (
            <aside className="viewer-hud">
            <section className="details-card hud-details">
              <h3>Dettagli satellite</h3>
              <div className="details-head">
                <strong>{selectedSatellite.satellite.satelliteName}</strong>
                <div className="details-head-actions">
                  <button
                    type="button"
                    className="primary-sighting-button"
                    onClick={() => {
                      void handleReportSighting()
                    }}
                    disabled={reportingSighting}
                  >
                    {reportingSighting ? 'Invio...' : 'Invia avvistamento'}
                  </button>
                  <button type="button" onClick={closeSelectedSatellite}>Chiudi</button>
                </div>
              </div>
              <div className="details-grid">
                <span>Gruppo</span>
                <span>{selectedSatellite.groupLabel}</span>
                <span>NORAD</span>
                <span>{selectedSatellite.satellite.noradCatId}</span>
                <span>Lat/Lon</span>
                <span>
                  {selectedSatellite.satellite.latitudeDeg.toFixed(2)} /{' '}
                  {selectedSatellite.satellite.longitudeDeg.toFixed(2)}
                </span>
                <span>Altitudine</span>
                <span>{selectedSatellite.satellite.altitudeKm.toFixed(2)} km</span>
                <span>Periodo orbitale</span>
                <span>{selectedSatellite.satellite.orbitalPeriodMinutes.toFixed(2)} min</span>
                <span>Mean motion</span>
                <span>{selectedSatellite.satellite.meanMotion.toFixed(4)}</span>
                {typeof selectedSatellite.satellite.velocityKmh === 'number' ? (
                  <>
                    <span>Velocita</span>
                    <span>{selectedSatellite.satellite.velocityKmh.toFixed(1)} km/h</span>
                  </>
                ) : null}
                {typeof selectedSatellite.satellite.directionDeg === 'number' ? (
                  <>
                    <span>Direzione</span>
                    <span>{selectedSatellite.satellite.directionDeg.toFixed(1)}deg</span>
                  </>
                ) : null}
                <span>Aggiornato</span>
                <span>{selectedSatellite.satellite.calculatedAtUtc}</span>
              </div>
              <div className="sighting-actions">
                <div className="sighting-location-row">
                  <input
                    type="text"
                    className="sighting-city-input"
                    value={sightingCity}
                    onChange={(event) => setSightingCity(event.target.value)}
                    placeholder="Inserisci citta (alternativa al browser)"
                  />
                  <button
                    type="button"
                    className="sighting-pin-button"
                    onClick={handleUseBrowserLocation}
                    disabled={locatingBrowser}
                    title="Usa posizione browser"
                    aria-label="Usa posizione browser"
                  >
                    {locatingBrowser ? (
                      '...'
                    ) : (
                      <svg viewBox="0 0 24 24" aria-hidden="true" focusable="false">
                        <path d="M12 2C8.14 2 5 5.14 5 9c0 5.08 6.13 12.31 6.39 12.62a.8.8 0 0 0 1.22 0C12.87 21.31 19 14.08 19 9c0-3.86-3.14-7-7-7Zm0 9.5A2.5 2.5 0 1 1 12 6.5a2.5 2.5 0 0 1 0 5Z" />
                      </svg>
                    )}
                  </button>
                </div>
                {sightingLatitude !== null && sightingLongitude !== null ? (
                  <small className="sighting-coords">
                    Posizione browser: {sightingLatitude.toFixed(4)}, {sightingLongitude.toFixed(4)}
                  </small>
                ) : null}
                {sightingInfo ? <small className="sighting-coords">{sightingInfo}</small> : null}
                {sightingsError ? <small className="sighting-error-inline">{sightingsError}</small> : null}
                <button
                  type="button"
                  onClick={() => {
                    void handleReportSighting()
                  }}
                  disabled={reportingSighting}
                >
                  {reportingSighting ? 'Invio avvistamento...' : 'Conferma avvistamento'}
                </button>
              </div>
            </section>
          </aside>
          ) : null}

          <SatelliteGlobe
            ref={globeRef}
            autoRotate={autoRotate}
            showBackSideSatellites={showBackSideSatellites}
            groupColorMap={groupColorMap}
            selectedEntityId={selectedEntityId}
            starlinkSatellites={starlinkSatellites}
            visibleEntitySatellites={visibleEntitySatellites}
            onPickEntityId={handlePickEntityId}
            onCompassChange={handleCompassChange}
          />
        </section>

        {visibilityOverlayOpen ? (
          <section className="visibility-overlay" role="dialog" aria-modal="true" aria-label="Lista completa passaggi visibili">
            <div className="visibility-overlay-backdrop" onClick={() => setVisibilityOverlayOpen(false)} />
            <article className="visibility-overlay-card">
              <header className="visibility-overlay-header">
                <h2>Passaggi visibili - Lista completa</h2>
                <div className="visibility-overlay-header-actions">
                  <button type="button" onClick={exportVisibilityOverlayCsv}>
                    Export CSV
                  </button>
                  <button type="button" onClick={() => setVisibilityOverlayOpen(false)}>
                    Chiudi
                  </button>
                </div>
              </header>
              <p className="visibility-overlay-meta">
                Totale risultati: {visibilityAllResults.length} | Mostrati: {visibilityOverlayFilteredResults.length} | Filtri: {visibilityHours}h, elevazione minima {visibilityMinElevation}deg
              </p>
              <p className="visibility-overlay-meta">{visibilityQueryLocationLabel}</p>
              <label className="visibility-overlay-filter-row">
                Cerca nella lista completa
                <input
                  type="text"
                  value={visibilityOverlayQuery}
                  onChange={(event) => setVisibilityOverlayQuery(event.target.value)}
                  placeholder="Nome satellite, NORAD, condizione..."
                />
              </label>
              <div className="visibility-overlay-table-wrap">
                <table className="visibility-overlay-table">
                  <thead>
                    <tr>
                      <th>#</th>
                      <th>Satellite</th>
                      <th>NORAD ID</th>
                      <th>Rise</th>
                      <th>Set</th>
                      <th>Elev. max</th>
                      <th>Magnitudine</th>
                      <th>Condizione</th>
                      <th>Visibilita</th>
                    </tr>
                  </thead>
                  <tbody>
                    {visibilityOverlayFilteredResults.map((pass, index) => (
                      <tr key={`${pass.satelliteId}-${pass.riseTime}-${pass.setTime}-${index}`}>
                        <td>{index + 1}</td>
                        <td>{pass.satelliteName}</td>
                        <td>{pass.satelliteId}</td>
                        <td>{new Date(pass.riseTime).toLocaleString('it-IT')}</td>
                        <td>{new Date(pass.setTime).toLocaleString('it-IT')}</td>
                        <td>{pass.maxElevation.toFixed(1)}deg</td>
                        <td>{pass.estimatedMagnitude.toFixed(1)}</td>
                        <td>{pass.observingCondition}</td>
                        <td>{pass.visibility}</td>
                      </tr>
                    ))}
                  </tbody>
                </table>
              </div>
            </article>
          </section>
        ) : null}
      </main>
    </MusicPlayerProvider>
  )
}

export default App
