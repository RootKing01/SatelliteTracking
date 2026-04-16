import { forwardRef, useEffect, useImperativeHandle, useMemo, useRef, useState } from 'react'
import {
  Cartesian3,
  Color,
  EllipsoidTerrainProvider,
  HeadingPitchRoll,
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
    const [viewerReadyTick, setViewerReadyTick] = useState(0)
    const terrainProvider = useMemo(() => new EllipsoidTerrainProvider(), [])
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
    }))

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
          outlineWidth: isSelected ? 2 : 0,
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
        {visibleEntitySatellites.map(({ group, satellite }) => {
          const altitudeMeters = Math.max(0, satellite.altitudeKm * 1000)
          const entityId = `${group.key}-${satellite.satelliteId}`
          const isSelected = selectedEntityId === entityId

          return (
            <Entity
              key={entityId}
              id={entityId}
              name={satellite.satelliteName}
              description={`${group.label} | NORAD ${satellite.noradCatId}`}
              position={Cartesian3.fromDegrees(
                satellite.longitudeDeg,
                satellite.latitudeDeg,
                altitudeMeters,
              )}
              point={{
                pixelSize: isSelected ? 10 : 7,
                color: groupColorMap[group.key],
                outlineColor: Color.WHITE,
                outlineWidth: isSelected ? 2 : 1,
                disableDepthTestDistance: showBackSideSatellites ? Number.POSITIVE_INFINITY : 0,
              }}
            />
          )
        })}
      </Viewer>
    )
  },
)

SatelliteGlobe.displayName = 'SatelliteGlobe'
