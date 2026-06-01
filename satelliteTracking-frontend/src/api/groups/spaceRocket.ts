import { fetchSatellitePositionsByType } from '../satellitePositionsClient'
import type { SatelliteGroupSource } from './types'

export const spaceRocketGroup: SatelliteGroupSource = {
  key: 'spaceRocket',
  label: 'Space Rocket',
  type: 'space-rocket',
  color: '#ffb86b',
  loadPositions: (signal?: AbortSignal) => fetchSatellitePositionsByType('space-rocket', signal),
}
