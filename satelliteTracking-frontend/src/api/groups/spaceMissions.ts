import { fetchSatellitePositionsByType } from '../satellitePositionsClient'
import type { SatelliteGroupSource } from './types'

export const spaceMissionsGroup: SatelliteGroupSource = {
  key: 'spaceMissions',
  label: 'Space Missions',
  type: 'space-missions',
  color: '#ffd166',
  loadPositions: (signal?: AbortSignal) => fetchSatellitePositionsByType('space-missions', signal),
}
