import { fetchSatellitePositionsByType } from '../satellitePositionsClient'
import type { SatelliteGroupSource } from './types'

export const onewebGroup: SatelliteGroupSource = {
  key: 'oneweb',
  label: 'OneWeb',
  type: 'oneweb',
  color: '#6ca8ff',
  loadPositions: (signal?: AbortSignal) => fetchSatellitePositionsByType('oneweb', signal),
}
