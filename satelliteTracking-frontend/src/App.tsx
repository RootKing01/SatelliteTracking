import {
  useSearchResultSelect,
  useReportSighting,
  useCalculateVisibility,
  useFocusBySatelliteId,
} from './hooks/useInteractionHandlers'
import { startTransition, useCallback, useEffect, useMemo, useRef, useState } from 'react'
import { isAxiosError } from 'axios'
import { Color, Ion } from 'cesium'
import { getCurrentUser, type AuthUser } from './api/authClient'
import { type OrekitStatusResponse } from './api/orekitStatusClient'
import { type SystemHealthResponse } from './api/systemHealthClient'
import { fetchSatelliteCatalogByType, type SatelliteCatalogItem } from './api/satelliteCatalogClient'
import { fetchSatellitePositionById } from './api/satellitePositionsClient'
import { fetchSatelliteGroupsStats } from './api/groups/groupsStatsClient'
import { fetchMySightings, type SatelliteSighting } from './api/sightingsClient'
import { type UpcomingPass } from './api/satelliteVisibilityClient'
import { satelliteGroupSources } from './api/groups'
import type { SatelliteGroupKey, SatelliteGroupSource } from './api/groups/types'
import { extractAuthErrorMessage } from './helpers/appErrorHelpers'
import { handleUseBrowserLocationImpl, handleUseBrowserLocationForVisibilityImpl } from './helpers/locationHelpers'
import {
  executeLoginFlow,
  executeLogoutFlow,
  executeRegisterFlow,
} from './helpers/authFlowHelpers'
import { buildEnabledGroupsFromPreset, createDefaultEnabledGroups, type GroupPreset } from './helpers/groupHelpers'
import { buildRuntimeSatelliteGroupSources } from './helpers/groupDiscoveryHelpers'
import { buildGroupRows } from './helpers/groupViewHelpers'
import {
  buildLiveEntityIdBySatelliteId,
  buildSatelliteLookupByEntityId,
  type SelectedSatelliteState,
} from './helpers/satelliteSelectionHelpers'
import {
  buildSearchResultItems,
  type SatelliteSearchScope,
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
import clearAuthFields from './helpers/authHelpers'
// visibility helpers moved to interactionHandlers where needed
import { AuthPanel } from './components/auth/AuthPanel'
import { PanelSidebarButtons, type SidebarPane } from './components/layout/PanelSidebarButtons'
import { PanelTopSection } from './components/layout/PanelTopSection'
import { SatelliteGlobe, type SatelliteGlobeHandle, type VisibleSatelliteItem } from './components/SatelliteGlobe'
import { computeMoonPosition } from './components/Moon'
import { CommunityPanel, GroupsPanel, MusicFloatingPlayer, MusicPanel, MusicPlayerProvider, SatellitesPanel, SightingsPanel, VisibilityPanel } from './components/panels'
import type { SatellitePosition } from './types/satellite'
import './App.css'
import './styles/orekit-badge.css'
import './styles/mobile-smartphone.css'
import useMusicWidgetHeight, { computeQuickZoomTop } from './helpers/appHelpers'

const ionToken =
  import.meta.env.VITE_CESIUM_TOKEN ?? import.meta.env.VITE_CESIUM_ION_TOKEN

if (ionToken && !import.meta.env.DEV) {
  Ion.defaultAccessToken = ionToken
}

type GroupPositionsState = Partial<Record<SatelliteGroupKey, SatellitePosition[]>>
type GroupLoadingState = Partial<Record<SatelliteGroupKey, boolean>>
type GroupErrorState = Partial<Record<SatelliteGroupKey, string>>

const defaultEnabledGroups = createDefaultEnabledGroups(satelliteGroupSources)

function computeRefreshIntervalMs(totalVisibleCount: number) {
  if (totalVisibleCount <= 50) {
    return 1400
  }
  if (totalVisibleCount <= 250) {
    return 2000
  }
  if (totalVisibleCount <= 1000) {
    return 3000
  }
  if (totalVisibleCount <= 3000) {
    return 3500
  }
  if (totalVisibleCount <= 8000) {
    return 4000
  }
  if (totalVisibleCount <= 12000) {
    return 4500
  }
  return 5000
}

type AuthMode = 'login' | 'register'

function App() {
  const globeRef = useRef<SatelliteGlobeHandle>(null)

  const [discoveredCanonicalGroupKeys, setDiscoveredCanonicalGroupKeys] = useState<string[]>([])

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
  const [showMoon, setShowMoon] = useState(true)
  const [moonDetailsOpen, setMoonDetailsOpen] = useState(false)
  const [compactMobileViewport, setCompactMobileViewport] = useState(false)
  const [landscapeMobileViewport, setLandscapeMobileViewport] = useState(false)
  const latestRequestIdRef = useRef(0)
  const inFlightRequestRef = useRef(false)
  const latestGroupPositionsRef = useRef<GroupPositionsState>({})
  const [authChecking, setAuthChecking] = useState(true)
  const [authSubmitting, setAuthSubmitting] = useState(false)
  const [authMode, setAuthMode] = useState<AuthMode>('login')
  const [authUser, setAuthUser] = useState<AuthUser | null>(null)
  const [authUsernameOrEmail, setAuthUsernameOrEmail] = useState('')
  const [authUsername, setAuthUsername] = useState('')
  const [authEmail, setAuthEmail] = useState('')
  const [authPassword, setAuthPassword] = useState('')
  const [authPasswordConfirm, setAuthPasswordConfirm] = useState('')
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

  const allGroups = useMemo(
    () =>
      buildRuntimeSatelliteGroupSources(
        satelliteGroupSources as readonly SatelliteGroupSource[],
        discoveredCanonicalGroupKeys,
      ),
    [discoveredCanonicalGroupKeys],
  )

  // Measured height of the floating music widget so floating controls can follow it
  const musicWidgetHeight = useMusicWidgetHeight()
  const desktopMusicTop = 8
  const desktopFocusTop = musicWidgetHeight > 0 ? Math.max(58, musicWidgetHeight + 16) : 58
  const desktopZoomTop = musicWidgetHeight > 0 ? Math.max(108, musicWidgetHeight + 86) : 108

  const groupColorMap = useMemo(
    () =>
      Object.fromEntries(
        allGroups.map((group) => [group.key, Color.fromCssColorString(group.color)]),
      ) as Record<SatelliteGroupKey, Color>,
    [allGroups],
  )

  useEffect(() => {
    const controller = new AbortController()

    void fetchSatelliteGroupsStats(controller.signal)
      .then(({ stats }) => {
        setDiscoveredCanonicalGroupKeys(Object.keys(stats))
      })
      .catch(() => {
        setDiscoveredCanonicalGroupKeys([])
      })

    return () => {
      controller.abort()
    }
  }, [])

  useEffect(() => {
    const presetEnabled = buildEnabledGroupsFromPreset(allGroups, selectedPreset)
    if (presetEnabled) {
      setEnabledGroups(presetEnabled)
      return
    }

    setEnabledGroups((prev) => {
      const next = { ...prev }
      for (const group of allGroups) {
        if (next[group.key] === undefined) {
          next[group.key] = false
        }
      }
      return next
    })
  }, [allGroups, selectedPreset])

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

  const refreshIntervalMs = useMemo(
    () => computeRefreshIntervalMs(totalVisibleCount),
    [totalVisibleCount],
  )

  useEffect(() => {
    const updateViewportFlags = () => {
      const isPointerCoarse = window.matchMedia('(pointer: coarse)').matches
      const compact = window.innerWidth <= 760 || (window.innerWidth <= 900 && isPointerCoarse)
      setCompactMobileViewport(compact)
      setLandscapeMobileViewport(compact && window.matchMedia('(orientation: landscape)').matches)
    }

    updateViewportFlags()

    const resizeHandler = () => updateViewportFlags()
    window.addEventListener('resize', resizeHandler, { passive: true })
    window.addEventListener('orientationchange', resizeHandler, { passive: true })

    return () => {
      window.removeEventListener('resize', resizeHandler)
      window.removeEventListener('orientationchange', resizeHandler)
    }
  }, [])

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
    () =>
      openPane === 'groups'
        ? buildGroupRows(allGroups, enabledGroups, groupPositions, groupLoading, groupErrors)
        : [],
    [allGroups, enabledGroups, groupErrors, groupLoading, groupPositions, openPane],
  )

  useEffect(() => {
    latestGroupPositionsRef.current = groupPositions
  }, [groupPositions])

  const searchResultItems = useMemo(
    () =>
      openPane === 'groups'
        ? buildSearchResultItems({
            allGroups,
            enabledGroups,
            groupPositions,
            catalogByGroup,
            searchScope,
            searchQuery,
          })
        : [],
    [allGroups, catalogByGroup, enabledGroups, groupPositions, openPane, searchQuery, searchScope],
  )

  const handlePickEntityId = useCallback(
    (entityId: string | null) => {
      setSelectedEntityId(entityId)

      if (entityId === 'moon-entity') {
        setMoonDetailsOpen(true)
      }

      if (!entityId) {
        setSelectedSatellite(null)
        setMoonDetailsOpen(false)
        return
      }

      const selected = satelliteLookupByEntityId.get(entityId)
      setSelectedSatellite(selected ?? null)
      if (entityId !== 'moon-entity') {
        setMoonDetailsOpen(false)
      }
    },
    [satelliteLookupByEntityId],
  )

  const closeSelectedSatellite = useCallback(() => {
    handlePickEntityId(null)
  }, [handlePickEntityId])

  const searchScopeOptions = useMemo(
    () => allGroups.map((group) => ({ key: group.key, label: group.label })),
    [allGroups],
  )

  const handleToggleAllGroups = useCallback(() => {
    const nextValue = !allSelected
    setSelectedPreset('custom')
    setEnabledGroups(
      Object.fromEntries(allGroups.map((group) => [group.key, nextValue])) as Record<
        SatelliteGroupKey,
        boolean
      >,
    )
  }, [allGroups, allSelected])

  const handlePresetChange = useCallback(
    (presetValue: string) => {
      const preset = presetValue as GroupPreset
      setSelectedPreset(preset)
      const nextEnabled = buildEnabledGroupsFromPreset(allGroups, preset)
      if (nextEnabled) {
        setEnabledGroups(nextEnabled)
      }
    },
    [allGroups],
  )

  const handleSearchScopeChange = useCallback((scopeValue: string) => {
    setSearchScope(scopeValue as SatelliteSearchScope)
  }, [])

  const handleSearchQueryChange = useCallback((query: string) => {
    setSearchQuery(query)
  }, [])

  const handleSearchResultSelect = useSearchResultSelect({
    setSelectedPreset,
    setEnabledGroups,
    handlePickEntityId,
    satelliteLookupByEntityId,
    globeRef,
  })

  const handleSearchResultSelectCallback = useCallback(
    (item: Parameters<typeof handleSearchResultSelect>[0]) => {
      void handleSearchResultSelect(item)
    },
    [handleSearchResultSelect],
  )

  const handleToggleGroupCallback = useCallback((groupKey: SatelliteGroupKey) => {
    setSelectedPreset('custom')
    setEnabledGroups((prev) => ({
      ...prev,
      [groupKey]: !prev[groupKey],
    }))
  }, [])

  const handleZoomIn = useCallback(() => globeRef.current?.zoomIn(), [])
  const handleZoomOut = useCallback(() => globeRef.current?.zoomOut(), [])
  const handleGoHome = useCallback(() => globeRef.current?.goToInitialView(), [])
  const handleAlignAxis = useCallback(() => globeRef.current?.alignToEarthAxis(), [])
  const handleToggleAutoRotate = useCallback(() => {
    setAutoRotate((prev) => !prev)
  }, [])
  const handleToggleBackSideSatellites = useCallback(() => {
    setShowBackSideSatellites((prev) => !prev)
  }, [])
  const handleToggleShowMoon = useCallback(() => {
    setShowMoon((prev) => !prev)
  }, [])
  const handlePingSystemHealth = useCallback(() => {
    setSystemHealthLoading(true)
    setSystemHealthError('')
    void loadSystemHealth()
      .then(({ status, error }) => {
        setSystemHealth(status)
        setSystemHealthError(error)
        console.log('[HEALTH CHECK]', status, error)
      })
      .finally(() => {
        setSystemHealthLoading(false)
      })
  }, [])

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
    if (openPane !== 'groups') {
      return
    }

    if (searchQuery.trim().length < 2) {
      return
    }

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
      startTransition(() => {
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
    })

    return () => {
      controller.abort()
    }
  }, [allGroups, catalogByGroup, openPane, searchQuery, searchScope])

  const resetAuthFields = () => {
    // delegate to helper to keep App small
    clearAuthFields(setAuthUsernameOrEmail, setAuthUsername, setAuthEmail, setAuthPassword)
    setAuthPasswordConfirm('')
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

    if (authPassword !== authPasswordConfirm) {
      setAuthError('Le password non coincidono.')
      return
    }

    setAuthSubmitting(true)
    setAuthError('')

    try {
      const result = await executeRegisterFlow({
        username: authUsername,
        email: authEmail,
        password: authPassword,
        passwordConfirm: authPasswordConfirm,
      })

      if (!result.user) {
        setAuthUser(null)
        setAuthError(result.error)
        return
      }

      setAuthUser(result.user)
      setAuthInfo(result.info)
      resetAuthFields()
      setAuthPasswordConfirm('')
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
      resetAuthFields()
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

  const handleReportSighting = useReportSighting({
    getSelectedSatellite: () => selectedSatellite,
    getReportingSighting: () => reportingSighting,
    getSightingLatitude: () => sightingLatitude,
    getSightingLongitude: () => sightingLongitude,
    getSightingAltitude: () => sightingAltitude,
    getSightingCity: () => sightingCity,
    setSightingsError: (s: string) => setSightingsError(s),
    setReportingSighting: (b: boolean) => setReportingSighting(b),
    setSightingInfo: (s: string) => setSightingInfo(s),
    setMySightings: (updater: any) => setMySightings(updater),
    setOpenPane: (p: string) => setOpenPane(p as any),
    setAuthUser: (u: any) => setAuthUser(u),
    setAuthInfo: (s: string) => setAuthInfo(s),
    setAuthError: (s: string) => setAuthError(s),
  })

  const handleUseBrowserLocation = () => {
    handleUseBrowserLocationImpl({
      locatingBrowser,
      setLocatingBrowser,
      setSightingsError,
      setSightingLatitude,
      setSightingLongitude,
      setSightingAltitude,
      setSightingInfo,
    })
  }

  const handleUseBrowserLocationForVisibility = () => {
    handleUseBrowserLocationForVisibilityImpl({
      visibilityLocatingBrowser,
      setVisibilityLocatingBrowser,
      setVisibilityError,
      setVisibilityLatitude,
      setVisibilityLongitude,
      setVisibilityAltitude,
      setVisibilityInfo,
    })
  }

  const handleCalculateVisibility = useCalculateVisibility({
    getVisibilityLoading: () => visibilityLoading,
    setVisibilityLoading: (b: boolean) => setVisibilityLoading(b),
    setVisibilityError: (s: string) => setVisibilityError(s),
    setVisibilityInfo: (s: string) => setVisibilityInfo(s),
    visibilityCity,
    visibilityHours,
    visibilityMinElevation,
    getVisibilityLatitude: () => visibilityLatitude,
    getVisibilityLongitude: () => visibilityLongitude,
    getVisibilityAltitude: () => visibilityAltitude,
    setVisibilityAllResults: (r: any[]) => setVisibilityAllResults(r),
    setVisibilityResults: (r: any[]) => setVisibilityResults(r),
    setVisibilityOverlayOpen: (b: boolean) => setVisibilityOverlayOpen(b),
    setAuthUser: (u: any) => setAuthUser(u),
    setAuthInfo: (s: string) => setAuthInfo(s),
    setAuthError: (s: string) => setAuthError(s),
  })

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

  const focusBySatelliteHandler = useFocusBySatelliteId({
    liveEntityIdBySatelliteId,
    satelliteLookupByEntityId,
    handlePickEntityId,
    globeRef,
    fetchSatellitePositionById,
    setVisibilityError,
  })

  const handleFocusFromVisibility = (pass: UpcomingPass) => {
    focusBySatelliteHandler(pass)
  }

  const handleFocusBySatelliteId = useCallback((satelliteId: number) => {
    focusBySatelliteHandler(satelliteId)
  }, [focusBySatelliteHandler])

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

    const pauseBetweenBatches = () =>
      new Promise<void>((resolve) => {
        window.setTimeout(resolve, 0)
      })

    const loadGroups = async (_requestId: number, signal?: AbortSignal) => {
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
          startTransition(() => {
            setAuthUser(null)
            setAuthError('Sessione scaduta. Esegui di nuovo l\'accesso.')
            setAuthInfo('Sessione non valida per le API live.')
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
  }, [activeGroups, authUser, compactMobileViewport, refreshIntervalMs])

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
        authPasswordConfirm={authPasswordConfirm}
        onSwitchMode={(mode) => {
          setAuthMode(mode)
          setAuthError('')
          setAuthPasswordConfirm('')
        }}
        onAuthUsernameOrEmailChange={setAuthUsernameOrEmail}
        onAuthUsernameChange={setAuthUsername}
        onAuthEmailChange={setAuthEmail}
        onAuthPasswordChange={setAuthPassword}
        onAuthPasswordConfirmChange={setAuthPasswordConfirm}
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
                onPingSystemHealth={handlePingSystemHealth}
              />
            </section>

            <section className="panel-component panel-component-main">
              <div className="panel-layout">
                <section className="panel-left">
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
                              searchScopeOptions={searchScopeOptions}
                              groupRows={groupRows}
                              onToggleAll={handleToggleAllGroups}
                              onPresetChange={handlePresetChange}
                              onSearchScopeChange={handleSearchScopeChange}
                              onSearchQueryChange={handleSearchQueryChange}
                              onSearchResultSelect={handleSearchResultSelectCallback}
                              onToggleGroup={handleToggleGroupCallback}
                            />
                          </div>
                        ) : null}

                        {openPane === 'satellites' ? (
                          <div id="panel-satellites">
                            <SatellitesPanel
                              autoRotate={autoRotate}
                              showBackSideSatellites={showBackSideSatellites}
                              showMoon={showMoon}
                              onToggleShowMoon={handleToggleShowMoon}
                              hasLoadedOnce={hasLoadedOnce}
                              isRefreshing={isRefreshing}
                              refreshIntervalMs={refreshIntervalMs}
                              onZoomIn={handleZoomIn}
                              onZoomOut={handleZoomOut}
                              onGoHome={handleGoHome}
                              onAlignAxis={handleAlignAxis}
                              onToggleAutoRotate={handleToggleAutoRotate}
                              onToggleBackSideSatellites={handleToggleBackSideSatellites}
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
                              compactLandscapeViewport={landscapeMobileViewport}
                              onFocusSightingSatellite={handleFocusBySatelliteId}
                            />
                          </div>
                        ) : null}

                        {openPane === 'community' ? (
                          <div id="panel-community">
                            <CommunityPanel
                              authUser={authUser}
                              selectedSatelliteId={selectedSatellite?.satellite.satelliteId ?? null}
                              selectedSatelliteName={selectedSatellite?.satellite.satelliteName ?? null}
                              onFocusSatellite={handleFocusBySatelliteId}
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
          <MusicFloatingPlayer
            floatingStyle={
              landscapeMobileViewport
                ? { right: 8, left: 'auto', top: 8 }
                : { left: focusGlobeMode ? 10 : panelWidth + 20, top: desktopMusicTop }
            }
          />

          <button
            type="button"
            className="focus-toggle"
            style={
              landscapeMobileViewport
                ? { right: 8, left: 'auto', top: 58 }
                : { left: focusGlobeMode ? 10 : panelWidth + 20, right: 'auto', top: desktopFocusTop }
            }
            onClick={() => setFocusGlobeMode((prev) => !prev)}
          >
            {focusGlobeMode ? 'Mostra pannello dati' : 'Focus Globe'}
          </button>

          <div
            className="quick-zoom quick-zoom-left"
            style={{
              ...(landscapeMobileViewport
                ? { right: 8, left: 'auto', top: 108 }
                : { left: focusGlobeMode ? 10 : panelWidth + 20, top: computeQuickZoomTop(musicWidgetHeight, desktopZoomTop - musicWidgetHeight) ?? desktopZoomTop }),
            }}
          >
            <button type="button" onClick={() => globeRef.current?.zoomIn()} aria-label="Zoom rapido in">
              +
            </button>
            <button type="button" onClick={() => globeRef.current?.zoomOut()} aria-label="Zoom rapido out">
              -
            </button>
          </div>
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

          {selectedEntityId === 'moon-entity' && moonDetailsOpen ? (
            <aside className="viewer-hud">
              <section className="details-card hud-details">
                <h3>Dettagli Luna</h3>
                <div className="details-head">
                  <strong>Moon</strong>
                  <div className="details-head-actions">
                    <button type="button" onClick={() => {
                      setMoonDetailsOpen(false)
                    }}>Chiudi</button>
                  </div>
                </div>
                <div className="details-grid">
                  <span>Nome</span>
                  <span>Moon</span>
                  <span>Fase</span>
                  <span>{(computeMoonPosition().phase * 100).toFixed(1)}%</span>
                  <span>Lat/Lon</span>
                  <span>{computeMoonPosition().lat.toFixed(2)} / {computeMoonPosition().lon.toFixed(2)}</span>
                  <span>Distanza</span>
                  <span>{(computeMoonPosition().altMeters / 1000).toFixed(0)} km</span>
                </div>
              </section>
            </aside>
          ) : null}

          <SatelliteGlobe
            ref={globeRef}
            autoRotate={autoRotate}
            showBackSideSatellites={compactMobileViewport ? false : showBackSideSatellites}
            showMoon={showMoon}
            performanceMode={compactMobileViewport}
            groupColorMap={groupColorMap}
            selectedEntityId={selectedEntityId}
            starlinkSatellites={starlinkSatellites}
            visibleEntitySatellites={visibleEntitySatellites}
            onPickEntityId={handlePickEntityId}
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
