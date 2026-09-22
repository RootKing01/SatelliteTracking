export interface SatellitePosition {
  satelliteId: number
  satelliteName: string
  satelliteType: string | null
  objectId: string
  noradCatId: number
  calculatedAtUtc: string
  latitudeDeg: number
  longitudeDeg: number
  altitudeKm: number
  distanceFromEarthCenterKm: number
  meanMotion: number
  orbitalPeriodMinutes: number
  orbitalPeriodHours: number
  velocityKmh?: number
  directionDeg?: number
}
