import { memo } from 'react'
import '../../styles/panels/satellites-panel.css'

type SatellitesPanelProps = {
  autoRotate: boolean
  showBackSideSatellites: boolean
  showMoon?: boolean
  onToggleShowMoon?: () => void
  hasLoadedOnce: boolean
  isRefreshing: boolean
  refreshIntervalMs: number
  onZoomIn: () => void
  onZoomOut: () => void
  onGoHome: () => void
  onAlignAxis: () => void
  onToggleAutoRotate: () => void
  onToggleBackSideSatellites: () => void
}

function SatellitesPanelBase({
  autoRotate,
  showBackSideSatellites,
  showMoon = true,
  onToggleShowMoon,
  hasLoadedOnce,
  isRefreshing,
  refreshIntervalMs,
  onZoomIn,
  onZoomOut,
  onGoHome,
  onAlignAxis,
  onToggleAutoRotate,
  onToggleBackSideSatellites,
}: SatellitesPanelProps) {
  return (
    <section className="collapsible side-drawer" aria-label="Comandi satelliti">
      <h3>Satelliti</h3>
      <div className="toolbar toolbar-left compact-toolbar">
        <button type="button" onClick={onZoomIn}>Zoom +</button>
        <button type="button" onClick={onZoomOut}>Zoom -</button>
        <button type="button" onClick={onGoHome}>Home</button>
        <button type="button" onClick={onAlignAxis}>Asse N-S</button>
        <button type="button" className={autoRotate ? 'toggle-active' : ''} onClick={onToggleAutoRotate}>
          {autoRotate ? 'Stop rotazione' : 'Avvia rotazione'}
        </button>
        <button
          type="button"
          className={showBackSideSatellites ? 'toggle-active' : ''}
          onClick={onToggleBackSideSatellites}
        >
          {showBackSideSatellites ? 'Nascondi lato opposto' : 'Mostra lato opposto'}
        </button>
        <button
          type="button"
          className={showMoon ? 'toggle-active' : ''}
          onClick={onToggleShowMoon}
        >
          {showMoon ? 'Nascondi Luna' : 'Mostra Luna'}
        </button>
      </div>

      <section className="sync-footer-card" aria-label="Stato sincronizzazione e camera">
        <p className="sync-status">
          <span className={`sync-dot ${hasLoadedOnce ? 'ok blink' : ''} ${isRefreshing ? 'active' : ''}`} />
          Sincronizzazione live attiva
        </p>
        <p className="sync-status">
          <strong>Visibilita:</strong> {showBackSideSatellites ? 'anche lato opposto' : 'solo lato visibile'}
        </p>
        <p className="sync-status">
          <strong>Refresh:</strong> ogni {(refreshIntervalMs / 1000).toFixed(1)}s
        </p>
      </section>
    </section>
  )
}

export const SatellitesPanel = memo(SatellitesPanelBase)
