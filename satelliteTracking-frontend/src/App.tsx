import { useCallback, useEffect, useMemo, useRef, useState } from 'react'
import { Color, Ion } from 'cesium'
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

type SidebarPane = 'groups' | 'satellites'

const defaultEnabledGroups = Object.fromEntries(
  satelliteGroupSources.map((group) => [group.key, group.key === 'stations']),
) as Record<SatelliteGroupKey, boolean>

const fixedRefreshIntervalSec = 0.8

type GroupPreset = 'custom' | 'all' | 'stations' | 'navigation' | 'leo'

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
  const [focusGlobeMode, setFocusGlobeMode] = useState(false)
  const [selectedSatellite, setSelectedSatellite] = useState<SelectedSatelliteState | null>(null)
  const [selectedEntityId, setSelectedEntityId] = useState<string | null>(null)
  const [autoRotate, setAutoRotate] = useState(true)
  const [showBackSideSatellites, setShowBackSideSatellites] = useState(false)
  const latestRequestIdRef = useRef(0)
  const inFlightRequestRef = useRef(false)
  const [compass, setCompass] = useState<CompassState>({
    headingDeg: 0,
    pitchDeg: 0,
    altitudeKm: 0,
  })

  const refreshIntervalMs = Math.round(fixedRefreshIntervalSec * 1000)

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

  const handleCompassChange = useCallback((nextCompass: CompassState) => {
    setCompass(nextCompass)
  }, [])

  useEffect(() => {
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

      activeGroups.forEach((group, index) => {
        const result = results[index]
        nextLoading[group.key] = false

        if (result.status === 'fulfilled') {
          nextPositions[group.key] = result.value.positions
          nextErrors[group.key] = ''
          return
        }

        nextPositions[group.key] = []
        nextErrors[group.key] = `Errore caricamento ${group.label}`
      })

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
  }, [activeGroups, refreshIntervalMs])

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
                      <div className="compass-row"><span>Heading</span><strong>{compass.headingDeg.toFixed(1)}deg</strong></div>
                      <div className="compass-row"><span>Pitch</span><strong>{compass.pitchDeg.toFixed(1)}deg</strong></div>
                      <div className="compass-row"><span>Quota camera</span><strong>{compass.altitudeKm.toFixed(0)} km</strong></div>
                    </section>
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
                <button type="button" onClick={closeSelectedSatellite}>Chiudi</button>
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
