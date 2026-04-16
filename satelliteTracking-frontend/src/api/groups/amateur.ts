import { fetchSatellitePositionsByType } from '../satellitePositionsClient'
import type { SatelliteGroupSource } from './types'

export const amateurGroup: SatelliteGroupSource = {
  key: 'amateur',
  label: 'Amateur',
  type: 'amateur',
  color: '#b4f5ff',
  loadPositions: (signal?: AbortSignal) => fetchSatellitePositionsByType('amateur', signal),
}
