import { fetchSatellitePositionsByType } from '../satellitePositionsClient'
import type { SatelliteGroupSource } from './types'

export const cubesatGroup: SatelliteGroupSource = {
  key: 'cubesat',
  label: 'CubeSat',
  type: 'cubesat',
  color: '#97ffc7',
  loadPositions: (signal?: AbortSignal) => fetchSatellitePositionsByType('cubesat', signal),
}
