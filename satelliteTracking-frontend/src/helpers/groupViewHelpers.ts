import type { SatelliteGroupKey, SatelliteGroupSource } from '../api/groups/types'
import type { SatellitePosition } from '../types/satellite'

type GroupLoadingState = Partial<Record<SatelliteGroupKey, boolean>>
type GroupErrorState = Partial<Record<SatelliteGroupKey, string>>
type GroupPositionsState = Partial<Record<SatelliteGroupKey, SatellitePosition[]>>

export function buildGroupRows(
  allGroups: readonly SatelliteGroupSource[],
  enabledGroups: Record<SatelliteGroupKey, boolean>,
  groupPositions: GroupPositionsState,
  groupLoading: GroupLoadingState,
  groupErrors: GroupErrorState,
) {
  return allGroups.map((group) => ({
    key: group.key,
    label: group.label,
    color: group.color,
    count: groupPositions[group.key]?.length ?? 0,
    loading: groupLoading[group.key] ?? false,
    error: groupErrors[group.key] ?? '',
    checked: enabledGroups[group.key],
  }))
}
