import { fetchSatellitePositionsByType } from '../satellitePositionsClient'
import type { SatelliteGroupSource } from './types'

export const beidouGroup: SatelliteGroupSource = {
  key: 'beidou',
  label: 'BeiDou',
  type: 'beidou',
  color: '#ffd66c',
  loadPositions: (signal?: AbortSignal) => fetchSatellitePositionsByType('beidou', signal),
}
