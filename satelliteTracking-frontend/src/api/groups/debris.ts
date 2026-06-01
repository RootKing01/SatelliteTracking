import { fetchSatellitePositionsByType } from '../satellitePositionsClient'
import type { SatelliteGroupSource } from './types'

export const debrisGroup: SatelliteGroupSource = {
  key: 'debris',
  label: 'Debris',
  type: 'debris',
  color: '#ff6b6b',
  loadPositions: (signal?: AbortSignal) => fetchSatellitePositionsByType('debris', signal),
}
