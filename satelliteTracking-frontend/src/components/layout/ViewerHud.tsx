import { computeMoonPosition } from '../Moon'
import type { SelectedSatelliteState } from '../../helpers/searchHelpers'

type SatelliteDetailsHudProps = {
  selectedSatellite: SelectedSatelliteState
  reportingSighting: boolean
  sightingCity: string
  locatingBrowser: boolean
  sightingLatitude: number | null
  sightingLongitude: number | null
  sightingInfo: string
  sightingsError: string
  onReportSighting: () => void
  onClose: () => void
  onSightingCityChange: (value: string) => void
  onUseBrowserLocation: () => void
}

export function SatelliteDetailsHud({
  selectedSatellite,
  reportingSighting,
  sightingCity,
  locatingBrowser,
  sightingLatitude,
  sightingLongitude,
  sightingInfo,
  sightingsError,
  onReportSighting,
  onClose,
  onSightingCityChange,
  onUseBrowserLocation,
}: SatelliteDetailsHudProps) {
  return (
    <aside className="viewer-hud">
      <section className="details-card hud-details">
        <h3>Dettagli satellite</h3>
        <div className="details-head">
          <strong>{selectedSatellite.satellite.satelliteName}</strong>
          <div className="details-head-actions">
            <button
              type="button"
              className="primary-sighting-button"
              onClick={onReportSighting}
              disabled={reportingSighting}
            >
              {reportingSighting ? 'Invio...' : 'Invia avvistamento'}
            </button>
            <button type="button" onClick={onClose}>Chiudi</button>
          </div>
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
        <div className="sighting-actions">
          <div className="sighting-location-row">
            <input
              type="text"
              className="sighting-city-input"
              value={sightingCity}
              onChange={(event) => onSightingCityChange(event.target.value)}
              placeholder="Inserisci citta (alternativa al browser)"
            />
            <button
              type="button"
              className="sighting-pin-button"
              onClick={onUseBrowserLocation}
              disabled={locatingBrowser}
              title="Usa posizione browser"
              aria-label="Usa posizione browser"
            >
              {locatingBrowser ? (
                '...'
              ) : (
                <svg viewBox="0 0 24 24" aria-hidden="true" focusable="false">
                  <path d="M12 2C8.14 2 5 5.14 5 9c0 5.08 6.13 12.31 6.39 12.62a.8.8 0 0 0 1.22 0C12.87 21.31 19 14.08 19 9c0-3.86-3.14-7-7-7Zm0 9.5A2.5 2.5 0 1 1 12 6.5a2.5 2.5 0 0 1 0 5Z" />
                </svg>
              )}
            </button>
          </div>
          {sightingLatitude !== null && sightingLongitude !== null ? (
            <small className="sighting-coords">
              Posizione browser: {sightingLatitude.toFixed(4)}, {sightingLongitude.toFixed(4)}
            </small>
          ) : null}
          {sightingInfo ? <small className="sighting-coords">{sightingInfo}</small> : null}
          {sightingsError ? <small className="sighting-error-inline">{sightingsError}</small> : null}
          <button type="button" onClick={onReportSighting} disabled={reportingSighting}>
            {reportingSighting ? 'Invio avvistamento...' : 'Conferma avvistamento'}
          </button>
        </div>
      </section>
    </aside>
  )
}

export function MoonDetailsHud({ onClose }: { onClose: () => void }) {
  const moonPosition = computeMoonPosition()

  return (
    <aside className="viewer-hud">
      <section className="details-card hud-details">
        <h3>Dettagli Luna</h3>
        <div className="details-head">
          <strong>Moon</strong>
          <div className="details-head-actions">
            <button type="button" onClick={onClose}>Chiudi</button>
          </div>
        </div>
        <div className="details-grid">
          <span>Nome</span>
          <span>Moon</span>
          <span>Fase</span>
          <span>{(moonPosition.phase * 100).toFixed(1)}%</span>
          <span>Lat/Lon</span>
          <span>{moonPosition.lat.toFixed(2)} / {moonPosition.lon.toFixed(2)}</span>
          <span>Distanza</span>
          <span>{(moonPosition.altMeters / 1000).toFixed(0)} km</span>
        </div>
      </section>
    </aside>
  )
}
