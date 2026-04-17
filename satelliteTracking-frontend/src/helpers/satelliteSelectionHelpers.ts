import type { SatelliteGroupKey, SatelliteGroupSource } from '../api/groups/types'
import type { SatellitePosition } from '../types/satellite'

export type SelectedSatelliteState = {
  groupLabel: string
  groupKey: SatelliteGroupKey
  satellite: SatellitePosition
}

export function buildSatelliteLookupByEntityId(
  allGroups: readonly SatelliteGroupSource[],
  groupPositions: Partial<Record<SatelliteGroupKey, SatellitePosition[]>>,
) {
  const map = new Map<string, SelectedSatelliteState>()

  for (const group of allGroups) {
    for (const satellite of groupPositions[group.key] ?? []) {
      map.set(`${group.key}-${satellite.satelliteId}`, {
        groupLabel: group.label,
        groupKey: group.key,
        satellite,
      })
    }
  }

  return map
}

export function buildLiveEntityIdBySatelliteId(
  allGroups: readonly SatelliteGroupSource[],
  groupPositions: Partial<Record<SatelliteGroupKey, SatellitePosition[]>>,
) {
  const map = new Map<number, string>()

  for (const group of allGroups) {
    for (const satellite of groupPositions[group.key] ?? []) {
      map.set(satellite.satelliteId, `${group.key}-${satellite.satelliteId}`)
    }
  }

  return map
}
