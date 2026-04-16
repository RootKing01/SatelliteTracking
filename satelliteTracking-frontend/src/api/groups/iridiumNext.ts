import { fetchSatellitePositionsByType } from '../satellitePositionsClient'
import type { SatelliteGroupSource } from './types'

export const iridiumNextGroup: SatelliteGroupSource = {
  key: 'iridiumNext',
  label: 'Iridium NEXT',
  type: 'iridium-NEXT',
  color: '#89ffe2',
  loadPositions: (signal?: AbortSignal) => fetchSatellitePositionsByType('iridium-NEXT', signal),
}
