import { fetchSatellitePositionsByType } from '../satellitePositionsClient'
import type { SatelliteGroupSource } from './types'

export const planetGroup: SatelliteGroupSource = {
  key: 'planet',
  label: 'Planet',
  type: 'planet',
  color: '#9ec7ff',
  loadPositions: (signal?: AbortSignal) => fetchSatellitePositionsByType('planet', signal),
}
