import { httpClient } from './httpClient'

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

  const hasCustomLocation =
    typeof query.latitude === 'number' && typeof query.longitude === 'number'

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
