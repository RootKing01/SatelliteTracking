import { httpClient } from './httpClient'
import type { SatellitePosition } from '../types/satellite'

export async function fetchAllSatellitePositions(
  signal?: AbortSignal,
): Promise<SatellitePosition[]> {
  const response = await httpClient.get<SatellitePosition[]>('/api/satellites/positions', {
    signal,
  })

  return response.data
}

export async function fetchSatellitePositionsByType(
  type: string,
  signal?: AbortSignal,
): Promise<SatellitePosition[]> {
  const response = await httpClient.get<SatellitePosition[]>('/api/satellites/positions', {
    params: { type },
    signal,
  })

  return response.data
}

export async function fetchSatellitePositionById(
  satelliteId: number,
  signal?: AbortSignal,
): Promise<SatellitePosition> {
  const response = await httpClient.get<SatellitePosition>(`/api/satellites/${satelliteId}/position`, {
    signal,
  })

  return response.data
}
