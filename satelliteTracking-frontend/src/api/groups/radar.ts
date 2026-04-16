import { fetchSatellitePositionsByType } from '../satellitePositionsClient'
import type { SatelliteGroupSource } from './types'

export const radarGroup: SatelliteGroupSource = {
  key: 'radar',
  label: 'Radar',
  type: 'radar',
  color: '#f0bbff',
  loadPositions: (signal?: AbortSignal) => fetchSatellitePositionsByType('radar', signal),
}
