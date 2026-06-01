import { httpClient } from '../httpClient'

export type SatelliteGroupsStatsResponse = {
  stats: Record<string, number>
  total: number
}

export async function fetchSatelliteGroupsStats(signal?: AbortSignal): Promise<SatelliteGroupsStatsResponse> {
  const response = await httpClient.get<SatelliteGroupsStatsResponse>('/api/satellites/groups-stats', {
    signal,
  })

  return response.data
}