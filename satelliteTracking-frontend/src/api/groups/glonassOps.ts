import { fetchSatellitePositionsByType } from '../satellitePositionsClient'
import type { SatelliteGroupSource } from './types'

export const glonassOpsGroup: SatelliteGroupSource = {
  key: 'glonassOps',
  label: 'GLONASS Ops',
  type: 'glonass-ops',
  color: '#b7f49a',
  loadPositions: (signal?: AbortSignal) => fetchSatellitePositionsByType('glonass-ops', signal),
}
