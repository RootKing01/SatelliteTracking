import { fetchSatellitePositionsByType } from '../satellitePositionsClient'
import type { SatelliteGroupSource } from './types'

export const scienceGroup: SatelliteGroupSource = {
  key: 'science',
  label: 'Science',
  type: 'science',
  color: '#eaa5ff',
  loadPositions: (signal?: AbortSignal) => fetchSatellitePositionsByType('science', signal),
}
