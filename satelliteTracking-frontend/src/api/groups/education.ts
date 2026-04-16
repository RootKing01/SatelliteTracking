import { fetchSatellitePositionsByType } from '../satellitePositionsClient'
import type { SatelliteGroupSource } from './types'

export const educationGroup: SatelliteGroupSource = {
  key: 'education',
  label: 'Education',
  type: 'education',
  color: '#9fd7ff',
  loadPositions: (signal?: AbortSignal) => fetchSatellitePositionsByType('education', signal),
}
