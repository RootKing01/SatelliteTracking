import { memo } from 'react'
import '../../styles/panels/satellites-panel.css'

type SatellitesPanelProps = {
  autoRotate: boolean
  showBackSideSatellites: boolean
  hasLoadedOnce: boolean
  isRefreshing: boolean
  refreshIntervalMs: number
  refreshProfileLabel: string
  refreshTuningIndex: number
  onZoomIn: () => void
  onZoomOut: () => void
  onGoHome: () => void
  onAlignAxis: () => void
  onToggleAutoRotate: () => void
  onToggleBackSideSatellites: () => void
  onRefreshTuningIndexChange: (value: number) => void
}

function SatellitesPanelBase({
  autoRotate,
  showBackSideSatellites,
  hasLoadedOnce,
  isRefreshing,
  refreshIntervalMs,
  refreshProfileLabel,
  refreshTuningIndex,
  onZoomIn,
  onZoomOut,
  onGoHome,
  onAlignAxis,
  onToggleAutoRotate,
  onToggleBackSideSatellites,
  onRefreshTuningIndexChange,
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
        <div className="refresh-slider-block" aria-label="Profilo refresh live">
          <div className="refresh-slider-head">
            <span>Profilo refresh</span>
            <strong>{refreshProfileLabel}</strong>
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
              onRefreshTuningIndexChange(Math.max(0, Math.min(2, parsed)))
            }}
          />
          <div className="refresh-slider-scale" aria-hidden="true">
            <span>Aggressivo</span>
            <span>Bilanciato</span>
            <span>Stabile</span>
          </div>
        </div>
      </section>
    </section>
  )
}

export const SatellitesPanel = memo(SatellitesPanelBase)
