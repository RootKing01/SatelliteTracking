import type { SatellitePosition } from '../../types/satellite'

export type SatelliteGroupKey = 'stations' | 'starlink' | 'gpsOps' | 'weather'

export interface SatelliteGroupSource {
  key: SatelliteGroupKey
  label: string
  type: string
  color: string
  loadPositions: (signal?: AbortSignal) => Promise<SatellitePosition[]>
}
