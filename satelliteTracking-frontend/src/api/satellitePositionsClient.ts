import axios from 'axios'
import type { SatellitePosition } from '../types/satellite'

const configuredApiBaseUrl = import.meta.env.VITE_API_BASE_URL?.trim()

const isRemoteClient =
  typeof window !== 'undefined' &&
  !['localhost', '127.0.0.1'].includes(window.location.hostname)

const apiBaseUrl =
  configuredApiBaseUrl && !(isRemoteClient && configuredApiBaseUrl.includes('localhost'))
    ? configuredApiBaseUrl
    : ''

const apiClient = axios.create({
  baseURL: apiBaseUrl,
  timeout: 15000,
})

export async function fetchSatellitePositionsByType(
  type: string,
  signal?: AbortSignal,
): Promise<SatellitePosition[]> {
  const response = await apiClient.get<SatellitePosition[]>('/api/satellites/positions', {
    params: { type },
    signal,
  })

  return response.data
}
