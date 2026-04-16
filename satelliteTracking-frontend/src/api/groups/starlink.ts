import { fetchSatellitePositionsByType } from '../satellitePositionsClient'
import type { SatelliteGroupSource } from './types'

export const starlinkGroup: SatelliteGroupSource = {
  key: 'starlink',
  label: 'Starlink',
  type: 'starlink',
  color: '#ff8f67',
  loadPositions: (signal?: AbortSignal) => fetchSatellitePositionsByType('starlink', signal),
}
