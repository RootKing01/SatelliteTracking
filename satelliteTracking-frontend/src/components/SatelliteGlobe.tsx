import { Fragment, forwardRef, useEffect, useImperativeHandle, useMemo, useRef, useState } from 'react'
import {
  BoundingSphere,
  CallbackProperty,
  Cartesian3,
  Color,
  EllipsoidTerrainProvider,
  HeadingPitchRoll,
  HeadingPitchRange,
  Math as CesiumMath,
  OpenStreetMapImageryProvider,
  PointPrimitiveCollection,
  ScreenSpaceEventHandler,
  ScreenSpaceEventType,
  defined,
  type Viewer as CesiumViewer,
} from 'cesium'
import { Entity, Viewer, type CesiumComponentRef } from 'resium'
import type { SatelliteGroupKey, SatelliteGroupSource } from '../api/groups/types'
import type { SatellitePosition } from '../types/satellite'

const earthRotationSpeed = 0.00015
const initialCameraDestination = Cartesian3.fromDegrees(-20, -6, 24000000)
const initialCameraOrientation = new HeadingPitchRoll(0, -1.5, 0)

export type CompassState = {
  headingDeg: number
  pitchDeg: number
  altitudeKm: number
}

export type VisibleSatelliteItem = {
  group: SatelliteGroupSource
  satellite: SatellitePosition
}

export type SatelliteGlobeHandle = {
  zoomIn: () => void
  zoomOut: () => void
  goToInitialView: () => void
  alignToEarthAxis: () => void
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
  groupColorMap: Record<SatelliteGroupKey, Color>
  selectedEntityId: string | null
  starlinkSatellites: SatellitePosition[]
  visibleEntitySatellites: VisibleSatelliteItem[]
  onPickEntityId: (entityId: string | null) => void
  onCompassChange: (compass: CompassState) => void
}

export const SatelliteGlobe = forwardRef<SatelliteGlobeHandle, SatelliteGlobeProps>(
  (
    {
      autoRotate,
      showBackSideSatellites,
      groupColorMap,
      selectedEntityId,
      starlinkSatellites,
      visibleEntitySatellites,
      onPickEntityId,
      onCompassChange,
    },
    ref,
  ) => {
    const viewerRef = useRef<CesiumComponentRef<CesiumViewer>>(null)
    const starlinkPointsRef = useRef<PointPrimitiveCollection | null>(null)
    const autoRotateRef = useRef(autoRotate)
    const pauseAutoRotateUntilRef = useRef(0)
    const [viewerReadyTick, setViewerReadyTick] = useState(0)
    const terrainProvider = useMemo(() => new EllipsoidTerrainProvider(), [])
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
    useEffect(() => {
      if (viewerReadyTick > 0) {
        return
      }

      let frameId = 0
      const waitForViewer = () => {
        if (viewerRef.current?.cesiumElement) {
          setViewerReadyTick(1)
          return
        }
        frameId = window.requestAnimationFrame(waitForViewer)
      }

      waitForViewer()

      return () => {
        window.cancelAnimationFrame(frameId)
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
      }
    }, [selectedEntityId, viewerReadyTick])

    useEffect(() => {
      const viewer = viewerRef.current?.cesiumElement
      if (!viewer || !selectedEntityId) {
        return
      }

      const entity = viewer.entities.getById(selectedEntityId)
      if (entity) {
        viewer.selectedEntity = entity
        viewer.trackedEntity = entity
      }
    }, [selectedEntityId, selectedStarlinkSatellite, viewerReadyTick])

    useEffect(() => {
      const viewer = viewerRef.current?.cesiumElement
      if (!viewer) {
        return
      }

      const dpr = window.devicePixelRatio || 1
      viewer.resolutionScale = Math.min(1.5, Math.max(1, dpr))
      viewer.scene.highDynamicRange = true
      viewer.scene.postProcessStages.fxaa.enabled = true
      viewer.scene.globe.maximumScreenSpaceError = 2
      viewer.scene.globe.tileCacheSize = 1000
      viewer.scene.globe.baseColor = Color.fromCssColorString('#0b1d33')
      viewer.scene.globe.depthTestAgainstTerrain = true
      viewer.scene.globe.translucency.enabled = false
      viewer.scene.globe.showGroundAtmosphere = true
      if (viewer.scene.skyAtmosphere) {
        viewer.scene.skyAtmosphere.show = true
      }
      viewer.scene.fog.enabled = true
      viewer.scene.fog.density = 0.00005
      viewer.scene.globe.enableLighting = true

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

      const introDestination = Cartesian3.fromDegrees(-20, -6, 31000000)
      viewer.camera.setView({
        destination: introDestination,
        orientation: {
          heading: initialCameraOrientation.heading,
          pitch: initialCameraOrientation.pitch,
          roll: initialCameraOrientation.roll,
        },
      })

      viewer.camera.flyTo({
        destination: initialCameraDestination,
        orientation: {
          heading: initialCameraOrientation.heading,
          pitch: initialCameraOrientation.pitch,
          roll: initialCameraOrientation.roll,
        },
        duration: 1.1,
      })

      const updateCompass = () => {
        const headingDeg = ((CesiumMath.toDegrees(viewer.camera.heading) % 360) + 360) % 360
        const pitchDeg = CesiumMath.toDegrees(viewer.camera.pitch)
        const altitudeKm = viewer.camera.positionCartographic.height / 1000

        onCompassChange({ headingDeg, pitchDeg, altitudeKm })
      }

      viewer.camera.percentageChanged = 0.0005
      updateCompass()
      viewer.camera.changed.addEventListener(updateCompass)

      return () => {
        viewer.camera.changed.removeEventListener(updateCompass)
      }
    }, [onCompassChange, viewerReadyTick])

    useEffect(() => {
      const viewer = viewerRef.current?.cesiumElement
      if (!viewer) {
        return
      }

      const rotateEarth = () => {
        if (Date.now() < pauseAutoRotateUntilRef.current) {
          return
        }

        if (autoRotateRef.current) {
          viewer.scene.camera.rotateRight(earthRotationSpeed)
        }
      }

      viewer.clock.onTick.addEventListener(rotateEarth)

      return () => {
        viewer.clock.onTick.removeEventListener(rotateEarth)
      }
    }, [viewerReadyTick])

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

      collection.removeAll()

      const starlinkColor = groupColorMap.starlink

      for (const satellite of starlinkSatellites) {
        const entityId = `starlink-${satellite.satelliteId}`
        const isSelected = selectedEntityId === entityId

        collection.add({
          position: Cartesian3.fromDegrees(
            satellite.longitudeDeg,
            satellite.latitudeDeg,
            Math.max(0, satellite.altitudeKm * 1000),
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
      }

      return () => {
        if (starlinkPointsRef.current) {
          starlinkPointsRef.current.removeAll()
        }
      }
    }, [groupColorMap, selectedEntityId, showBackSideSatellites, starlinkSatellites])

    useEffect(() => {
      const viewer = viewerRef.current?.cesiumElement
      if (!viewer) {
        return
      }

      const clickHandler = new ScreenSpaceEventHandler(viewer.scene.canvas)

      clickHandler.setInputAction((event: { position: unknown }) => {
        const picked = viewer.scene.pick(event.position as Parameters<typeof viewer.scene.pick>[0])
        if (!defined(picked)) {
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
      >
        {selectedStarlinkSatellite ? (
          <Entity
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

        {visibleEntitySatellites.map(({ group, satellite }) => {
          const altitudeMeters = Math.max(0, satellite.altitudeKm * 1000)
          const entityId = `${group.key}-${satellite.satelliteId}`
          const isSelected = selectedEntityId === entityId

          const pulsePixelSize = new CallbackProperty((time) => {
            const elapsed = time?.secondsOfDay ?? 0
            return 16 + Math.sin(elapsed * 4) * 3
          }, false)

          const pulseAlpha = new CallbackProperty((time) => {
            const elapsed = time?.secondsOfDay ?? 0
            return 0.18 + (Math.sin(elapsed * 4) + 1) * 0.12
          }, false)

          const pulseColor = new CallbackProperty((time) => {
            const alpha = pulseAlpha.getValue(time) as number
            return Color.fromAlpha(Color.WHITE, alpha)
          }, false)

          return (
            <Fragment key={entityId}>
              <Entity
                id={entityId}
                name={satellite.satelliteName}
                description={`${group.label} | NORAD ${satellite.noradCatId}`}
                position={Cartesian3.fromDegrees(
                  satellite.longitudeDeg,
                  satellite.latitudeDeg,
                  altitudeMeters,
                )}
                point={{
                  pixelSize: isSelected ? 13 : 7,
                  color: groupColorMap[group.key],
                  outlineColor: Color.WHITE,
                  outlineWidth: isSelected ? 3 : 1,
                  disableDepthTestDistance: showBackSideSatellites ? Number.POSITIVE_INFINITY : 0,
                }}
              />
              {isSelected ? (
                <Entity
                  key={`${entityId}-pulse`}
                  position={Cartesian3.fromDegrees(
                    satellite.longitudeDeg,
                    satellite.latitudeDeg,
                    altitudeMeters,
                  )}
                  point={{
                    pixelSize: pulsePixelSize,
                    color: pulseColor,
                    outlineColor: Color.WHITE,
                    outlineWidth: 1,
                    disableDepthTestDistance: showBackSideSatellites ? Number.POSITIVE_INFINITY : 0,
                  }}
                />
              ) : null}
            </Fragment>
          )
        })}
      </Viewer>
    )
  },
)

SatelliteGlobe.displayName = 'SatelliteGlobe'
