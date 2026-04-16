import { fetchSatellitePositionsByType } from '../satellitePositionsClient'
import type { SatelliteGroupSource } from './types'

export const gpsOpsGroup: SatelliteGroupSource = {
  key: 'gpsOps',
  label: 'GPS Ops',
  type: 'gps-ops',
  color: '#c8ff78',
  loadPositions: (signal?: AbortSignal) => fetchSatellitePositionsByType('gps-ops', signal),
}
