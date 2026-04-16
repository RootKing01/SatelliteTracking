import { fetchSatellitePositionsByType } from '../satellitePositionsClient'
import type { SatelliteGroupSource } from './types'

export const engineeringGroup: SatelliteGroupSource = {
  key: 'engineering',
  label: 'Engineering',
  type: 'engineering',
  color: '#ffb897',
  loadPositions: (signal?: AbortSignal) => fetchSatellitePositionsByType('engineering', signal),
}
