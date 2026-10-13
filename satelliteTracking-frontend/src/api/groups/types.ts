import type { SatellitePosition } from '../../types/satellite'

export type SatelliteGroupKey = string
export type GroupPositionsState = Partial<Record<SatelliteGroupKey, SatellitePosition[]>>
export type GroupLoadingState = Partial<Record<SatelliteGroupKey, boolean>>
export type GroupErrorState = Partial<Record<SatelliteGroupKey, string>>

export interface SatelliteGroupSource {
  key: SatelliteGroupKey
  label: string
  type: string
  color: string
  loadPositions: (signal?: AbortSignal) => Promise<SatellitePosition[]>
}
