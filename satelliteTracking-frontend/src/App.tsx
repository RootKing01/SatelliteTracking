import { useCallback, useEffect, useMemo, useRef, useState } from 'react'
import { isAxiosError } from 'axios'
import { Color, Ion } from 'cesium'
import { getCurrentUser, login, logout, register, type AuthUser } from './api/authClient'
import { fetchSatelliteCatalogByType, type SatelliteCatalogItem } from './api/satelliteCatalogClient'
import { fetchSatellitePositionById } from './api/satellitePositionsClient'
import { fetchMySightings, reportSighting, type SatelliteSighting } from './api/sightingsClient'
import { fetchVisibleUpcomingPasses, type UpcomingPass } from './api/satelliteVisibilityClient'
import { satelliteGroupSources } from './api/groups'
import type { SatelliteGroupKey, SatelliteGroupSource } from './api/groups/types'
import { SatelliteGlobe, type CompassState, type SatelliteGlobeHandle, type VisibleSatelliteItem } from './components/SatelliteGlobe'
import type { SatellitePosition } from './types/satellite'
import './App.css'

const ionToken =
  import.meta.env.VITE_CESIUM_TOKEN ?? import.meta.env.VITE_CESIUM_ION_TOKEN

if (ionToken && !import.meta.env.DEV) {
  Ion.defaultAccessToken = ionToken
}

type GroupPositionsState = Partial<Record<SatelliteGroupKey, SatellitePosition[]>>
type GroupLoadingState = Partial<Record<SatelliteGroupKey, boolean>>
type GroupErrorState = Partial<Record<SatelliteGroupKey, string>>

type SelectedSatelliteState = {
  groupLabel: string
  groupKey: SatelliteGroupKey
  satellite: SatellitePosition
}

type SatelliteSearchScope = 'enabled' | 'all' | SatelliteGroupKey

type SearchResultItem = {
  entityId: string
  groupKey: SatelliteGroupKey
  groupLabel: string
  satelliteId: number
  satelliteName: string
  objectId: string
  noradCatId: number
  hasLivePosition: boolean
}

type SidebarPane = 'groups' | 'satellites' | 'visibility' | 'sightings'

const defaultEnabledGroups = Object.fromEntries(
  satelliteGroupSources.map((group) => [group.key, group.key === 'stations']),
) as Record<SatelliteGroupKey, boolean>

const defaultRefreshIntervalSec = 0.8
const mediumRefreshIntervalSec = 1.4
const heavyRefreshIntervalSec = 2.1
const veryHeavyRefreshIntervalSec = 3
const refreshTuningProfiles = [
  { label: 'Aggressivo', multiplier: 0.72 },
  { label: 'Bilanciato', multiplier: 1 },
  { label: 'Stabile', multiplier: 1.28 },
] as const

type GroupPreset = 'custom' | 'all' | 'stations' | 'navigation' | 'leo'
type AuthMode = 'login' | 'register'

function extractAuthErrorMessage(error: unknown, fallbackMessage: string) {
  if (isAxiosError(error)) {
    const responseData = error.response?.data as { message?: string } | undefined
    if (responseData?.message) {
      return responseData.message
    }
  }
  return fallbackMessage
}

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
  const [openPane, setOpenPane] = useState<SidebarPane | null>('groups')
  const [searchScope, setSearchScope] = useState<SatelliteSearchScope>('enabled')
  const [searchQuery, setSearchQuery] = useState('')
  const [catalogByGroup, setCatalogByGroup] =
    useState<Partial<Record<SatelliteGroupKey, SatelliteCatalogItem[]>>>({})
  const [focusGlobeMode, setFocusGlobeMode] = useState(false)
  const [selectedSatellite, setSelectedSatellite] = useState<SelectedSatelliteState | null>(null)
  const [selectedEntityId, setSelectedEntityId] = useState<string | null>(null)
  const [autoRotate, setAutoRotate] = useState(true)
  const [showBackSideSatellites, setShowBackSideSatellites] = useState(false)
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
  const [visibilityResults, setVisibilityResults] = useState<UpcomingPass[]>([])
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

  const satelliteLookupByEntityId = useMemo(() => {
    const map = new Map<string, SelectedSatelliteState>()

    for (const group of allGroups) {
      for (const satellite of groupPositions[group.key] ?? []) {
        map.set(`${group.key}-${satellite.satelliteId}`, {
          groupLabel: group.label,
          groupKey: group.key,
          satellite,
        })
      }
    }

    return map
  }, [allGroups, groupPositions])

  const liveEntityIdBySatelliteId = useMemo(() => {
    const map = new Map<number, string>()
    for (const group of allGroups) {
      for (const satellite of groupPositions[group.key] ?? []) {
        map.set(satellite.satelliteId, `${group.key}-${satellite.satelliteId}`)
      }
    }
    return map
  }, [allGroups, groupPositions])

  useEffect(() => {
    latestGroupPositionsRef.current = groupPositions
  }, [groupPositions])

  const searchResultItems = useMemo(() => {
    const groupsToSearch =
      searchScope === 'enabled'
        ? allGroups.filter((group) => enabledGroups[group.key])
        : searchScope === 'all'
          ? allGroups
          : allGroups.filter((group) => group.key === searchScope)

    const liveItems = groupsToSearch.flatMap((group) =>
      (groupPositions[group.key] ?? []).map((satellite) => ({
        entityId: `${group.key}-${satellite.satelliteId}`,
        groupKey: group.key,
        groupLabel: group.label,
        satelliteId: satellite.satelliteId,
        satelliteName: satellite.satelliteName,
        objectId: satellite.objectId,
        noradCatId: satellite.noradCatId,
        hasLivePosition: true,
      })),
    )

    const mergedByEntityId = new Map<string, SearchResultItem>()
    for (const item of liveItems) {
      mergedByEntityId.set(item.entityId, item)
    }

    if (searchScope !== 'enabled') {
      for (const group of groupsToSearch) {
        for (const satellite of catalogByGroup[group.key] ?? []) {
          const entityId = `${group.key}-${satellite.id}`
          if (!mergedByEntityId.has(entityId)) {
            mergedByEntityId.set(entityId, {
              entityId,
              groupKey: group.key,
              groupLabel: group.label,
              satelliteId: satellite.id,
              satelliteName: satellite.objectName,
              objectId: satellite.objectId,
              noradCatId: satellite.noradCatId,
              hasLivePosition: false,
            })
          }
        }
      }
    }

    const baseItems = Array.from(mergedByEntityId.values())

    const normalizedQuery = searchQuery.trim().toLowerCase()
    const filteredItems = normalizedQuery
      ? baseItems.filter((item) => {
          const searchableText = `${item.satelliteName} ${item.noradCatId} ${item.objectId}`
          return searchableText.toLowerCase().includes(normalizedQuery)
        })
      : baseItems

    return filteredItems
      .sort((a, b) => a.satelliteName.localeCompare(b.satelliteName))
      .slice(0, 90)
  }, [allGroups, catalogByGroup, enabledGroups, groupPositions, searchQuery, searchScope])

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

  const submitLogin = async () => {
    if (authSubmitting) {
      return
    }

    setAuthSubmitting(true)
    setAuthError('')

    try {
      const response = await login({
        usernameOrEmail: authUsernameOrEmail,
        password: authPassword,
      })
      if (!response.authenticated || !response.user) {
        setAuthError(response.message || 'Accesso non riuscito')
        return
      }

      // Verifica immediata della sessione per evitare stato UI "loggato" senza cookie valido.
      const me = await getCurrentUser()
      if (!me.authenticated || !me.user) {
        setAuthUser(null)
        setAuthError('Accesso effettuato ma sessione non valida. Riprova il login.')
        return
      }

      setAuthUser(me.user)
      setAuthInfo(`Benvenuto ${me.user.username}`)
      resetAuthFields()
    } catch (error) {
      setAuthError(extractAuthErrorMessage(error, 'Errore durante il login'))
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
      const response = await register({
        username: authUsername,
        email: authEmail,
        password: authPassword,
      })
      if (!response.authenticated || !response.user) {
        setAuthError(response.message || 'Registrazione non riuscita')
        return
      }

      const me = await getCurrentUser()
      if (!me.authenticated || !me.user) {
        setAuthUser(null)
        setAuthError('Registrazione completata ma sessione non valida. Esegui l\'accesso.')
        return
      }

      setAuthUser(me.user)
      setAuthInfo(`Registrazione completata: ${me.user.username}`)
      resetAuthFields()
    } catch (error) {
      setAuthError(extractAuthErrorMessage(error, 'Errore durante la registrazione'))
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
      await logout()
      setAuthUser(null)
      setMySightings([])
      setAuthInfo('Sessione chiusa, esegui un nuovo accesso.')
    } catch (error) {
      setAuthError(extractAuthErrorMessage(error, 'Errore durante il logout'))
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
    if (!navigator.geolocation || locatingBrowser) {
      setSightingsError('Geolocalizzazione non disponibile nel browser.')
      return
    }

    setLocatingBrowser(true)
    setSightingsError('')

    navigator.geolocation.getCurrentPosition(
      (position) => {
        setSightingLatitude(position.coords.latitude)
        setSightingLongitude(position.coords.longitude)
        setSightingAltitude(position.coords.altitude ?? 30)
        setSightingInfo('Posizione browser acquisita.')
        setLocatingBrowser(false)
      },
      () => {
        setSightingsError('Impossibile acquisire la posizione browser.')
        setLocatingBrowser(false)
      },
      {
        enableHighAccuracy: true,
        maximumAge: 120000,
        timeout: 10000,
      },
    )
  }

  const handleUseBrowserLocationForVisibility = () => {
    if (!navigator.geolocation || visibilityLocatingBrowser) {
      setVisibilityError('Geolocalizzazione non disponibile nel browser.')
      return
    }

    setVisibilityLocatingBrowser(true)
    setVisibilityError('')

    navigator.geolocation.getCurrentPosition(
      (position) => {
        setVisibilityLatitude(position.coords.latitude)
        setVisibilityLongitude(position.coords.longitude)
        setVisibilityAltitude(position.coords.altitude ?? 30)
        setVisibilityInfo('Posizione browser attiva per il calcolo visibilita.')
        setVisibilityLocatingBrowser(false)
      },
      () => {
        setVisibilityError('Impossibile acquisire la posizione browser.')
        setVisibilityLocatingBrowser(false)
      },
      {
        enableHighAccuracy: true,
        maximumAge: 120000,
        timeout: 10000,
      },
    )
  }

  const handleCalculateVisibility = async () => {
    if (visibilityLoading) {
      return
    }

    setVisibilityLoading(true)
    setVisibilityError('')
    setVisibilityInfo('')

    try {
      const results = await fetchVisibleUpcomingPasses({
        hours: visibilityHours,
        minElevation: visibilityMinElevation,
        observingCondition: 'any',
        maxMagnitude: 8.0,
        latitude: visibilityLatitude ?? undefined,
        longitude: visibilityLongitude ?? undefined,
        altitude: visibilityAltitude ?? undefined,
      })
      setVisibilityResults(results.slice(0, 30))
      setVisibilityInfo(
        results.length === 0
          ? 'Nessun passaggio visibile nei parametri selezionati.'
          : `Trovati ${results.length} passaggi visibili.`,
      )
    } catch (error) {
      if (isAxiosError(error) && error.response?.status === 401) {
        setAuthUser(null)
        setAuthInfo('Sessione scaduta. Esegui di nuovo l\'accesso.')
        setAuthError('Sessione non valida per il calcolo visibilita.')
        return
      }

      setVisibilityError(extractAuthErrorMessage(error, 'Errore durante il calcolo della visibilita'))
    } finally {
      setVisibilityLoading(false)
    }
  }

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

      const results = await Promise.allSettled(
        activeGroups.map(async (group) => {
          const positions = await group.loadPositions(signal)
          return { key: group.key, positions }
        }),
      )

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
    if (preset === 'custom') {
      return
    }

    const navigationKeys = new Set(['gpsOps', 'galileo', 'glonassOps', 'beidou', 'sbas'])
    const leoKeys = new Set(['starlink', 'oneweb', 'iridiumNext', 'planet', 'spire', 'cubesat'])

    const nextEnabled = Object.fromEntries(
      allGroups.map((group) => {
        if (preset === 'all') {
          return [group.key, true]
        }
        if (preset === 'stations') {
          return [group.key, group.key === 'stations']
        }
        if (preset === 'navigation') {
          return [group.key, navigationKeys.has(group.key)]
        }
        return [group.key, leoKeys.has(group.key)]
      }),
    ) as Record<SatelliteGroupKey, boolean>

    setEnabledGroups(nextEnabled)
  }

  if (authChecking) {
    return (
      <main className="auth-shell">
        <section className="auth-card">
          <h1>Satellite Tracker</h1>
          <p>Verifica sessione in corso...</p>
        </section>
      </main>
    )
  }

  if (!authUser) {
    return (
      <main className="auth-shell">
        <section className="auth-card">
          <h1>Satellite Tracker</h1>
          <p className="auth-info">{authInfo}</p>

          <div className="auth-tabs">
            <button
              type="button"
              className={authMode === 'login' ? 'is-active' : ''}
              onClick={() => {
                setAuthMode('login')
                setAuthError('')
              }}
            >
              Accesso
            </button>
            <button
              type="button"
              className={authMode === 'register' ? 'is-active' : ''}
              onClick={() => {
                setAuthMode('register')
                setAuthError('')
              }}
            >
              Iscrizione
            </button>
          </div>

          <form
            className="auth-form"
            onSubmit={(event) => {
              event.preventDefault()
              if (authMode === 'login') {
                void submitLogin()
                return
              }
              void submitRegister()
            }}
          >
            {authMode === 'login' ? (
              <label>
                Username o email
                <input
                  value={authUsernameOrEmail}
                  onChange={(event) => setAuthUsernameOrEmail(event.target.value)}
                  autoComplete="username"
                  required
                />
              </label>
            ) : (
              <>
                <label>
                  Username
                  <input
                    value={authUsername}
                    onChange={(event) => setAuthUsername(event.target.value)}
                    autoComplete="username"
                    required
                  />
                </label>
                <label>
                  Email
                  <input
                    type="email"
                    value={authEmail}
                    onChange={(event) => setAuthEmail(event.target.value)}
                    autoComplete="email"
                    required
                  />
                </label>
              </>
            )}

            <label>
              Password
              <input
                type="password"
                value={authPassword}
                onChange={(event) => setAuthPassword(event.target.value)}
                autoComplete={authMode === 'login' ? 'current-password' : 'new-password'}
                required
              />
            </label>

            {authError ? <p className="auth-error">{authError}</p> : null}

            <button type="submit" className="auth-submit" disabled={authSubmitting}>
              {authSubmitting
                ? 'Invio in corso...'
                : authMode === 'login'
                  ? 'Accedi'
                  : 'Crea account'}
            </button>
          </form>

          <div className="auth-hint">
            <p>
              Profilo base: <strong>demo</strong> / <strong>Demo123!</strong>
            </p>
          </div>
        </section>
      </main>
    )
  }

  if (!ionToken) {
    return (
      <main className="app-shell">
        <aside className="panel-section">
          <div className="panel">
            <h1>Cesium token mancante</h1>
            <p>
              Aggiungi <strong>VITE_CESIUM_TOKEN</strong> nel file .env in root e riavvia il
              frontend.
            </p>
          </div>
        </aside>
        <section className="viewer-section" />
      </main>
    )
  }

  return (
    <main className={`app-shell ${focusGlobeMode ? 'focus-mode' : ''}`}>
      {!focusGlobeMode ? (
        <aside className="panel-section">
          <header className="panel">
          <div className="panel-header">
            <span className="panel-badge">
              <span className="live-dot" />
              Live globe
            </span>
            <h1>Satellite Tracker</h1>
            <button
              type="button"
              className="panel-logout"
              onClick={() => {
                void handleLogout()
              }}
            >
              Logout ({authUser.username})
            </button>
          </div>

          <div className="panel-layout">
            <section className="panel-left">
              <p className="updated-at">Live feed /api/satellites/positions</p>
              <p className="updated-at">Visibili: {totalVisibleCount} satelliti</p>

              <div className="sidebar-split">
                <nav className="sidebar-tabs" aria-label="Pannelli laterali">
                  <button
                    type="button"
                    className={openPane === 'groups' ? 'tab-active' : ''}
                    onClick={() => setOpenPane((prev) => (prev === 'groups' ? null : 'groups'))}
                  >
                    <span className="tab-icon tab-icon-constellation" aria-hidden="true" />
                    <span>Costellazioni</span>
                  </button>
                  <button
                    type="button"
                    className={openPane === 'satellites' ? 'tab-active' : ''}
                    onClick={() => setOpenPane((prev) => (prev === 'satellites' ? null : 'satellites'))}
                  >
                    <span className="tab-icon tab-icon-view" aria-hidden="true" />
                    <span>Gestione vista</span>
                  </button>
                  <button
                    type="button"
                    className={openPane === 'visibility' ? 'tab-active' : ''}
                    onClick={() => setOpenPane((prev) => (prev === 'visibility' ? null : 'visibility'))}
                  >
                    <span className="tab-icon tab-icon-visibility" aria-hidden="true" />
                    <span>Visibilita</span>
                  </button>
                  <button
                    type="button"
                    className={openPane === 'sightings' ? 'tab-active' : ''}
                    onClick={() => setOpenPane((prev) => (prev === 'sightings' ? null : 'sightings'))}
                  >
                    <span className="tab-icon tab-icon-sighting" aria-hidden="true" />
                    <span>Avvistamenti</span>
                  </button>
                </nav>

                {openPane === 'groups' ? (
                  <section className="collapsible side-drawer" aria-label="Gruppi satelliti">
                    <h3>Gruppi satelliti</h3>
                    <label className="select-all">
                      <input type="checkbox" checked={allSelected} onChange={toggleAllGroups} />
                      <span>Seleziona tutti i gruppi</span>
                    </label>
                    <div className="group-preset-row">
                      <label htmlFor="group-preset">Preset gruppi</label>
                      <select
                        id="group-preset"
                        value={selectedPreset}
                        onChange={(event) => {
                          const preset = event.target.value as GroupPreset
                          setSelectedPreset(preset)
                          applyGroupPreset(preset)
                        }}
                      >
                        <option value="custom">Personalizzato</option>
                        <option value="stations">Solo stazioni</option>
                        <option value="navigation">Navigazione GNSS</option>
                        <option value="leo">LEO tracking</option>
                        <option value="all">Tutti i gruppi</option>
                      </select>
                    </div>
                    <div className="search-panel">
                      <div className="group-preset-row">
                        <label htmlFor="satellite-search-scope">Ambito ricerca</label>
                        <select
                          id="satellite-search-scope"
                          value={searchScope}
                          onChange={(event) =>
                            setSearchScope(event.target.value as SatelliteSearchScope)
                          }
                        >
                          <option value="enabled">Gruppi attivi</option>
                          <option value="all">Tutti i gruppi</option>
                          {allGroups.map((group) => (
                            <option key={group.key} value={group.key}>
                              {group.label}
                            </option>
                          ))}
                        </select>
                      </div>
                      <label htmlFor="satellite-search-input" className="search-label">
                        Cerca satellite
                      </label>
                      <input
                        id="satellite-search-input"
                        className="search-input"
                        type="text"
                        value={searchQuery}
                        onChange={(event) => setSearchQuery(event.target.value)}
                        placeholder="Nome, NORAD o object id"
                      />
                      <div className="search-results-meta">
                        {searchResultItems.length} risultati
                      </div>
                      <div className="search-results-list">
                        {searchResultItems.map((item) => (
                          <button
                            key={item.entityId}
                            type="button"
                            className="search-result-item"
                            onClick={() => handleSearchResultSelect(item)}
                          >
                            <span>{item.satelliteName}</span>
                            <small>
                              {item.groupLabel} | NORAD {item.noradCatId}
                              {!item.hasLivePosition ? ' | in attesa posizione live' : ''}
                            </small>
                          </button>
                        ))}
                      </div>
                    </div>
                    <div className="group-list">
                      {allGroups.map((group) => {
                        const count = groupPositions[group.key]?.length ?? 0
                        const loading = groupLoading[group.key]
                        const error = groupErrors[group.key]

                        return (
                          <label
                            key={group.key}
                            className={`group-item ${loading ? 'is-loading' : ''}`}
                            aria-busy={loading ? 'true' : 'false'}
                          >
                            <input
                              type="checkbox"
                              checked={enabledGroups[group.key]}
                              onChange={() => toggleGroup(group.key)}
                            />
                            <span
                              className="group-color"
                              style={{
                                backgroundColor: group.color,
                                boxShadow: `0 0 8px ${group.color}, 0 0 0 1px rgba(4, 10, 24, 0.85)`,
                              }}
                              aria-hidden="true"
                            />
                            <span className="group-name">{group.label}</span>
                            <span className="group-meta">{`${count} sat`}</span>
                            {error ? <span className="group-error">!</span> : null}
                          </label>
                        )
                      })}
                    </div>
                  </section>
                ) : null}

                {openPane === 'satellites' ? (
                  <section className="collapsible side-drawer" aria-label="Comandi satelliti">
                    <h3>Satelliti</h3>
                    <div className="toolbar toolbar-left compact-toolbar">
                      <button type="button" onClick={() => globeRef.current?.zoomIn()}>Zoom +</button>
                      <button type="button" onClick={() => globeRef.current?.zoomOut()}>Zoom -</button>
                      <button type="button" onClick={() => globeRef.current?.goToInitialView()}>Home</button>
                      <button type="button" onClick={() => globeRef.current?.alignToEarthAxis()}>Asse N-S</button>
                      <button
                        type="button"
                        className={autoRotate ? 'toggle-active' : ''}
                        onClick={() => setAutoRotate((prev) => !prev)}
                      >
                        {autoRotate ? 'Stop rotazione' : 'Avvia rotazione'}
                      </button>
                      <button
                        type="button"
                        className={showBackSideSatellites ? 'toggle-active' : ''}
                        onClick={() => setShowBackSideSatellites((prev) => !prev)}
                      >
                        {showBackSideSatellites ? 'Nascondi lato opposto' : 'Mostra lato opposto'}
                      </button>
                    </div>

                    <section className="sync-footer-card" aria-label="Stato sincronizzazione e camera">
                      <p className="sync-status">
                        <span className={`sync-dot ${hasLoadedOnce ? 'ok blink' : ''} ${isRefreshing ? 'active' : ''}`} />
                        Sincronizzazione live attiva
                      </p>
                      <p className="sync-status">
                        <strong>Visibilita:</strong>{' '}
                        {showBackSideSatellites ? 'anche lato opposto' : 'solo lato visibile'}
                      </p>
                      <p className="sync-status">
                        <strong>Refresh:</strong> ogni {(refreshIntervalMs / 1000).toFixed(1)}s
                      </p>
                      <div className="refresh-slider-block" aria-label="Profilo refresh live">
                        <div className="refresh-slider-head">
                          <span>Profilo refresh</span>
                          <strong>{selectedRefreshTuning.label}</strong>
                        </div>
                        <input
                          type="range"
                          min={0}
                          max={2}
                          step={1}
                          value={refreshTuningIndex}
                          onChange={(event) => {
                            const parsed = Number.parseInt(event.target.value, 10)
                            if (!Number.isFinite(parsed)) {
                              return
                            }
                            setRefreshTuningIndex(Math.max(0, Math.min(2, parsed)))
                          }}
                        />
                        <div className="refresh-slider-scale" aria-hidden="true">
                          <span>Aggressivo</span>
                          <span>Bilanciato</span>
                          <span>Stabile</span>
                        </div>
                      </div>
                      <div className="compass-row"><span>Heading</span><strong>{compass.headingDeg.toFixed(1)}deg</strong></div>
                      <div className="compass-row"><span>Pitch</span><strong>{compass.pitchDeg.toFixed(1)}deg</strong></div>
                      <div className="compass-row"><span>Quota camera</span><strong>{compass.altitudeKm.toFixed(0)} km</strong></div>
                    </section>
                  </section>
                ) : null}

                {openPane === 'visibility' ? (
                  <section className="collapsible side-drawer" aria-label="Calcolo visibilita satelliti">
                    <h3>Visibilita prossime ore</h3>
                    <div className="visibility-grid">
                      <label>
                        Ore
                        <input
                          type="number"
                          min={1}
                          max={24}
                          value={visibilityHours}
                          onChange={(event) =>
                            setVisibilityHours(
                              Math.max(1, Math.min(24, Number(event.target.value) || 12)),
                            )
                          }
                        />
                      </label>
                      <label>
                        Elev. min (deg)
                        <input
                          type="number"
                          min={0}
                          max={90}
                          value={visibilityMinElevation}
                          onChange={(event) =>
                            setVisibilityMinElevation(
                              Math.max(0, Math.min(90, Number(event.target.value) || 10)),
                            )
                          }
                        />
                      </label>
                    </div>

                    <div className="visibility-actions">
                      <button
                        type="button"
                        className="sighting-pin-button"
                        onClick={handleUseBrowserLocationForVisibility}
                        disabled={visibilityLocatingBrowser}
                        title="Usa posizione browser"
                        aria-label="Usa posizione browser"
                      >
                        {visibilityLocatingBrowser ? (
                          '...'
                        ) : (
                          <svg viewBox="0 0 24 24" aria-hidden="true" focusable="false">
                            <path d="M12 2C8.14 2 5 5.14 5 9c0 5.08 6.13 12.31 6.39 12.62a.8.8 0 0 0 1.22 0C12.87 21.31 19 14.08 19 9c0-3.86-3.14-7-7-7Zm0 9.5A2.5 2.5 0 1 1 12 6.5a2.5 2.5 0 0 1 0 5Z" />
                          </svg>
                        )}
                      </button>
                      <button type="button" onClick={() => { void handleCalculateVisibility() }}>
                        {visibilityLoading ? 'Calcolo...' : 'Calcola visibilita'}
                      </button>
                    </div>

                    {visibilityLatitude !== null && visibilityLongitude !== null ? (
                      <small className="visibility-note">
                        Posizione browser: {visibilityLatitude.toFixed(4)}, {visibilityLongitude.toFixed(4)}
                      </small>
                    ) : (
                      <small className="visibility-note">
                        Posizione default backend (San Marcellino) se non usi il pin.
                      </small>
                    )}

                    {visibilityInfo ? <p className="sighting-info">{visibilityInfo}</p> : null}
                    {visibilityError ? <p className="sighting-error">{visibilityError}</p> : null}

                    {visibilityResults.length > 0 ? (
                      <div className="visibility-list">
                        {visibilityResults.map((pass) => (
                          <article
                            key={`${pass.satelliteId}-${pass.riseTime}-${pass.setTime}`}
                            className="visibility-item"
                          >
                            <strong>{pass.satelliteName}</strong>
                            <small>{new Date(pass.riseTime).toLocaleString('it-IT')} {'->'} {new Date(pass.setTime).toLocaleTimeString('it-IT')}</small>
                            <small>
                              Elev. max {pass.maxElevation.toFixed(1)}deg | Mag {pass.estimatedMagnitude.toFixed(1)}
                            </small>
                            <small>
                              Condizione: {pass.observingCondition} | Visibilita: {pass.visibility}
                            </small>
                            <button
                              type="button"
                              onClick={() => {
                                handleFocusFromVisibility(pass)
                              }}
                            >
                              Focus satellite
                            </button>
                          </article>
                        ))}
                      </div>
                    ) : null}
                  </section>
                ) : null}

                {openPane === 'sightings' ? (
                  <section className="collapsible side-drawer" aria-label="Avvistamenti utente">
                    <h3>Avvistamenti</h3>
                    <p className="updated-at">Storico personale validato dal backend.</p>

                    {sightingInfo ? <p className="sighting-info">{sightingInfo}</p> : null}
                    {sightingsError ? <p className="sighting-error">{sightingsError}</p> : null}

                    {sightingsLoading ? (
                      <p className="updated-at">Caricamento avvistamenti...</p>
                    ) : mySightings.length === 0 ? (
                      <p className="updated-at">Nessun avvistamento registrato.</p>
                    ) : (
                      <div className="sighting-list">
                        {mySightings.map((item) => (
                          <article key={item.id} className="sighting-item">
                            <strong>{item.satelliteName}</strong>
                            <small>NORAD {item.noradCatId}</small>
                            <small>{new Date(item.sightedAt).toLocaleString('it-IT')}</small>
                            <small>{item.observerLocationName}</small>
                            <small className={item.valid ? 'sighting-valid' : 'sighting-invalid'}>
                              {item.valid ? 'Valido' : 'Non valido'}
                            </small>
                            {item.estimatedMagnitude !== null ? (
                              <small>Magnitudine stimata: {item.estimatedMagnitude.toFixed(1)}</small>
                            ) : null}
                            {item.maxElevationDeg !== null ? (
                              <small>Elevazione max: {item.maxElevationDeg.toFixed(1)}deg</small>
                            ) : null}
                            <small>{item.validationMessage}</small>
                          </article>
                        ))}
                      </div>
                    )}
                  </section>
                ) : null}
              </div>
            </section>
          </div>
          </header>
        </aside>
      ) : null}

      <section className="viewer-section">
        <button
          type="button"
          className="focus-toggle"
          onClick={() => setFocusGlobeMode((prev) => !prev)}
        >
          {focusGlobeMode ? 'Mostra pannello dati' : 'Focus Globe'}
        </button>
        <aside className="viewer-hud">
          {selectedSatellite ? (
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
          ) : null}
        </aside>

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
    </main>
  )
}

export default App
