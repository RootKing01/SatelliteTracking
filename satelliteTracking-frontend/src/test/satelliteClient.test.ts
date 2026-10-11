import { afterEach, describe, expect, it, vi } from 'vitest'
import { httpClient } from '../api/httpClient'
import {
  fetchSatellitePositionsByType,
  fetchVisibleUpcomingPasses,
} from '../api/satelliteClient'

describe('Satellite API Client', () => {
  afterEach(() => {
    vi.restoreAllMocks()
  })

  it('fetches and normalizes satellite positions from the API', async () => {
    const responseData = [
      {
        satelliteId: '25544',
        satelliteName: 'ISS (ZARYA)',
        satelliteType: 'stations',
        objectId: '1998-067A',
        noradCatId: '25544',
        calculatedAtUtc: '2026-04-17T12:00:00Z',
        latitudeDeg: '41.5',
        longitudeDeg: '14.3',
        altitudeKm: '400',
        distanceFromEarthCenterKm: '6771',
        meanMotion: '15.5',
        orbitalPeriodMinutes: '92.9',
        orbitalPeriodHours: '1.55',
      },
      {
        satelliteId: '39444',
        satelliteName: 'INVALID',
        satelliteType: 'starlink',
        objectId: '2013-001A',
        noradCatId: '39444',
        calculatedAtUtc: '2026-04-17T12:00:00Z',
        latitudeDeg: 'not-a-number',
        longitudeDeg: '10.5',
        altitudeKm: '550',
        distanceFromEarthCenterKm: '6921',
        meanMotion: '15.1',
        orbitalPeriodMinutes: '95.3',
        orbitalPeriodHours: '1.59',
      }
    ]
    const getSpy = vi.spyOn(httpClient, 'get').mockResolvedValue({ data: responseData } as never)

    const positions = await fetchSatellitePositionsByType('stations')

    expect(positions).toHaveLength(1)
    expect(positions[0].satelliteName).toBe('ISS (ZARYA)')
    expect(positions[0].satelliteId).toBe(25544)
    expect(positions[0].latitudeDeg).toBe(41.5)
    expect(getSpy).toHaveBeenCalledWith('/api/satellites/positions', {
      params: { type: 'stations' },
      signal: undefined,
    })
  })

  it('fetches upcoming passes with the requested filters', async () => {
    const responseData = [
      {
        satelliteId: 25544,
        satelliteName: 'ISS (ZARYA)',
        riseTime: '2026-04-17T19:30:00Z',
        maxElevationTime: '2026-04-17T19:35:00Z',
        setTime: '2026-04-17T19:40:00Z',
        riseAzimuth: 310.0,
        maxElevationAzimuth: 180.0,
        setAzimuth: 50.0,
        maxElevation: 85.0,
        maxDistance: 1200,
        isVisible: true,
        isSunlit: true,
        visibility: 'excellent',
        observingCondition: 'night',
        estimatedMagnitude: 1.2,
        satelliteAltitudeKm: 400,
      }
    ]
    const getSpy = vi.spyOn(httpClient, 'get').mockResolvedValue({ data: responseData } as never)

    const passes = await fetchVisibleUpcomingPasses({
      hours: 6,
      minElevation: 30,
      observingCondition: 'night',
      maxMagnitude: 4,
    })

    expect(passes).toEqual(responseData)
    expect(getSpy).toHaveBeenCalledWith('/api/satellites/upcoming-passes/filtered', {
      timeout: 60000,
      params: {
        hours: 6,
        minElevation: 30,
        observingCondition: 'night',
        maxMagnitude: 4,
        latitude: undefined,
        longitude: undefined,
        altitude: undefined,
      },
    })
  })
})
