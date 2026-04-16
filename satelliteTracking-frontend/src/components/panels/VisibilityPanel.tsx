import type { UpcomingPass } from '../../api/satelliteVisibilityClient'
import '../../styles/panels/visibility-panel.css'

type VisibilityPanelProps = {
  visibilityHours: number
  visibilityMinElevation: number
  visibilityCity: string
  visibilityLocatingBrowser: boolean
  visibilityLoading: boolean
  visibilityLatitude: number | null
  visibilityLongitude: number | null
  visibilityInfo: string
  visibilityError: string
  visibilityResults: UpcomingPass[]
  visibilityResultsTotal: number
  onVisibilityHoursChange: (value: number) => void
  onVisibilityMinElevationChange: (value: number) => void
  onVisibilityCityChange: (value: string) => void
  onUseBrowserLocation: () => void
  onCalculateVisibility: () => void
  onOpenFullResults: () => void
  onFocusFromVisibility: (pass: UpcomingPass) => void
}

export function VisibilityPanel({
  visibilityHours,
  visibilityMinElevation,
  visibilityCity,
  visibilityLocatingBrowser,
  visibilityLoading,
  visibilityLatitude,
  visibilityLongitude,
  visibilityInfo,
  visibilityError,
  visibilityResults,
  visibilityResultsTotal,
  onVisibilityHoursChange,
  onVisibilityMinElevationChange,
  onVisibilityCityChange,
  onUseBrowserLocation,
  onCalculateVisibility,
  onOpenFullResults,
  onFocusFromVisibility,
}: VisibilityPanelProps) {
  return (
    <section className="collapsible side-drawer" aria-label="Calcolo visibilita satelliti">
      <h3>Visibilita prossime ore</h3>
      <div className="visibility-grid">
        <label>
          Ore
          <input
            type="number"
            min={1}
            max={24}
            value={visibilityHours}
            onChange={(event) =>
              onVisibilityHoursChange(Math.max(1, Math.min(24, Number(event.target.value) || 12)))
            }
          />
        </label>
        <label>
          Elev. min (deg)
          <input
            type="number"
            min={0}
            max={90}
            value={visibilityMinElevation}
            onChange={(event) =>
              onVisibilityMinElevationChange(
                Math.max(0, Math.min(90, Number(event.target.value) || 10)),
              )
            }
          />
        </label>
      </div>

      <label className="visibility-city-row">
        Citta (opzionale, prioritaria)
        <input
          type="text"
          value={visibilityCity}
          onChange={(event) => onVisibilityCityChange(event.target.value)}
          placeholder="Es. Napoli, Roma, Milano"
        />
      </label>

      <div className="visibility-actions">
        <button
          type="button"
          className="sighting-pin-button"
          onClick={onUseBrowserLocation}
          disabled={visibilityLocatingBrowser}
          title="Usa posizione browser"
          aria-label="Usa posizione browser"
        >
          {visibilityLocatingBrowser ? (
            '...'
          ) : (
            <svg viewBox="0 0 24 24" aria-hidden="true" focusable="false">
              <path d="M12 2C8.14 2 5 5.14 5 9c0 5.08 6.13 12.31 6.39 12.62a.8.8 0 0 0 1.22 0C12.87 21.31 19 14.08 19 9c0-3.86-3.14-7-7-7Zm0 9.5A2.5 2.5 0 1 1 12 6.5a2.5 2.5 0 0 1 0 5Z" />
            </svg>
          )}
        </button>
        <button type="button" onClick={onCalculateVisibility}>
          {visibilityLoading ? 'Calcolo...' : 'Calcola visibilita'}
        </button>
      </div>

      {visibilityLatitude !== null && visibilityLongitude !== null ? (
        <small className="visibility-note">
          Posizione browser: {visibilityLatitude.toFixed(4)}, {visibilityLongitude.toFixed(4)}
        </small>
      ) : (
        <small className="visibility-note">
          Posizione default backend (San Marcellino) se non usi il pin.
        </small>
      )}

      {visibilityInfo ? <p className="sighting-info">{visibilityInfo}</p> : null}
      {visibilityError ? <p className="sighting-error">{visibilityError}</p> : null}

      {visibilityResultsTotal > 0 ? (
        <div className="visibility-full-list-actions">
          <small>
            Anteprima: {visibilityResults.length} / Totale: {visibilityResultsTotal}
          </small>
          <button type="button" onClick={onOpenFullResults}>
            Apri lista completa in nuova pagina
          </button>
        </div>
      ) : null}

      {visibilityResults.length > 0 ? (
        <div className="visibility-list">
          {visibilityResults.map((pass) => (
            <article key={`${pass.satelliteId}-${pass.riseTime}-${pass.setTime}`} className="visibility-item">
              <strong>{pass.satelliteName}</strong>
              <small>
                {new Date(pass.riseTime).toLocaleString('it-IT')} {'->'}{' '}
                {new Date(pass.setTime).toLocaleTimeString('it-IT')}
              </small>
              <small>
                Elev. max {pass.maxElevation.toFixed(1)}deg | Mag {pass.estimatedMagnitude.toFixed(1)}
              </small>
              <small>
                Condizione: {pass.observingCondition} | Visibilita: {pass.visibility}
              </small>
              <button type="button" onClick={() => onFocusFromVisibility(pass)}>
                Focus satellite
              </button>
            </article>
          ))}
        </div>
      ) : null}
    </section>
  )
}
