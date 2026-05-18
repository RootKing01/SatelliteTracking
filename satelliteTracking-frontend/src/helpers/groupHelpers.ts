import type { SatelliteGroupKey, SatelliteGroupSource } from '../api/groups/types'

export type GroupPreset = 'custom' | 'all' | 'stations' | 'navigation' | 'leo'

export function createDefaultEnabledGroups(groups: readonly SatelliteGroupSource[]) {
  return Object.fromEntries(
    groups.map((group) => [group.key, group.key === 'stations' || group.key === 'spaceMissions']),
  ) as Record<SatelliteGroupKey, boolean>
}

export function buildEnabledGroupsFromPreset(
  groups: readonly SatelliteGroupSource[],
  preset: GroupPreset,
) {
  if (preset === 'custom') {
    return null
  }

  const navigationKeys = new Set<SatelliteGroupKey>(['gpsOps', 'galileo', 'glonassOps', 'beidou', 'sbas'])
  const leoKeys = new Set<SatelliteGroupKey>(['starlink', 'oneweb', 'iridiumNext', 'planet', 'spire', 'cubesat', 'spaceMissions'])

  return Object.fromEntries(
    groups.map((group) => {
      if (preset === 'all') {
        return [group.key, true]
      }
      if (preset === 'stations') {
        return [group.key, group.key === 'stations']
      }
      if (preset === 'navigation') {
        return [group.key, navigationKeys.has(group.key)]
      }
      return [group.key, leoKeys.has(group.key)]
    }),
  ) as Record<SatelliteGroupKey, boolean>
}
