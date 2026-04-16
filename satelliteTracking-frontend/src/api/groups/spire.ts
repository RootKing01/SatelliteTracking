import { fetchSatellitePositionsByType } from '../satellitePositionsClient'
import type { SatelliteGroupSource } from './types'

export const spireGroup: SatelliteGroupSource = {
  key: 'spire',
  label: 'Spire',
  type: 'spire',
  color: '#7effac',
  loadPositions: (signal?: AbortSignal) => fetchSatellitePositionsByType('spire', signal),
}
