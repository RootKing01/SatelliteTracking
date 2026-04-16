import { fetchSatellitePositionsByType } from '../satellitePositionsClient'
import type { SatelliteGroupSource } from './types'

export const weatherGroup: SatelliteGroupSource = {
  key: 'weather',
  label: 'Weather',
  type: 'weather',
  color: '#ffd86b',
  loadPositions: (signal?: AbortSignal) => fetchSatellitePositionsByType('weather', signal),
}
