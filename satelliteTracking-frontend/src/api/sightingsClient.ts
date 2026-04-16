import { httpClient } from './httpClient'

export type SatelliteSighting = {
  id: number
  satelliteId: number
  satelliteName: string
  noradCatId: number
  sightedAt: string
  valid: boolean
  validationMessage: string
  estimatedMagnitude: number | null
  maxElevationDeg: number | null
  observerLocationName: string
  observerLatitude: number
  observerLongitude: number
}

export type SightingReportPayload = {
  satelliteId: number
  city?: string
  latitude?: number
  longitude?: number
  altitudeMeters?: number
}

export async function reportSighting(payload: SightingReportPayload): Promise<SatelliteSighting> {
  const response = await httpClient.post<SatelliteSighting>('/api/sightings', payload)
  return response.data
}

export async function fetchMySightings(signal?: AbortSignal): Promise<SatelliteSighting[]> {
  const response = await httpClient.get<SatelliteSighting[]>('/api/sightings/mine', { signal })
  return response.data
}
