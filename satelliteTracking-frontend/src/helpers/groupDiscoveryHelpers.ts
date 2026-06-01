import { fetchSatellitePositionsByType } from '../api/satellitePositionsClient'
import type { SatelliteGroupSource } from '../api/groups/types'

const canonicalKeyAliases: Record<string, string> = {
  'space-missions': 'spaceMissions',
  'iridium-NEXT': 'iridiumNext',
  'gps-ops': 'gpsOps',
  'glonass-ops': 'glonassOps',
  'space-rocket': 'spaceRocket',
}

function resolveFrontendKey(key: string): string {
  return canonicalKeyAliases[key] ?? key
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

  if (customLabels[key]) {
    return customLabels[key]
  }

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

  const hue = Math.abs(hash) % 360
  return `hsl(${hue} 82% 63%)`
}

export function buildRuntimeSatelliteGroupSources(
  baseGroups: readonly SatelliteGroupSource[],
  discoveredCanonicalKeys: readonly string[],
): SatelliteGroupSource[] {
  const groupsByFrontendKey = new Map(baseGroups.map((group) => [group.key, group]))
  const runtimeGroups = [...baseGroups]

  const sortedKeys = [...discoveredCanonicalKeys].sort((left, right) => left.localeCompare(right))

  for (const canonicalKey of sortedKeys) {
    const frontendKey = resolveFrontendKey(canonicalKey)
    if (groupsByFrontendKey.has(frontendKey)) {
      continue
    }

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