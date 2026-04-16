import { fetchSatellitePositionsByType } from '../satellitePositionsClient'
import type { SatelliteGroupSource } from './types'

export const stationsGroup: SatelliteGroupSource = {
  key: 'stations',
  label: 'Stations',
  type: 'stations',
  color: '#63d6ff',
  loadPositions: (signal?: AbortSignal) => fetchSatellitePositionsByType('stations', signal),
}
