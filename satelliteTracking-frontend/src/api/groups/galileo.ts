import { fetchSatellitePositionsByType } from '../satelliteClient'
import type { SatelliteGroupSource } from './types'

export const galileoGroup: SatelliteGroupSource = {
  key: 'galileo',
  label: 'Galileo',
  type: 'galileo',
  color: '#d7ff68',
  loadPositions: (signal?: AbortSignal) => fetchSatellitePositionsByType('galileo', signal),
}
