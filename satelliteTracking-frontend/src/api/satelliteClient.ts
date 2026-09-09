import { httpClient } from './httpClient'
import type { SatellitePosition } from '../types/satellite'

function normalizeSatellitePosition(position: SatellitePosition): SatellitePosition {
  return {
    ...position,
    satelliteId: Number(position.satelliteId),
    noradCatId: Number(position.noradCatId),
    latitudeDeg: Number(position.latitudeDeg),
    longitudeDeg: Number(position.longitudeDeg),
    altitudeKm: Number(position.altitudeKm),
    distanceFromEarthCenterKm: Number(position.distanceFromEarthCenterKm),
    meanMotion: Number(position.meanMotion),
    orbitalPeriodMinutes: Number(position.orbitalPeriodMinutes),
    orbitalPeriodHours: Number(position.orbitalPeriodHours),
    velocityKmh: position.velocityKmh == null ? undefined : Number(position.velocityKmh),
    directionDeg: position.directionDeg == null ? undefined : Number(position.directionDeg),
  }
}

function normalizeSatellitePositions(positions: SatellitePosition[]): SatellitePosition[] {
  return positions
    .map(normalizeSatellitePosition)
    .filter((position) =>
      Number.isFinite(position.latitudeDeg) &&
      Number.isFinite(position.longitudeDeg) &&
      Number.isFinite(position.altitudeKm),
    )
}

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

export type SatelliteCatalogItem = {
  id: number
  objectName: string
  objectId: string
  noradCatId: number
}

export async function fetchSatelliteById(id: string | number, signal?: AbortSignal): Promise<SatelliteCatalogItem | null> {
  try {
    const response = await httpClient.get<SatelliteCatalogItem>(`/api/satellites/${id}`, { signal })
    return response.data
  } catch (error) {
    console.error('[satelliteClient] fetchSatelliteById error', id, error)
    return null
  }
}

export async function fetchSatelliteCatalogByType(
  type: string,
  signal?: AbortSignal,
): Promise<SatelliteCatalogItem[]> {
  try {
    let url = '/api/satellites'
    const config: { signal?: AbortSignal; params?: { type: string } } = { signal }
    if (type && type.toUpperCase() !== 'ALL') {
      url = '/api/satellites/search-by-type'
      config.params = { type }
    }
    const response = await httpClient.get<SatelliteCatalogItem[]>(url, config)
    console.log('[satelliteClient] response', response)
    return response.data
  } catch (error) {
    console.error('[satelliteClient] fetch catalog error', error)
    throw error
  }
}

export async function fetchSatellitePositionsByType(
  type: string,
  signal?: AbortSignal,
): Promise<SatellitePosition[]> {
  const response = await httpClient.get<SatellitePosition[]>('/api/satellites/positions', {
    params: { type },
    signal,
  })
  return normalizeSatellitePositions(response.data)
}

export async function fetchSatellitePositionById(
  satelliteId: number,
  signal?: AbortSignal,
): Promise<SatellitePosition> {
  const response = await httpClient.get<SatellitePosition>(`/api/satellites/${satelliteId}/position`, {
    signal,
  })
  return normalizeSatellitePosition(response.data)
}

export type UpcomingPass = {
  satelliteId: number
  satelliteName: string
  riseTime: string
  maxElevationTime: string
  setTime: string
  maxElevation: number
  riseAzimuth: number
  maxElevationAzimuth: number
  setAzimuth: number
  maxDistance: number
  isVisible: boolean
  isSunlit: boolean
  visibility: string
  observingCondition: string
  estimatedMagnitude: number
  satelliteAltitudeKm: number
}

export type UpcomingPassQuery = {
  hours: number
  minElevation: number
  observingCondition?: 'any' | 'night' | 'twilight' | 'daylight'
  maxMagnitude?: number
  latitude?: number
  longitude?: number
  altitude?: number
}

export type UpcomingPassesByCityResponse = {
  timestamp: string
  city: {
    name: string
    displayName: string
    latitude: number
    longitude: number
    altitude: number
  }
  query: {
    hours: number
    minElevation: string
    observingCondition: string
    maxMagnitude: number
  }
  totalPasses: number
  passes: UpcomingPass[]
}

export type UpcomingPassesByCityQuery = {
  city: string
  hours: number
  minElevation: number
  observingCondition?: 'any' | 'night' | 'twilight' | 'daylight'
  maxMagnitude?: number
}

export async function fetchVisibleUpcomingPasses(query: UpcomingPassQuery): Promise<UpcomingPass[]> {
  const observingCondition = query.observingCondition ?? 'any'
  const maxMagnitude = query.maxMagnitude ?? 6.0
  const hasCustomLocation = typeof query.latitude === 'number' && typeof query.longitude === 'number'
  const endpoint = hasCustomLocation
    ? '/api/satellites/upcoming-passes/filtered/custom'
    : '/api/satellites/upcoming-passes/filtered'

  const response = await httpClient.get<UpcomingPass[]>(endpoint, {
    timeout: 60000,
    params: {
      hours: query.hours,
      minElevation: query.minElevation,
      observingCondition,
      maxMagnitude,
      latitude: query.latitude,
      longitude: query.longitude,
      altitude: query.altitude,
    },
  })
  return response.data
}

export async function fetchVisibleUpcomingPassesByCity(
  query: UpcomingPassesByCityQuery,
): Promise<UpcomingPassesByCityResponse> {
  const observingCondition = query.observingCondition ?? 'any'
  const maxMagnitude = query.maxMagnitude ?? 6.0
  const response = await httpClient.get<UpcomingPassesByCityResponse>(
    '/api/satellites/upcoming-passes/by-city',
    {
      timeout: 60000,
      params: {
        city: query.city,
        hours: query.hours,
        minElevation: query.minElevation,
        observingCondition,
        maxMagnitude,
      },
    },
  )
  return response.data
}
