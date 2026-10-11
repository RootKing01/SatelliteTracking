import type { UpcomingPass } from '../../api/satelliteClient'

type VisibilityResultsTableProps = {
  results: UpcomingPass[]
}

export function VisibilityResultsTable({ results }: VisibilityResultsTableProps) {
  return (
    <div className="visibility-overlay-table-wrap">
      <table className="visibility-overlay-table">
        <thead>
          <tr>
            <th>#</th>
            <th>Satellite</th>
            <th>NORAD ID</th>
            <th>Rise</th>
            <th>Set</th>
            <th>Elev. max</th>
            <th>Magnitudine</th>
            <th>Condizione</th>
            <th>Visibilità</th>
          </tr>
        </thead>
        <tbody>
          {results.map((pass, index) => (
            <tr key={`${pass.satelliteId}-${pass.riseTime}-${pass.setTime}-${index}`}>
              <td>{index + 1}</td>
              <td>{pass.satelliteName}</td>
              <td>{pass.satelliteId}</td>
              <td>{new Date(pass.riseTime).toLocaleString('it-IT')}</td>
              <td>{new Date(pass.setTime).toLocaleString('it-IT')}</td>
              <td>{pass.maxElevation.toFixed(1)}deg</td>
              <td>{pass.estimatedMagnitude.toFixed(1)}</td>
              <td>{pass.observingCondition}</td>
              <td>{pass.visibility}</td>
            </tr>
          ))}
        </tbody>
      </table>
    </div>
  )
}
