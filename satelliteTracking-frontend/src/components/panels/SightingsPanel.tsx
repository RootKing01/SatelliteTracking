import { useEffect, useRef } from 'react'
import type { SatelliteSighting } from '../../api/sightingsClient'
import '../../styles/panels/sightings-panel.css'

type SightingsPanelProps = {
  sightingInfo: string
  sightingsError: string
  sightingsLoading: boolean
  mySightings: SatelliteSighting[]
  compactLandscapeViewport: boolean
  onFocusSightingSatellite: (satelliteId: number) => void
}

export function SightingsPanel({
  sightingInfo,
  sightingsError,
  sightingsLoading,
  mySightings,
  compactLandscapeViewport,
  onFocusSightingSatellite,
}: SightingsPanelProps) {
  const autoFocusedSatelliteIdRef = useRef<number | null>(null)

  useEffect(() => {
    if (!compactLandscapeViewport || mySightings.length === 0) {
      autoFocusedSatelliteIdRef.current = null
      return
    }

    const firstSighting = mySightings[0]
    if (!firstSighting || autoFocusedSatelliteIdRef.current === firstSighting.satelliteId) {
      return
    }

    autoFocusedSatelliteIdRef.current = firstSighting.satelliteId
    onFocusSightingSatellite(firstSighting.satelliteId)
  }, [compactLandscapeViewport, mySightings, onFocusSightingSatellite])

  return (
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
          {mySightings.map((item, index) => (
            <details
              key={item.id}
              className={`sighting-item ${index === 0 ? 'sighting-item-primary' : ''} ${compactLandscapeViewport ? 'sighting-item-compact' : ''}`}
              open={index === 0}
            >
              <summary className={`sighting-item-summary ${compactLandscapeViewport ? 'sighting-item-summary-compact' : ''}`}>
                <strong>{item.satelliteName}</strong>
                <small>{new Date(item.sightedAt).toLocaleString('it-IT')}</small>
                <small className={item.valid ? 'sighting-valid' : 'sighting-invalid'}>
                  {item.valid ? 'Valido' : 'Non valido'}
                </small>
              </summary>

              <div className={`sighting-item-body ${compactLandscapeViewport ? 'sighting-item-body-compact' : ''}`}>
                <small>NORAD {item.noradCatId}</small>
                <small>{item.observerLocationName}</small>
                {item.estimatedMagnitude !== null ? (
                  <small>Magnitudine stimata: {item.estimatedMagnitude.toFixed(1)}</small>
                ) : null}
                {item.maxElevationDeg !== null ? (
                  <small>Elevazione max: {item.maxElevationDeg.toFixed(1)}deg</small>
                ) : null}
                <small>{item.validationMessage}</small>
                <button
                  type="button"
                  className="sighting-focus-button"
                  onClick={() => onFocusSightingSatellite(item.satelliteId)}
                >
                  Focus satellite
                </button>
              </div>
            </details>
          ))}
        </div>
      )}
    </section>
  )
}
