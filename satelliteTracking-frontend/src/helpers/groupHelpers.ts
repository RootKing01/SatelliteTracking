import type {
  GroupErrorState,
  GroupLoadingState,
  GroupPositionsState,
  SatelliteGroupKey,
  SatelliteGroupSource,
} from '../api/groups/types'
import { canonicalGroupAliases } from '../api/groups'
import { fetchSatellitePositionsByType } from '../api/satelliteClient'

export type GroupPreset = 'custom' | 'all' | 'stations' | 'navigation' | 'leo'

export type GroupRow = {
  key: SatelliteGroupKey
  label: string
  color: string
  count: number
  loading: boolean
  error: string
  checked: boolean
}

export function createDefaultEnabledGroups(groups: readonly SatelliteGroupSource[]) {
  return Object.fromEntries(
    groups.map((group) => [group.key, group.key === 'stations']),
  ) as Record<SatelliteGroupKey, boolean>
}

function humanizeGroupLabel(key: string): string {
  return key
    .replace(/[-_]+/g, ' ')
    .replace(/([a-z])([A-Z])/g, '$1 $2')
    .replace(/\b\w/g, (match) => match.toUpperCase())
    .trim()
}

function colorFromKey(key: string): string {
  let hash = 0
  for (let index = 0; index < key.length; index += 1) {
    hash = (hash * 31 + key.charCodeAt(index)) | 0
  }

  const unsignedHash = hash >>> 0
  const red = (unsignedHash >>> 16) & 0xff
  const green = (unsignedHash >>> 8) & 0xff
  const blue = unsignedHash & 0xff
  return `#${[red, green, blue].map((channel) => channel.toString(16).padStart(2, '0')).join('')}`
}

export function buildRuntimeSatelliteGroupSources(
  baseGroups: readonly SatelliteGroupSource[],
  discoveredCanonicalKeys: readonly string[],
): SatelliteGroupSource[] {
  const groupsByFrontendKey = new Map(baseGroups.map((group) => [group.key, group]))
  const runtimeGroups = [...baseGroups]

  for (const canonicalKey of [...discoveredCanonicalKeys].sort((left, right) => left.localeCompare(right))) {
    const frontendKey = canonicalGroupAliases[canonicalKey] ?? canonicalKey
    if (groupsByFrontendKey.has(frontendKey)) continue

    groupsByFrontendKey.set(frontendKey, {
      key: frontendKey,
      label: humanizeGroupLabel(canonicalKey),
      type: canonicalKey,
      color: colorFromKey(canonicalKey),
      loadPositions: (signal?: AbortSignal) => fetchSatellitePositionsByType(canonicalKey, signal),
    })
  }

  for (const group of groupsByFrontendKey.values()) {
    if (!runtimeGroups.some((existing) => existing.key === group.key)) {
      runtimeGroups.push(group)
    }
  }

  return runtimeGroups
}

export function buildEnabledGroupsFromPreset(
  groups: readonly SatelliteGroupSource[],
  preset: GroupPreset,
) {
  if (preset === 'custom') {
    return null
  }

  const navigationKeys = new Set<SatelliteGroupKey>(['gpsOps', 'galileo', 'glonassOps', 'beidou', 'sbas'])
  const leoKeys = new Set<SatelliteGroupKey>([
    'starlink',
    'oneweb',
    'iridiumNext',
    'planet',
    'spire',
    'cubesat',
    'spaceMissions',
    'debris',
    'payload',
    'spaceRocket',
  ])

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

export function buildGroupRows(
  allGroups: readonly SatelliteGroupSource[],
  enabledGroups: Record<SatelliteGroupKey, boolean>,
  groupPositions: GroupPositionsState,
  groupLoading: GroupLoadingState,
  groupErrors: GroupErrorState,
): GroupRow[] {
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
