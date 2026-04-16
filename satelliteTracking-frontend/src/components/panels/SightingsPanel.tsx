import type { SatelliteSighting } from '../../api/sightingsClient'
import '../../styles/panels/sightings-panel.css'

type SightingsPanelProps = {
  sightingInfo: string
  sightingsError: string
  sightingsLoading: boolean
  mySightings: SatelliteSighting[]
}

export function SightingsPanel({
  sightingInfo,
  sightingsError,
  sightingsLoading,
  mySightings,
}: SightingsPanelProps) {
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
          {mySightings.map((item) => (
            <article key={item.id} className="sighting-item">
              <strong>{item.satelliteName}</strong>
              <small>NORAD {item.noradCatId}</small>
              <small>{new Date(item.sightedAt).toLocaleString('it-IT')}</small>
              <small>{item.observerLocationName}</small>
              <small className={item.valid ? 'sighting-valid' : 'sighting-invalid'}>
                {item.valid ? 'Valido' : 'Non valido'}
              </small>
              {item.estimatedMagnitude !== null ? (
                <small>Magnitudine stimata: {item.estimatedMagnitude.toFixed(1)}</small>
              ) : null}
              {item.maxElevationDeg !== null ? (
                <small>Elevazione max: {item.maxElevationDeg.toFixed(1)}deg</small>
              ) : null}
              <small>{item.validationMessage}</small>
            </article>
          ))}
        </div>
      )}
    </section>
  )
}
