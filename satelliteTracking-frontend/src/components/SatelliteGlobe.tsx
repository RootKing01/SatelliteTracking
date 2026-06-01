import { forwardRef, memo, useEffect, useImperativeHandle, useMemo, useRef, useState } from 'react'
import {
  BoundingSphere,
  Cartesian3,
  Color,
  EllipsoidTerrainProvider,
  HeadingPitchRoll,
  HeadingPitchRange,
  HorizontalOrigin,
  OpenStreetMapImageryProvider,
  PointPrimitive,
  PointPrimitiveCollection,
  ScreenSpaceEventHandler,
  ScreenSpaceEventType,
  Math as CesiumMath,
  VerticalOrigin,
  type Viewer as CesiumViewer,
} from 'cesium'
import { Entity, Viewer, type CesiumComponentRef } from 'resium'
import Moon, { computeMoonPosition } from './Moon'
import type { SatelliteGroupKey, SatelliteGroupSource } from '../api/groups/types'
import type { SatellitePosition } from '../types/satellite'

const earthRotationSpeed = 0.00015
const initialCameraDestination = Cartesian3.fromDegrees(-20, -6, 24000000)
const initialCameraOrientation = new HeadingPitchRoll(0, -1.5, 0)

export type VisibleSatelliteItem = {
  group: SatelliteGroupSource
  satellite: SatellitePosition
}

type SelectedSatelliteTarget = {
  entityId: string
  satelliteName: string
  description: string
  longitudeDeg: number
  latitudeDeg: number
  altitudeKm: number
}

export type SatelliteGlobeHandle = {
  zoomIn: () => void
  zoomOut: () => void
  goToInitialView: () => void
  alignToEarthAxis: () => void
  focusOnMoon: () => void
  focusOnSatellite: (
    longitudeDeg: number,
    latitudeDeg: number,
    altitudeKm?: number,
    entityId?: string,
  ) => void
}

type SatelliteGlobeProps = {
  autoRotate: boolean
  showBackSideSatellites: boolean
  showMoon?: boolean
  performanceMode?: boolean
  groupColorMap: Record<SatelliteGroupKey, Color>
  selectedEntityId: string | null
  starlinkSatellites: SatellitePosition[]
  visibleEntitySatellites: VisibleSatelliteItem[]
  onPickEntityId: (entityId: string | null) => void
}

const SatelliteGlobeBase = forwardRef<SatelliteGlobeHandle, SatelliteGlobeProps>(
  (
    {
      autoRotate,
      showBackSideSatellites,
      showMoon = true,
      performanceMode = false,
      groupColorMap,
      selectedEntityId,
      starlinkSatellites,
      visibleEntitySatellites,
      onPickEntityId,
    },
    ref,
  ) => {
    const viewerRef = useRef<CesiumComponentRef<CesiumViewer>>(null)
    const starlinkPointsRef = useRef<PointPrimitiveCollection | null>(null)
    const starlinkPrimitiveByIdRef = useRef<Map<number, PointPrimitive>>(new Map())
    const starlinkNextIdsRef = useRef<Set<number>>(new Set())
    const groupPointsRef = useRef<PointPrimitiveCollection | null>(null)
    const groupPrimitiveByEntityIdRef = useRef<Map<string, PointPrimitive>>(new Map())
    const groupNextEntityIdsRef = useRef<Set<string>>(new Set())
    const lastTrackedEntityIdRef = useRef<string | null>(null)
    const autoRotateRef = useRef(autoRotate)
    const pauseAutoRotateUntilRef = useRef(0)
    const lastAutoRotateTickRef = useRef(0)
    const autoRotateIntervalRef = useRef<number | null>(null)
    const [viewerReadyTick, setViewerReadyTick] = useState(0)
    const terrainProvider = useMemo(() => new EllipsoidTerrainProvider(), [])
    const selectedPulsePixelSize = 18
    const selectedPulseColor = useMemo(() => Color.fromAlpha(Color.WHITE, 0.28), [])
    const selectedReticleImage = useMemo(() => {
      const svg = `
        <svg xmlns="http://www.w3.org/2000/svg" width="64" height="64" viewBox="0 0 64 64" fill="none">
          <path d="M10 20V10h10" stroke="#7ff9ff" stroke-width="4" stroke-linecap="round" stroke-linejoin="round"/>
          <path d="M54 20V10H44" stroke="#7ff9ff" stroke-width="4" stroke-linecap="round" stroke-linejoin="round"/>
          <path d="M10 44v10h10" stroke="#7ff9ff" stroke-width="4" stroke-linecap="round" stroke-linejoin="round"/>
          <path d="M54 44v10H44" stroke="#7ff9ff" stroke-width="4" stroke-linecap="round" stroke-linejoin="round"/>
          <circle cx="32" cy="32" r="3.5" fill="#7ff9ff" fill-opacity="0.98"/>
        </svg>
      `.trim()

      return `data:image/svg+xml;charset=utf-8,${encodeURIComponent(svg)}`
    }, [])
    const selectedReticleHaloImage = useMemo(() => {
      const svg = `
        <svg xmlns="http://www.w3.org/2000/svg" width="96" height="96" viewBox="0 0 96 96" fill="none">
          <rect x="16" y="16" width="64" height="64" rx="8" stroke="#7ff9ff" stroke-width="2" stroke-opacity="0.18"/>
          <rect x="24" y="24" width="48" height="48" rx="6" stroke="#7ff9ff" stroke-width="2" stroke-opacity="0.14"/>
          <circle cx="48" cy="48" r="20" fill="#7ff9ff" fill-opacity="0.06"/>
        </svg>
      `.trim()

      return `data:image/svg+xml;charset=utf-8,${encodeURIComponent(svg)}`
    }, [])
    const selectedStarlinkSatellite = useMemo(() => {
      if (!selectedEntityId || !selectedEntityId.startsWith('starlink-')) {
        return null
      }

      const parsedId = Number(selectedEntityId.replace('starlink-', ''))
      if (!Number.isFinite(parsedId)) {
        return null
      }

      return (
        starlinkSatellites.find((satellite) => satellite.satelliteId === parsedId) ?? null
      )
    }, [selectedEntityId, starlinkSatellites])
    const selectedVisibleSatellite = useMemo(() => {
      if (!selectedEntityId || selectedEntityId.startsWith('starlink-')) {
        return null
      }

      return (
        visibleEntitySatellites.find(
          ({ group, satellite }) => `${group.key}-${satellite.satelliteId}` === selectedEntityId,
        ) ?? null
      )
    }, [selectedEntityId, visibleEntitySatellites])
    const selectedTarget = useMemo<SelectedSatelliteTarget | null>(() => {
      if (selectedStarlinkSatellite) {
        return {
          entityId: `starlink-${selectedStarlinkSatellite.satelliteId}`,
          satelliteName: selectedStarlinkSatellite.satelliteName,
          description: `Starlink | NORAD ${selectedStarlinkSatellite.noradCatId}`,
          longitudeDeg: selectedStarlinkSatellite.longitudeDeg,
          latitudeDeg: selectedStarlinkSatellite.latitudeDeg,
          altitudeKm: selectedStarlinkSatellite.altitudeKm,
        }
      }

      if (selectedVisibleSatellite) {
        return {
          entityId: `${selectedVisibleSatellite.group.key}-${selectedVisibleSatellite.satellite.satelliteId}`,
          satelliteName: selectedVisibleSatellite.satellite.satelliteName,
          description: `${selectedVisibleSatellite.group.label} | NORAD ${selectedVisibleSatellite.satellite.noradCatId}`,
          longitudeDeg: selectedVisibleSatellite.satellite.longitudeDeg,
          latitudeDeg: selectedVisibleSatellite.satellite.latitudeDeg,
          altitudeKm: selectedVisibleSatellite.satellite.altitudeKm,
        }
      }

      return null
    }, [selectedEntityId, selectedStarlinkSatellite, selectedVisibleSatellite])
    useEffect(() => {
      if (viewerReadyTick > 0) {
        return
      }

      let timeoutId = 0
      const waitForViewer = () => {
        if (viewerRef.current?.cesiumElement) {
          setViewerReadyTick(1)
          return
        }
        timeoutId = window.setTimeout(waitForViewer, 50)
      }

      waitForViewer()

      return () => {
        window.clearTimeout(timeoutId)
      }
    }, [viewerReadyTick])

    useEffect(() => {
      autoRotateRef.current = autoRotate
    }, [autoRotate])

    useImperativeHandle(ref, () => ({
      zoomIn: () => {
        const viewer = viewerRef.current?.cesiumElement
        if (!viewer) {
          return
        }

        const currentHeight = viewer.camera.positionCartographic.height
        viewer.camera.zoomIn(Math.max(20000, currentHeight * 0.28))
      },
      zoomOut: () => {
        const viewer = viewerRef.current?.cesiumElement
        if (!viewer) {
          return
        }

        const currentHeight = viewer.camera.positionCartographic.height
        viewer.camera.zoomOut(Math.max(40000, currentHeight * 0.38))
      },
      goToInitialView: () => {
        const viewer = viewerRef.current?.cesiumElement
        if (!viewer) {
          return
        }

        viewer.camera.flyTo({
          destination: initialCameraDestination,
          orientation: {
            heading: initialCameraOrientation.heading,
            pitch: initialCameraOrientation.pitch,
            roll: initialCameraOrientation.roll,
          },
          duration: 1.1,
        })
      },
      alignToEarthAxis: () => {
        const viewer = viewerRef.current?.cesiumElement
        if (!viewer) {
          return
        }

        viewer.camera.flyTo({
          destination: viewer.camera.positionWC.clone(),
          orientation: {
            heading: 0,
            pitch: viewer.camera.pitch,
            roll: 0,
          },
          duration: 0.9,
        })
      },
      focusOnMoon: () => {
        const viewer = viewerRef.current?.cesiumElement
        if (!viewer) {
          return
        }

        const { lon, lat, altMeters } = computeMoonPosition()
        const moonPosition = Cartesian3.fromDegrees(lon, lat, altMeters)

        viewer.camera.cancelFlight()
        pauseAutoRotateUntilRef.current = Date.now() + 1800

        const moonSphere = new BoundingSphere(moonPosition, 900000)
        viewer.camera.flyToBoundingSphere(moonSphere, {
          offset: new HeadingPitchRange(viewer.camera.heading, CesiumMath.toRadians(-22), 2600000),
          duration: 1.35,
        })

        const entity = viewer.entities.getById('moon-entity')
        if (entity) {
          viewer.selectedEntity = entity
          viewer.trackedEntity = entity
          lastTrackedEntityIdRef.current = 'moon-entity'
        }
      },
      focusOnSatellite: (
        longitudeDeg: number,
        latitudeDeg: number,
        altitudeKm = 500,
        entityId?: string,
      ) => {
        const viewer = viewerRef.current?.cesiumElement
        if (!viewer) {
          return
        }

        if (!Number.isFinite(longitudeDeg) || !Number.isFinite(latitudeDeg)) {
          return
        }

        const clampedLatitude = Math.max(-85, Math.min(85, latitudeDeg))
        const normalizedLongitude = ((((longitudeDeg + 180) % 360) + 360) % 360) - 180

        const safeAltitudeKm = Math.max(200, altitudeKm)
        const targetPosition = Cartesian3.fromDegrees(
          normalizedLongitude,
          clampedLatitude,
          safeAltitudeKm * 1000,
        )

        if (entityId) {
          const entity = viewer.entities.getById(entityId)
          if (entity) {
            viewer.selectedEntity = entity
            viewer.trackedEntity = entity
            lastTrackedEntityIdRef.current = entityId
          }
        }

        viewer.camera.cancelFlight()
        pauseAutoRotateUntilRef.current = Date.now() + 1800

        const focusSphere = new BoundingSphere(
          targetPosition,
          Math.max(110000, safeAltitudeKm * 320),
        )
        const focusRange = Math.max(780000, safeAltitudeKm * 1900)

        viewer.camera.flyToBoundingSphere(focusSphere, {
          offset: new HeadingPitchRange(viewer.camera.heading, CesiumMath.toRadians(-42), focusRange),
          duration: 1.05,
        })
      },
    }))

    useEffect(() => {
      const viewer = viewerRef.current?.cesiumElement
      if (!viewer) {
        return
      }

      if (!selectedEntityId) {
        viewer.trackedEntity = undefined
        lastTrackedEntityIdRef.current = null
      }
    }, [selectedEntityId, viewerReadyTick])

    useEffect(() => {
      const viewer = viewerRef.current?.cesiumElement
      if (!viewer || !selectedEntityId) {
        return
      }

      if (lastTrackedEntityIdRef.current === selectedEntityId) {
        return
      }

      const entity = viewer.entities.getById(selectedEntityId)
      if (entity) {
        viewer.selectedEntity = entity
        viewer.trackedEntity = entity
        lastTrackedEntityIdRef.current = selectedEntityId
      }
    }, [selectedEntityId, selectedStarlinkSatellite, viewerReadyTick])

    useEffect(() => {
      const viewer = viewerRef.current?.cesiumElement
      if (!viewer || !selectedVisibleSatellite) {
        return
      }

      const entityId = `${selectedVisibleSatellite.group.key}-${selectedVisibleSatellite.satellite.satelliteId}`
      if (lastTrackedEntityIdRef.current === entityId) {
        return
      }

      const entity = viewer.entities.getById(entityId)
      if (entity) {
        viewer.selectedEntity = entity
        viewer.trackedEntity = entity
        lastTrackedEntityIdRef.current = entityId
      }
    }, [selectedVisibleSatellite, viewerReadyTick])

    useEffect(() => {
      const viewer = viewerRef.current?.cesiumElement
      if (!viewer) {
        return
      }

      viewer.resolutionScale = performanceMode ? 0.75 : 1
      viewer.scene.highDynamicRange = false
      viewer.scene.requestRenderMode = true
      viewer.scene.maximumRenderTimeChange = Infinity
      viewer.scene.postProcessStages.fxaa.enabled = false
      viewer.scene.globe.maximumScreenSpaceError = performanceMode ? 8 : 4
      viewer.scene.globe.tileCacheSize = performanceMode ? 80 : 160
      viewer.scene.globe.baseColor = Color.fromCssColorString('#0b1d33')
      viewer.scene.globe.depthTestAgainstTerrain = true
      viewer.scene.globe.translucency.enabled = false
      viewer.scene.globe.showGroundAtmosphere = false
      if (viewer.scene.skyAtmosphere) {
        viewer.scene.skyAtmosphere.show = false
      }
      viewer.scene.fog.enabled = false
      viewer.scene.globe.enableLighting = false

      const controller = viewer.scene.screenSpaceCameraController
      controller.minimumZoomDistance = 15000
      controller.maximumZoomDistance = 45000000

      if (import.meta.env.DEV) {
        viewer.imageryLayers.removeAll()
        viewer.imageryLayers.addImageryProvider(
          new OpenStreetMapImageryProvider({
            url: 'https://tile.openstreetmap.org/',
          }),
        )
      }

      viewer.camera.setView({
        destination: initialCameraDestination,
        orientation: {
          heading: initialCameraOrientation.heading,
          pitch: initialCameraOrientation.pitch,
          roll: initialCameraOrientation.roll,
        },
      })

      viewer.camera.percentageChanged = 0.002
    }, [viewerReadyTick])

    useEffect(() => {
      const viewer = viewerRef.current?.cesiumElement
      if (!viewer) {
        return
      }

      if (autoRotateIntervalRef.current !== null) {
        window.clearInterval(autoRotateIntervalRef.current)
      }

      const autoRotateTickMs = performanceMode ? 200 : 100

      autoRotateIntervalRef.current = window.setInterval(() => {
        if (!viewerRef.current?.cesiumElement) {
          return
        }

        if (selectedEntityId) {
          return
        }

        if (Date.now() < pauseAutoRotateUntilRef.current) {
          return
        }

        const now = Date.now()
        if (autoRotateRef.current && now - lastAutoRotateTickRef.current >= 100) {
          lastAutoRotateTickRef.current = now
          viewer.scene.camera.rotateRight(earthRotationSpeed)
          viewer.scene.requestRender()
        }
      }, autoRotateTickMs)

      return () => {
        if (autoRotateIntervalRef.current !== null) {
          window.clearInterval(autoRotateIntervalRef.current)
          autoRotateIntervalRef.current = null
        }
      }
    }, [performanceMode, selectedEntityId, viewerReadyTick])

    useEffect(() => {
      const viewer = viewerRef.current?.cesiumElement
      if (!viewer) {
        return
      }

      if (!starlinkPointsRef.current) {
        starlinkPointsRef.current = viewer.scene.primitives.add(new PointPrimitiveCollection())
      }

      const collection = starlinkPointsRef.current
      if (!collection) {
        return
      }

      const starlinkColor = groupColorMap.starlink
      const nextIds = starlinkNextIdsRef.current
      nextIds.clear()
      // Process updates in chunks to avoid blocking the scheduler/message loop
      const CHUNK = 100
      let idx = 0
      const pendingTimeouts: number[] = []
      const pendingIdleIds: number[] = []

      const scheduleNext = (fn: () => void) => {
        if ((window as any).requestIdleCallback) {
          const id = (window as any).requestIdleCallback(fn, { timeout: 50 }) as number
          pendingIdleIds.push(id)
        } else {
          pendingTimeouts.push(window.setTimeout(fn, 0))
        }
      }

      const processChunk = () => {
        const end = Math.min(idx + CHUNK, starlinkSatellites.length)
        for (; idx < end; idx++) {
          const satellite = starlinkSatellites[idx]
          const satelliteId = satellite.satelliteId
          nextIds.add(satelliteId)

          const entityId = `starlink-${satelliteId}`
          const isSelected = selectedEntityId === entityId
          const altitudeMeters = Math.max(0, satellite.altitudeKm * 1000)
          let primitive = starlinkPrimitiveByIdRef.current.get(satelliteId)

          if (!primitive) {
            primitive = collection.add({
              position: Cartesian3.fromDegrees(
                satellite.longitudeDeg,
                satellite.latitudeDeg,
                altitudeMeters,
              ),
              color: starlinkColor,
              pixelSize: isSelected ? 6 : 4,
              outlineColor: Color.WHITE,
              outlineWidth: isSelected ? 3 : 0,
              disableDepthTestDistance: showBackSideSatellites ? Number.POSITIVE_INFINITY : 0,
              id: {
                entityId,
              },
            })
            starlinkPrimitiveByIdRef.current.set(satelliteId, primitive)
            continue
          }

          primitive.position = Cartesian3.fromDegrees(
            satellite.longitudeDeg,
            satellite.latitudeDeg,
            altitudeMeters,
          )
          primitive.color = starlinkColor
          primitive.pixelSize = isSelected ? 6 : 4
          primitive.outlineColor = Color.WHITE
          primitive.outlineWidth = isSelected ? 3 : 0
          primitive.disableDepthTestDistance = showBackSideSatellites ? Number.POSITIVE_INFINITY : 0
        }

        if (idx < starlinkSatellites.length) {
          scheduleNext(processChunk)
        } else {
          // cleanup removed primitives after finishing updates
          for (const [satelliteId, primitive] of starlinkPrimitiveByIdRef.current) {
            if (!nextIds.has(satelliteId)) {
              collection.remove(primitive)
              starlinkPrimitiveByIdRef.current.delete(satelliteId)
            }
          }
        }
      }

      processChunk()

      return () => {
        for (const t of pendingTimeouts) {
          window.clearTimeout(t)
        }
        for (const id of pendingIdleIds) {
          if ((window as any).cancelIdleCallback) {
            (window as any).cancelIdleCallback(id)
          }
        }
      }
    }, [groupColorMap, selectedEntityId, showBackSideSatellites, starlinkSatellites])

    useEffect(() => {
      const viewer = viewerRef.current?.cesiumElement
      if (!viewer) {
        return
      }

      if (!groupPointsRef.current) {
        groupPointsRef.current = viewer.scene.primitives.add(new PointPrimitiveCollection())
      }

      const collection = groupPointsRef.current
      if (!collection) {
        return
      }

      const nextEntityIds = groupNextEntityIdsRef.current
      nextEntityIds.clear()

      // Chunk updates to avoid blocking the main thread when many entities are present
      const CHUNK = 100
      let idx = 0
      const pendingTimeouts: number[] = []
      const pendingIdleIds: number[] = []

      const scheduleNext = (fn: () => void) => {
        if ((window as any).requestIdleCallback) {
          const id = (window as any).requestIdleCallback(fn, { timeout: 50 }) as number
          pendingIdleIds.push(id)
        } else {
          pendingTimeouts.push(window.setTimeout(fn, 0))
        }
      }

      const processChunk = () => {
        const end = Math.min(idx + CHUNK, visibleEntitySatellites.length)
        for (; idx < end; idx++) {
          const { group, satellite } = visibleEntitySatellites[idx]
          const entityId = `${group.key}-${satellite.satelliteId}`
          nextEntityIds.add(entityId)

          const isSelected = selectedEntityId === entityId
          const altitudeMeters = Math.max(0, satellite.altitudeKm * 1000)
          const groupColor = groupColorMap[group.key]
          let primitive = groupPrimitiveByEntityIdRef.current.get(entityId)

          if (!primitive) {
            primitive = collection.add({
              position: Cartesian3.fromDegrees(
                satellite.longitudeDeg,
                satellite.latitudeDeg,
                altitudeMeters,
              ),
              color: groupColor,
              pixelSize: isSelected ? 13 : 7,
              outlineColor: Color.WHITE,
              outlineWidth: isSelected ? 3 : 1,
              disableDepthTestDistance: showBackSideSatellites ? Number.POSITIVE_INFINITY : 0,
              id: {
                entityId,
              },
            })
            groupPrimitiveByEntityIdRef.current.set(entityId, primitive)
            continue
          }

          primitive.position = Cartesian3.fromDegrees(
            satellite.longitudeDeg,
            satellite.latitudeDeg,
            altitudeMeters,
          )
          primitive.color = groupColor
          primitive.pixelSize = isSelected ? 13 : 7
          primitive.outlineColor = Color.WHITE
          primitive.outlineWidth = isSelected ? 3 : 1
          primitive.disableDepthTestDistance = showBackSideSatellites ? Number.POSITIVE_INFINITY : 0
        }

        if (idx < visibleEntitySatellites.length) {
          scheduleNext(processChunk)
        } else {
          for (const [entityId, primitive] of groupPrimitiveByEntityIdRef.current) {
            if (!nextEntityIds.has(entityId)) {
              collection.remove(primitive)
              groupPrimitiveByEntityIdRef.current.delete(entityId)
            }
          }
        }
      }

      processChunk()

      return () => {
        for (const t of pendingTimeouts) {
          window.clearTimeout(t)
        }
        for (const id of pendingIdleIds) {
          if ((window as any).cancelIdleCallback) {
            (window as any).cancelIdleCallback(id)
          }
        }
      }
    }, [groupColorMap, selectedEntityId, showBackSideSatellites, visibleEntitySatellites])

    useEffect(() => {
      const viewer = viewerRef.current?.cesiumElement
      if (!viewer) {
        return
      }

      const clickHandler = new ScreenSpaceEventHandler(viewer.scene.canvas)

      clickHandler.setInputAction((event: { position: unknown }) => {
        const picked = viewer.scene.pick(event.position as Parameters<typeof viewer.scene.pick>[0])
        if (!picked) {
          onPickEntityId(null)
          return
        }

        const pickedId = (picked as { id?: unknown }).id

        let entityId: string | undefined
        if (typeof pickedId === 'string') {
          entityId = pickedId
        } else if (
          pickedId &&
          typeof pickedId === 'object' &&
          'id' in pickedId &&
          typeof (pickedId as { id?: unknown }).id === 'string'
        ) {
          entityId = (pickedId as { id: string }).id
        } else if (
          pickedId &&
          typeof pickedId === 'object' &&
          'entityId' in pickedId &&
          typeof (pickedId as { entityId?: unknown }).entityId === 'string'
        ) {
          entityId = (pickedId as { entityId: string }).entityId
        }

        onPickEntityId(entityId ?? null)
      }, ScreenSpaceEventType.LEFT_CLICK)

      return () => {
        clickHandler.destroy()
      }
    }, [onPickEntityId])

    useEffect(() => {
      return () => {
        const viewer = viewerRef.current?.cesiumElement
        if (viewer && starlinkPointsRef.current) {
          viewer.scene.primitives.remove(starlinkPointsRef.current)
          starlinkPointsRef.current = null
        }
        if (viewer && groupPointsRef.current) {
          viewer.scene.primitives.remove(groupPointsRef.current)
          groupPointsRef.current = null
        }
        starlinkPrimitiveByIdRef.current.clear()
        groupPrimitiveByEntityIdRef.current.clear()
      }
    }, [])

    return (
      <Viewer
        ref={viewerRef}
        className="viewer"
        terrainProvider={terrainProvider}
        animation={false}
        timeline={false}
        baseLayerPicker={false}
        geocoder={false}
        homeButton={false}
        navigationHelpButton={false}
        sceneModePicker={false}
        infoBox={false}
        selectionIndicator={false}
      >
        {selectedStarlinkSatellite ? (
          <Entity
            key={`starlink-${selectedStarlinkSatellite.satelliteId}`}
            id={`starlink-${selectedStarlinkSatellite.satelliteId}`}
            name={selectedStarlinkSatellite.satelliteName}
            description={`Starlink | NORAD ${selectedStarlinkSatellite.noradCatId}`}
            position={Cartesian3.fromDegrees(
              selectedStarlinkSatellite.longitudeDeg,
              selectedStarlinkSatellite.latitudeDeg,
              Math.max(0, selectedStarlinkSatellite.altitudeKm * 1000),
            )}
            point={{
              pixelSize: 1,
              color: Color.TRANSPARENT,
              outlineColor: Color.TRANSPARENT,
              outlineWidth: 0,
              disableDepthTestDistance: Number.POSITIVE_INFINITY,
            }}
          />
        ) : null}

        {selectedVisibleSatellite ? (
          <Entity
            key={`${selectedVisibleSatellite.group.key}-${selectedVisibleSatellite.satellite.satelliteId}`}
            id={`${selectedVisibleSatellite.group.key}-${selectedVisibleSatellite.satellite.satelliteId}`}
            name={selectedVisibleSatellite.satellite.satelliteName}
            description={`${selectedVisibleSatellite.group.label} | NORAD ${selectedVisibleSatellite.satellite.noradCatId}`}
            position={Cartesian3.fromDegrees(
              selectedVisibleSatellite.satellite.longitudeDeg,
              selectedVisibleSatellite.satellite.latitudeDeg,
              Math.max(0, selectedVisibleSatellite.satellite.altitudeKm * 1000),
            )}
            point={{
              pixelSize: 1,
              color: Color.TRANSPARENT,
              outlineColor: Color.TRANSPARENT,
              outlineWidth: 0,
              disableDepthTestDistance: Number.POSITIVE_INFINITY,
            }}
          />
        ) : null}

        {selectedTarget ? (
          <>
            <Entity
              key={`${selectedTarget.entityId}-reticle-halo`}
              id={`${selectedTarget.entityId}-reticle-halo`}
              position={Cartesian3.fromDegrees(
                selectedTarget.longitudeDeg,
                selectedTarget.latitudeDeg,
                Math.max(0, selectedTarget.altitudeKm * 1000),
              )}
              billboard={{
                image: selectedReticleHaloImage,
                width: 88,
                height: 88,
                horizontalOrigin: HorizontalOrigin.CENTER,
                verticalOrigin: VerticalOrigin.CENTER,
                disableDepthTestDistance: Number.POSITIVE_INFINITY,
              }}
            />
            <Entity
              key={`${selectedTarget.entityId}-reticle`}
              id={`${selectedTarget.entityId}-reticle`}
              name={`${selectedTarget.satelliteName} mirino`}
              description={selectedTarget.description}
              position={Cartesian3.fromDegrees(
                selectedTarget.longitudeDeg,
                selectedTarget.latitudeDeg,
                Math.max(0, selectedTarget.altitudeKm * 1000),
              )}
              billboard={{
                image: selectedReticleImage,
                width: 58,
                height: 58,
                horizontalOrigin: HorizontalOrigin.CENTER,
                verticalOrigin: VerticalOrigin.CENTER,
                disableDepthTestDistance: Number.POSITIVE_INFINITY,
              }}
            />
          </>
        ) : null}

        {/* Moon component handles its own visuals and glow */}
        <Moon show={!!showMoon} />

        {selectedVisibleSatellite ? (
          <Entity
            key={`${selectedVisibleSatellite.group.key}-${selectedVisibleSatellite.satellite.satelliteId}-pulse`}
            position={Cartesian3.fromDegrees(
              selectedVisibleSatellite.satellite.longitudeDeg,
              selectedVisibleSatellite.satellite.latitudeDeg,
              Math.max(0, selectedVisibleSatellite.satellite.altitudeKm * 1000),
            )}
            point={{
              pixelSize: selectedPulsePixelSize,
              color: selectedPulseColor,
              outlineColor: Color.WHITE,
              outlineWidth: 1,
              disableDepthTestDistance: showBackSideSatellites ? Number.POSITIVE_INFINITY : 0,
            }}
          />
        ) : null}
      </Viewer>
    )
  },
)

export const SatelliteGlobe = memo(SatelliteGlobeBase)

SatelliteGlobe.displayName = 'SatelliteGlobe'
