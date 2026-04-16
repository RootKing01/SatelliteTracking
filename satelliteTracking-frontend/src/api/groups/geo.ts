import { fetchSatellitePositionsByType } from '../satellitePositionsClient'
import type { SatelliteGroupSource } from './types'

export const geoGroup: SatelliteGroupSource = {
  key: 'geo',
  label: 'GEO',
  type: 'geo',
  color: '#ffd195',
  loadPositions: (signal?: AbortSignal) => fetchSatellitePositionsByType('geo', signal),
}
