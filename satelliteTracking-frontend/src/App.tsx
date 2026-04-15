import { useEffect, useMemo, useRef } from 'react'
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
import './App.css'

const ionToken =
  import.meta.env.VITE_CESIUM_TOKEN ?? import.meta.env.VITE_CESIUM_ION_TOKEN

if (ionToken) {
  Ion.defaultAccessToken = ionToken
}

const rome = Cartesian3.fromDegrees(12.4964, 41.9028, 250000)
const madrid = Cartesian3.fromDegrees(-3.7038, 40.4168, 350000)

function App() {
  const viewerRef = useRef<CesiumComponentRef<CesiumViewer>>(null)
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
        <p>
          Vista bilanciata: dettagli buoni, atmosfera più morbida e controlli zoom immediati.
        </p>
        <div className="toolbar">
          <button type="button" onClick={zoomIn}>Zoom +</button>
          <button type="button" onClick={zoomOut}>Zoom -</button>
        </div>
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
        <Entity
          name="Demo Satellite 1"
          position={rome}
          point={{ pixelSize: 10, color: Color.CYAN }}
        />
        <Entity
          name="Demo Satellite 2"
          position={madrid}
          point={{ pixelSize: 10, color: Color.YELLOW }}
        />
      </Viewer>
    </main>
  )
}

export default App
