import type { SatellitePosition } from '../../types/satellite'

export type SatelliteGroupKey =
  | 'stations'
  | 'starlink'
  | 'oneweb'
  | 'iridiumNext'
  | 'spire'
  | 'gpsOps'
  | 'galileo'
  | 'glonassOps'
  | 'beidou'
  | 'sbas'
  | 'science'
  | 'weather'
  | 'planet'
  | 'radar'
  | 'geo'
  | 'amateur'
  | 'cubesat'
  | 'education'
  | 'engineering'
  | 'military'
  | 'spaceMissions'

export interface SatelliteGroupSource {
  key: SatelliteGroupKey
  label: string
  type: string
  color: string
  loadPositions: (signal?: AbortSignal) => Promise<SatellitePosition[]>
}
