import { fetchSatellitePositionsByType } from '../satellitePositionsClient'
import type { SatelliteGroupSource } from './types'

export const payloadGroup: SatelliteGroupSource = {
  key: 'payload',
  label: 'Payload',
  type: 'payload',
  color: '#8bd3a7',
  loadPositions: (signal?: AbortSignal) => fetchSatellitePositionsByType('payload', signal),
}
