import type { SatelliteGroupKey, SatelliteGroupSource } from '../api/groups/types'
import { fetchSatellitePositionsByType } from '../api/satelliteClient'
import type { SatellitePosition } from '../types/satellite'

export type GroupPreset = 'custom' | 'all' | 'stations' | 'navigation' | 'leo'

const canonicalKeyAliases: Record<string, string> = {
  'space-missions': 'spaceMissions',
  'iridium-NEXT': 'iridiumNext',
  'gps-ops': 'gpsOps',
  'glonass-ops': 'glonassOps',
  'space-rocket': 'spaceRocket',
}

type GroupLoadingState = Partial<Record<SatelliteGroupKey, boolean>>
type GroupErrorState = Partial<Record<SatelliteGroupKey, string>>
type GroupPositionsState = Partial<Record<SatelliteGroupKey, SatellitePosition[]>>

export function createDefaultEnabledGroups(groups: readonly SatelliteGroupSource[]) {
  return Object.fromEntries(
    groups.map((group) => [group.key, group.key === 'stations' || group.key === 'spaceMissions']),
  ) as Record<SatelliteGroupKey, boolean>
}

function humanizeGroupLabel(key: string): string {
  const customLabels: Record<string, string> = {
    'space-missions': 'Space Missions',
    'iridium-NEXT': 'Iridium NEXT',
    'gps-ops': 'GPS Ops',
    'glonass-ops': 'GLONASS Ops',
    debris: 'Debris',
    payload: 'Payload',
    'space-rocket': 'Space Rocket',
  }

  if (customLabels[key]) return customLabels[key]

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

  return `hsl(${Math.abs(hash) % 360} 82% 63%)`
}

export function buildRuntimeSatelliteGroupSources(
  baseGroups: readonly SatelliteGroupSource[],
  discoveredCanonicalKeys: readonly string[],
): SatelliteGroupSource[] {
  const groupsByFrontendKey = new Map(baseGroups.map((group) => [group.key, group]))
  const runtimeGroups = [...baseGroups]

  for (const canonicalKey of [...discoveredCanonicalKeys].sort((left, right) => left.localeCompare(right))) {
    const frontendKey = canonicalKeyAliases[canonicalKey] ?? canonicalKey
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
