import { fetchSatellitePositionsByType } from '../satellitePositionsClient'
import type { SatelliteGroupSource } from './types'

export const militaryGroup: SatelliteGroupSource = {
  key: 'military',
  label: 'Military',
  type: 'military',
  color: '#ff8ea1',
  loadPositions: (signal?: AbortSignal) => fetchSatellitePositionsByType('military', signal),
}
