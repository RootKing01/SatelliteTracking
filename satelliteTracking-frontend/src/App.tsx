import { useEffect, useMemo, useRef, useState } from 'react'
import {
  Cartesian3,
  Color,
  EllipsoidTerrainProvider,
  GridImageryProvider,
  HeadingPitchRoll,
  Ion,
  type Viewer as CesiumViewer,
} from 'cesium'
import { Entity, ImageryLayer, Viewer, type CesiumComponentRef } from 'resium'
import { satelliteGroupSources } from './api/groups'
import type { SatelliteGroupKey } from './api/groups/types'
import type { SatellitePosition } from './types/satellite'
import './App.css'

const ionToken =
  import.meta.env.VITE_CESIUM_TOKEN ?? import.meta.env.VITE_CESIUM_ION_TOKEN

if (ionToken) {
  Ion.defaultAccessToken = ionToken
}

const refreshIntervalMs = 20000

type GroupPositionsState = Partial<Record<SatelliteGroupKey, SatellitePosition[]>>
type GroupLoadingState = Partial<Record<SatelliteGroupKey, boolean>>
type GroupErrorState = Partial<Record<SatelliteGroupKey, string>>

function App() {
  const viewerRef = useRef<CesiumComponentRef<CesiumViewer>>(null)
  const [groupPositions, setGroupPositions] = useState<GroupPositionsState>({})
  const [groupLoading, setGroupLoading] = useState<GroupLoadingState>({})
  const [groupErrors, setGroupErrors] = useState<GroupErrorState>({})
  const [lastUpdatedAt, setLastUpdatedAt] = useState<string | null>(null)
  const [enabledGroups, setEnabledGroups] = useState<Record<SatelliteGroupKey, boolean>>({
    stations: true,
    starlink: true,
    gpsOps: false,
    weather: false,
  })

  const terrainProvider = useMemo(() => new EllipsoidTerrainProvider(), [])
  const gridProvider = useMemo(
    () =>
      new GridImageryProvider({
        cells: 8,
        color: Color.fromCssColorString('rgba(134, 173, 214, 0.35)'),
        glowColor: Color.fromCssColorString('rgba(9, 17, 31, 0.55)'),
      }),
    [],
  )
  const groupColorMap = useMemo(
    () =>
      Object.fromEntries(
        satelliteGroupSources.map((group) => [group.key, Color.fromCssColorString(group.color)]),
      ) as Record<SatelliteGroupKey, Color>,
    [],
  )

  const activeGroups = useMemo(
    () => satelliteGroupSources.filter((group) => enabledGroups[group.key]),
    [enabledGroups],
  )

  const visibleSatellites = useMemo(
    () =>
      activeGroups.flatMap((group) =>
        (groupPositions[group.key] ?? []).map((satellite) => ({
          group,
          satellite,
        })),
      ),
    [activeGroups, groupPositions],
  )

  useEffect(() => {
    const viewer = viewerRef.current?.cesiumElement
    if (!viewer) {
      return
    }

    // Improve visual sharpness on high DPI displays.
    const dpr = window.devicePixelRatio || 1
    viewer.resolutionScale = Math.min(1.5, Math.max(1, dpr))
    viewer.scene.highDynamicRange = true
    viewer.scene.postProcessStages.fxaa.enabled = true
    viewer.scene.globe.maximumScreenSpaceError = 2
    viewer.scene.globe.tileCacheSize = 1000
    viewer.scene.globe.baseColor = Color.fromCssColorString('#09111f')
    viewer.scene.globe.depthTestAgainstTerrain = false
    viewer.scene.globe.showGroundAtmosphere = false
    if (viewer.scene.skyAtmosphere) {
      viewer.scene.skyAtmosphere.show = true
    }
    viewer.scene.fog.enabled = false

    const controller = viewer.scene.screenSpaceCameraController
    controller.minimumZoomDistance = 15000
    controller.maximumZoomDistance = 45000000

    viewer.camera.setView({
      destination: Cartesian3.fromDegrees(12, 20, 19000000),
      orientation: new HeadingPitchRoll(0, -1.35, 0),
    })
  }, [])

  useEffect(() => {
    if (activeGroups.length === 0) {
      setGroupPositions({})
      setGroupErrors({})
      setGroupLoading({})
      return
    }

    let isMounted = true

    const loadGroups = async (signal?: AbortSignal) => {
      for (const group of activeGroups) {
        if (!isMounted) {
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
      setLastUpdatedAt(new Date().toLocaleTimeString())
    }

    const controller = new AbortController()
    void loadGroups(controller.signal)

    const refreshId = window.setInterval(() => {
      const refreshController = new AbortController()
      void loadGroups(refreshController.signal)
    }, refreshIntervalMs)

    return () => {
      isMounted = false
      controller.abort()
      window.clearInterval(refreshId)
    }
  }, [activeGroups])

  const zoomIn = () => {
    const viewer = viewerRef.current?.cesiumElement
    if (!viewer) {
      return
    }

    const currentHeight = viewer.camera.positionCartographic.height
    viewer.camera.zoomIn(Math.max(20000, currentHeight * 0.28))
  }

  const zoomOut = () => {
    const viewer = viewerRef.current?.cesiumElement
    if (!viewer) {
      return
    }

    const currentHeight = viewer.camera.positionCartographic.height
    viewer.camera.zoomOut(Math.max(40000, currentHeight * 0.38))
  }

  const toggleGroup = (key: SatelliteGroupKey) => {
    setEnabledGroups((prev) => ({
      ...prev,
      [key]: !prev[key],
    }))
  }

  if (!ionToken) {
    return (
      <main className="app-shell">
        <div className="panel">
          <h1>Cesium token mancante</h1>
          <p>
            Aggiungi <strong>VITE_CESIUM_TOKEN</strong> nel file .env in root e riavvia il
            frontend.
          </p>
        </div>
      </main>
    )
  }

  return (
    <main className="app-shell">
      <header className="panel">
        <div className="panel-header">
          <span className="panel-badge">Live globe</span>
          <h1>Satellite Tracker</h1>
        </div>
        <p>Caricamento asincrono per gruppo da /api/satellites/positions</p>
        <div className="toolbar">
          <button type="button" onClick={zoomIn}>Zoom +</button>
          <button type="button" onClick={zoomOut}>Zoom -</button>
        </div>
        <div className="group-list">
          {satelliteGroupSources.map((group) => {
            const count = groupPositions[group.key]?.length ?? 0
            const loading = groupLoading[group.key]
            const error = groupErrors[group.key]

            return (
              <label key={group.key} className="group-item">
                <input
                  type="checkbox"
                  checked={enabledGroups[group.key]}
                  onChange={() => toggleGroup(group.key)}
                />
                <span className="group-name">{group.label}</span>
                <span className="group-meta">
                  {loading ? 'loading...' : `${count} sat`}
                </span>
                {error ? <span className="group-error">!</span> : null}
              </label>
            )
          })}
        </div>
        {lastUpdatedAt ? <p className="updated-at">Ultimo update: {lastUpdatedAt}</p> : null}
      </header>

      <Viewer
        ref={viewerRef}
        className="viewer"
        full
        terrainProvider={terrainProvider}
        animation={false}
        timeline={false}
        baseLayerPicker={false}
        geocoder={false}
        homeButton={false}
        navigationHelpButton={false}
        sceneModePicker={false}
      >
        <ImageryLayer imageryProvider={gridProvider} />
        {visibleSatellites.map(({ group, satellite }) => {
          const altitudeMeters = Math.max(0, satellite.altitudeKm * 1000)

          return (
            <Entity
              key={`${group.key}-${satellite.satelliteId}`}
              name={satellite.satelliteName}
              description={`${group.label} | NORAD ${satellite.noradCatId}`}
              position={Cartesian3.fromDegrees(
                satellite.longitudeDeg,
                satellite.latitudeDeg,
                altitudeMeters,
              )}
              point={{
                pixelSize: group.key === 'starlink' ? 4 : 7,
                color: groupColorMap[group.key],
              }}
            />
          )
        })}
      </Viewer>
    </main>
  )
}

export default App
