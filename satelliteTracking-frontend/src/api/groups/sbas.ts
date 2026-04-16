import { fetchSatellitePositionsByType } from '../satellitePositionsClient'
import type { SatelliteGroupSource } from './types'

export const sbasGroup: SatelliteGroupSource = {
  key: 'sbas',
  label: 'SBAS',
  type: 'sbas',
  color: '#ffe18d',
  loadPositions: (signal?: AbortSignal) => fetchSatellitePositionsByType('sbas', signal),
}
