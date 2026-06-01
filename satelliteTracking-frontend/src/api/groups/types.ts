import type { SatellitePosition } from '../../types/satellite'

export type SatelliteGroupKey = string

export interface SatelliteGroupSource {
  key: SatelliteGroupKey
  label: string
  type: string
  color: string
  loadPositions: (signal?: AbortSignal) => Promise<SatellitePosition[]>
}
