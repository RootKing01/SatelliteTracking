import {
  useSearchResultSelect,
  useReportSighting,
  useFocusBySatelliteId,
} from './hooks/useInteractionHandlers'
import { defaultEnabledGroups, useSatelliteTracking } from './hooks/useSatelliteTracking'
import { useVisibility } from './hooks/useVisibility'
import { useSightings } from './hooks/useSightings'
import { useAuthFlow } from './hooks/useAuthFlow'
import { useCommunityNotifications } from './hooks/useCommunityNotifications'
import {
  markCommunityNotificationAsRead,
  type CommunityNotification,
} from './api/communityClient'
import { lazy, startTransition, Suspense, useCallback, useEffect, useMemo, useRef, useState } from 'react'
import {
  loadOrekitStatus,
  loadSystemHealth,
  type OrekitStatusResponse,
  type SystemHealthResponse,
} from './api/systemStatusClient'
import {
  fetchSatelliteCatalogByType,
  fetchSatellitePositionById,
  type SatelliteCatalogItem,
} from './api/satelliteClient'
import type { SatelliteGroupKey } from './api/groups/types'
import { handleUseBrowserLocationImpl, handleUseBrowserLocationForVisibilityImpl } from './helpers/locationHelpers'
import { buildEnabledGroupsFromPreset, buildGroupRows, type GroupPreset } from './helpers/groupHelpers'
import {
  buildSearchResultItems,
  type SatelliteSearchScope,
  type SelectedSatelliteState,
} from './helpers/searchHelpers'
import { downloadVisibilityResultsCsv } from './helpers/visibilityHelpers'
import { AuthPanel } from './components/auth/AuthPanel'
import { CommunityNotificationTray } from './components/community/CommunityNotificationTray'
import { PanelSidebarButtons, type SidebarPane } from './components/layout/PanelSidebarButtons'
import { PanelTopSection } from './components/layout/PanelTopSection'
import { SidebarPaneContent } from './components/layout/SidebarPaneContent'
import { SatelliteDetailsHud } from './components/layout/ViewerHud'
import { VisibilityResultsTable } from './components/visibility/VisibilityResultsTable'
import type { SatelliteGlobeHandle } from './components/SatelliteGlobe'
import { CommunityPanel, GroupsPanel, SatellitesPanel, SightingsPanel, VisibilityPanel } from './components/panels'
import './App.css'
import './styles/orekit-badge.css'
import './styles/mobile-smartphone.css'

const ionToken = import.meta.env.VITE_CESIUM_TOKEN

const LazySatelliteGlobe = lazy(() =>
  import('./components/SatelliteGlobe').then(({ SatelliteGlobe }) => ({ default: SatelliteGlobe })),
)

if (ionToken && !import.meta.env.DEV) {
  void import('cesium').then(({ Ion }) => {
    Ion.defaultAccessToken = ionToken
  })
}

function App() {
  const globeRef = useRef<SatelliteGlobeHandle>(null)

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
  const [compactMobileViewport, setCompactMobileViewport] = useState(false)
  const [landscapeMobileViewport, setLandscapeMobileViewport] = useState(false)
  const [orekitStatus, setOrekitStatus] = useState<OrekitStatusResponse | null>(null)
  const [orekitStatusLoading, setOrekitStatusLoading] = useState(false)
  const [orekitStatusError, setOrekitStatusError] = useState('')
  const [systemHealth, setSystemHealth] = useState<SystemHealthResponse | null>(null)
  const [systemHealthLoading, setSystemHealthLoading] = useState(false)
  const [systemHealthError, setSystemHealthError] = useState('')
  const [reportingSighting, setReportingSighting] = useState(false)
  const [sightingCity, setSightingCity] = useState('')
  const [sightingLatitude, setSightingLatitude] = useState<number | null>(null)
  const [sightingLongitude, setSightingLongitude] = useState<number | null>(null)
  const [sightingAltitude, setSightingAltitude] = useState<number | null>(null)
  const [locatingBrowser, setLocatingBrowser] = useState(false)
  const [pendingCommunityNotification, setPendingCommunityNotification] =
    useState<CommunityNotification | null>(null)
  const visibilityOverlayCloseRef = useRef<HTMLButtonElement>(null)

  const clearSessionRef = useRef<() => void>(() => {})
  const clearPrivateSessionState = useCallback(() => {
    clearSessionRef.current()
    setSelectedSatellite(null)
    setSelectedEntityId(null)
    setCatalogByGroup({})
  }, [])

  const {
    authChecking,
    authSubmitting,
    authMode,
    authUser,
    authUsernameOrEmail,
    authUsername,
    authEmail,
    authPassword,
    authPasswordConfirm,
    authError,
    authInfo,
    setAuthUser,
    setAuthMode,
    setAuthUsernameOrEmail,
    setAuthUsername,
    setAuthEmail,
    setAuthPassword,
    setAuthPasswordConfirm,
    setAuthError,
    setAuthInfo,
    submitLogin,
    submitRegister,
    handleLogout,
  } = useAuthFlow({ onLogoutSuccess: clearPrivateSessionState })

  const {
    notificationsOpen,
    setNotificationsOpen,
    notifications,
    setNotifications,
    notificationCount,
    setNotificationCount,
  } = useCommunityNotifications({ authUser })

  const visibility = useVisibility({
    setAuthUser,
    setAuthInfo,
    setAuthError,
    clearSessionState: clearPrivateSessionState,
  })
  const sightings = useSightings({
    authUser,
    openPane,
    setAuthUser,
    setAuthInfo,
    setAuthError,
  })
  const {
    visibilityHours, setVisibilityHours, visibilityMinElevation, setVisibilityMinElevation,
    visibilityLoading, visibilityError, visibilityInfo, visibilityCity, setVisibilityCity,
    visibilityResults, visibilityAllResults, visibilityOverlayOpen, setVisibilityOverlayOpen,
    visibilityOverlayQuery, setVisibilityOverlayQuery, visibilityLatitude, setVisibilityLongitude,
    visibilityLongitude, visibilityLocatingBrowser, setVisibilityLocatingBrowser,
    setVisibilityError, setVisibilityInfo, setVisibilityLatitude, setVisibilityAltitude,
    handleCalculateVisibility, visibilityQueryLocationLabel, visibilityOverlayFilteredResults,
  } = visibility
  const {
    mySightings, setMySightings, sightingsLoading, sightingsError, setSightingsError,
    sightingInfo, setSightingInfo,
  } = sightings
  useEffect(() => {
    clearSessionRef.current = () => {
      sightings.setMySightings([])
      visibility.setVisibilityResults([])
      visibility.setVisibilityAllResults([])
      visibility.setVisibilityOverlayOpen(false)
      visibility.setVisibilityOverlayQuery('')
      sightings.setSightingsError('')
      sightings.setSightingInfo('')
      visibility.setVisibilityError('')
      visibility.setVisibilityInfo('')
    }
  }, [sightings, visibility])

  const handleLiveSessionExpired = useCallback(() => {
    clearPrivateSessionState()
    setAuthUser(null)
    setAuthError('Sessione scaduta. Esegui di nuovo l\'accesso.')
    setAuthInfo('Sessione non valida per le API live.')
  }, [clearPrivateSessionState, setAuthError, setAuthInfo, setAuthUser])

  const {
    allGroups,
    groupColorMap,
    effectiveEnabledGroups,
    groupPositions,
    groupLoading,
    groupErrors,
    isRefreshing,
    hasLoadedOnce,
    refreshIntervalMs,
    totalVisibleCount,
    visibleEntitySatellites,
    starlinkSatellites,
    satelliteLookupByEntityId,
    liveEntityIdBySatelliteId,
  } = useSatelliteTracking({
    authenticated: Boolean(authUser),
    enabledGroups,
    selectedPreset,
    compactMobileViewport,
    onSessionExpired: handleLiveSessionExpired,
  })

  const allSelected = useMemo(
    () => allGroups.every((group) => effectiveEnabledGroups[group.key]),
    [allGroups, effectiveEnabledGroups],
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

  const groupRows = useMemo(
    () =>
      openPane === 'groups'
        ? buildGroupRows(allGroups, enabledGroups, groupPositions, groupLoading, groupErrors)
        : [],
    [allGroups, enabledGroups, groupErrors, groupLoading, groupPositions, openPane],
  )

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

  const handleToggleNotifications = useCallback(() => {
    if (openPane !== 'community') {
      setOpenPane('community')
      setNotificationsOpen(true)
      return
    }

    setNotificationsOpen((previous) => !previous)
  }, [openPane, setNotificationsOpen])

  const handleSelectNotification = useCallback(async (notification: CommunityNotification) => {
    if (notification.readAt == null) {
      try {
        const updated = await markCommunityNotificationAsRead(notification.id)
        setNotifications((previous) => previous.map((item) => item.id === updated.id ? updated : item))
        setNotificationCount((previous) => Math.max(0, previous - 1))
      } catch (error) {
        console.error('Impossibile segnare la notifica come letta', error)
      }
    }

    setOpenPane('community')
    setPendingCommunityNotification(notification)
    setNotificationsOpen(false)
  }, [setNotificationCount, setNotifications, setNotificationsOpen])

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
  const handlePingSystemHealth = useCallback(() => {
    setSystemHealthLoading(true)
    setSystemHealthError('')
    void loadSystemHealth()
      .then(({ status, error }) => {
        setSystemHealth(status)
        setSystemHealthError(error)
      })
      .finally(() => {
        setSystemHealthLoading(false)
      })
  }, [])

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

  const loadAuthenticatedSystemStatus = useCallback(
    (orekitSignal: AbortSignal, healthSignal: AbortSignal) => {
      setOrekitStatusLoading(true)
      setSystemHealthLoading(true)

      void loadOrekitStatus(orekitSignal)
        .then(({ status, error }) => {
          setOrekitStatus(status)
          setOrekitStatusError(error)
        })
        .finally(() => {
          setOrekitStatusLoading(false)
        })

      void loadSystemHealth(healthSignal)
        .then(({ status, error }) => {
          setSystemHealth(status)
          setSystemHealthError(error)
        })
        .finally(() => {
          setSystemHealthLoading(false)
        })
    },
    [],
  )

  useEffect(() => {
    if (!authUser) {
      return
    }

    const orekitController = new AbortController()
    const healthController = new AbortController()
    queueMicrotask(() => {
      if (!orekitController.signal.aborted && !healthController.signal.aborted) {
        loadAuthenticatedSystemStatus(orekitController.signal, healthController.signal)
      }
    })

    return () => {
      orekitController.abort()
      healthController.abort()
    }
  }, [authUser, loadAuthenticatedSystemStatus])

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
    setMySightings,
    setOpenPane,
    setAuthUser,
    setAuthInfo,
    setAuthError,
    clearSessionState: clearPrivateSessionState,
  })

  const handleUseBrowserLocation = useCallback(() => {
    handleUseBrowserLocationImpl({
      locatingBrowser,
      setLocatingBrowser,
      setSightingsError,
      setSightingLatitude,
      setSightingLongitude,
      setSightingAltitude,
      setSightingInfo,
    })
  }, [locatingBrowser, setSightingAltitude, setSightingInfo, setSightingLatitude, setSightingLongitude, setSightingsError])

  const handleUseBrowserLocationForVisibility = useCallback(() => {
    handleUseBrowserLocationForVisibilityImpl({
      visibilityLocatingBrowser,
      setVisibilityLocatingBrowser,
      setVisibilityError,
      setVisibilityLatitude,
      setVisibilityLongitude,
      setVisibilityAltitude,
      setVisibilityInfo,
    })
  }, [setVisibilityAltitude, setVisibilityError, setVisibilityInfo, setVisibilityLatitude, setVisibilityLocatingBrowser, setVisibilityLongitude, visibilityLocatingBrowser])

  const openVisibilityFullResultsOverlay = useCallback(() => {
    if (visibilityAllResults.length === 0) {
      setVisibilityError('Nessun risultato completo disponibile da mostrare.')
      return
    }
    setVisibilityOverlayQuery('')
    setVisibilityOverlayOpen(true)
  }, [setVisibilityError, setVisibilityOverlayOpen, setVisibilityOverlayQuery, visibilityAllResults.length])

  const exportVisibilityOverlayCsv = useCallback(() => {
    if (visibilityOverlayFilteredResults.length === 0) {
      setVisibilityError('Nessun risultato da esportare in CSV.')
      return
    }

    downloadVisibilityResultsCsv(visibilityOverlayFilteredResults)
  }, [setVisibilityError, visibilityOverlayFilteredResults])

  useEffect(() => {
    if (!visibilityOverlayOpen) {
      return
    }

    const previousFocus = document.activeElement as HTMLElement | null
    visibilityOverlayCloseRef.current?.focus()
    const handleEscape = (event: KeyboardEvent) => {
      if (event.key === 'Escape') {
        setVisibilityOverlayOpen(false)
      }
    }
    document.addEventListener('keydown', handleEscape)

    return () => {
      document.removeEventListener('keydown', handleEscape)
      previousFocus?.focus()
    }
  }, [setVisibilityOverlayOpen, visibilityOverlayOpen])

  const focusBySatelliteHandler = useFocusBySatelliteId({
    liveEntityIdBySatelliteId,
    satelliteLookupByEntityId,
    handlePickEntityId,
    globeRef,
    fetchSatellitePositionById,
    setVisibilityError,
  })

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
      <main className="app-shell">
        <aside className="panel-section">
          <section className="panel-component panel-component-token">
            <h1>Cesium token mancante</h1>
            <p>
              Aggiungi <strong>VITE_CESIUM_TOKEN</strong> nel file .env alla radice e riavvia il
              frontend.
            </p>
          </section>
        </aside>
        <section className="viewer-section" />
      </main>
    )
  }

  return (
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
                notificationCount={notificationCount}
                onToggleNotifications={handleToggleNotifications}
                onLogout={() => {
                  void handleLogout()
                }}
                onPingSystemHealth={handlePingSystemHealth}
              />
              <CommunityNotificationTray
                open={notificationsOpen}
                notifications={notifications}
                onSelect={(notification) => {
                  void handleSelectNotification(notification)
                }}
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
                          <SidebarPaneContent pane="groups">
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
                          </SidebarPaneContent>
                        ) : null}

                        {openPane === 'satellites' ? (
                          <SidebarPaneContent pane="satellites">
                            <SatellitesPanel
                              autoRotate={autoRotate}
                              showBackSideSatellites={showBackSideSatellites}
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
                          </SidebarPaneContent>
                        ) : null}

                        {openPane === 'visibility' ? (
                          <SidebarPaneContent pane="visibility">
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
                              onFocusFromVisibility={focusBySatelliteHandler}
                            />
                          </SidebarPaneContent>
                        ) : null}

                        {openPane === 'sightings' ? (
                          <SidebarPaneContent pane="sightings">
                            <SightingsPanel
                              sightingInfo={sightingInfo}
                              sightingsError={sightingsError}
                              sightingsLoading={sightingsLoading}
                              mySightings={mySightings}
                              compactLandscapeViewport={landscapeMobileViewport}
                              onFocusSightingSatellite={focusBySatelliteHandler}
                            />
                          </SidebarPaneContent>
                        ) : null}

                        {openPane === 'community' ? (
                          <SidebarPaneContent pane="community">
                            <CommunityPanel
                              authUser={authUser}
                              selectedSatelliteId={selectedSatellite?.satellite.satelliteId ?? null}
                              selectedSatelliteName={selectedSatellite?.satellite.satelliteName ?? null}
                              onFocusSatellite={focusBySatelliteHandler}
                              pendingNotification={pendingCommunityNotification}
                              onPendingNotificationHandled={() => setPendingCommunityNotification(null)}
                            />
                          </SidebarPaneContent>
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
          <button
            type="button"
            className="focus-toggle"
            style={
              landscapeMobileViewport
                ? { right: 8, left: 'auto', top: 58 }
                : { left: focusGlobeMode ? 10 : panelWidth + 20, right: 'auto', top: 58 }
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
                : { left: focusGlobeMode ? 10 : panelWidth + 20, top: 108 }),
            }}
          >
            <button type="button" onClick={handleZoomIn} aria-label="Zoom rapido in">
              +
            </button>
            <button type="button" onClick={handleZoomOut} aria-label="Zoom rapido out">
              -
            </button>
          </div>
          {selectedSatellite ? (
            <SatelliteDetailsHud
              selectedSatellite={selectedSatellite}
              reportingSighting={reportingSighting}
              sightingCity={sightingCity}
              locatingBrowser={locatingBrowser}
              sightingLatitude={sightingLatitude}
              sightingLongitude={sightingLongitude}
              sightingInfo={sightingInfo}
              sightingsError={sightingsError}
              onReportSighting={() => {
                void handleReportSighting()
              }}
              onClose={closeSelectedSatellite}
              onSightingCityChange={setSightingCity}
              onUseBrowserLocation={handleUseBrowserLocation}
            />
          ) : null}

          <Suspense fallback={<p className="viewer-loading" aria-live="polite">Caricamento globo...</p>}>
            <LazySatelliteGlobe
              ref={globeRef}
              autoRotate={autoRotate}
              showBackSideSatellites={compactMobileViewport ? false : showBackSideSatellites}
              performanceMode={compactMobileViewport}
              groupColorMap={groupColorMap}
              selectedEntityId={selectedEntityId}
              starlinkSatellites={starlinkSatellites}
              visibleEntitySatellites={visibleEntitySatellites}
              onPickEntityId={handlePickEntityId}
            />
          </Suspense>
        </section>

        {visibilityOverlayOpen ? (
          <section className="visibility-overlay" role="dialog" aria-modal="true" aria-labelledby="visibility-overlay-title">
            <div
              className="visibility-overlay-backdrop"
              role="button"
              tabIndex={0}
              aria-label="Chiudi lista completa"
              onClick={() => setVisibilityOverlayOpen(false)}
              onKeyDown={(event) => {
                if (event.key === 'Enter' || event.key === ' ') {
                  setVisibilityOverlayOpen(false)
                }
              }}
            />
            <article className="visibility-overlay-card">
              <header className="visibility-overlay-header">
                <h2 id="visibility-overlay-title">Passaggi visibili - Lista completa</h2>
                <div className="visibility-overlay-header-actions">
                  <button type="button" onClick={exportVisibilityOverlayCsv}>
                    Export CSV
                  </button>
                  <button ref={visibilityOverlayCloseRef} type="button" onClick={() => setVisibilityOverlayOpen(false)}>
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
              <VisibilityResultsTable results={visibilityOverlayFilteredResults} />
            </article>
          </section>
        ) : null}
      </main>
  )
}

export default App
