import { fetchSatellitePositionsByType } from '../satelliteClient'
import type { SatelliteGroupSource } from './types'

type SatelliteGroupDefinition = Omit<SatelliteGroupSource, 'loadPositions'>
export const STARLINK_GROUP_KEY = 'starlink' as const

const satelliteGroupDefinitions = [
  { key: 'stations', label: 'Stations', type: 'stations', color: '#63d6ff' },
  { key: STARLINK_GROUP_KEY, label: 'Starlink', type: STARLINK_GROUP_KEY, color: '#ff8f67' },
  { key: 'oneweb', label: 'OneWeb', type: 'oneweb', color: '#6ca8ff' },
  { key: 'iridiumNext', label: 'Iridium NEXT', type: 'iridium-NEXT', color: '#89ffe2' },
  { key: 'spire', label: 'Spire', type: 'spire', color: '#7effac' },
  { key: 'gpsOps', label: 'GPS Ops', type: 'gps-ops', color: '#c8ff78' },
  { key: 'galileo', label: 'Galileo', type: 'galileo', color: '#d7ff68' },
  { key: 'glonassOps', label: 'GLONASS Ops', type: 'glonass-ops', color: '#b7f49a' },
  { key: 'beidou', label: 'BeiDou', type: 'beidou', color: '#ffd66c' },
  { key: 'sbas', label: 'SBAS', type: 'sbas', color: '#ffe18d' },
  { key: 'science', label: 'Science', type: 'science', color: '#eaa5ff' },
  { key: 'weather', label: 'Weather', type: 'weather', color: '#ffd86b' },
  { key: 'planet', label: 'Planet', type: 'planet', color: '#9ec7ff' },
  { key: 'radar', label: 'Radar', type: 'radar', color: '#f0bbff' },
  { key: 'geo', label: 'GEO', type: 'geo', color: '#ffd195' },
  { key: 'amateur', label: 'Amateur', type: 'amateur', color: '#b4f5ff' },
  { key: 'cubesat', label: 'CubeSat', type: 'cubesat', color: '#97ffc7' },
  { key: 'education', label: 'Education', type: 'education', color: '#9fd7ff' },
  { key: 'engineering', label: 'Engineering', type: 'engineering', color: '#ffb897' },
  { key: 'military', label: 'Military', type: 'military', color: '#ff8ea1' },
  { key: 'spaceMissions', label: 'Space Missions', type: 'space-missions', color: '#ffd166' },
  { key: 'debris', label: 'Debris', type: 'debris', color: '#ff6b6b' },
  { key: 'payload', label: 'Payload', type: 'payload', color: '#8bd3a7' },
  { key: 'spaceRocket', label: 'Space Rocket', type: 'space-rocket', color: '#ffb86b' },
] as const satisfies readonly SatelliteGroupDefinition[]

function createSatelliteGroup(definition: SatelliteGroupDefinition): SatelliteGroupSource {
  return {
    ...definition,
    loadPositions: (signal?: AbortSignal) => fetchSatellitePositionsByType(definition.type, signal),
  }
}

export const canonicalGroupAliases: Record<string, string> = Object.fromEntries(
  satelliteGroupDefinitions
    .filter((definition) => definition.key !== definition.type)
    .map((definition) => [definition.type, definition.key]),
)

export const satelliteGroupSources = satelliteGroupDefinitions.map(createSatelliteGroup)
