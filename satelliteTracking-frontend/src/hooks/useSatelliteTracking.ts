import { useEffect, useMemo, useState } from 'react'
import { Color } from 'cesium'
import { fetchSatelliteGroupsStats } from '../api/satelliteClient'
import { satelliteGroupSources } from '../api/groups'
import type { SatelliteGroupKey, SatelliteGroupSource } from '../api/groups/types'
import {
  buildEnabledGroupsFromPreset,
  buildRuntimeSatelliteGroupSources,
  createDefaultEnabledGroups,
  type GroupPreset,
} from '../helpers/groupHelpers'
import {
  buildLiveEntityIdBySatelliteId,
  buildSatelliteLookupByEntityId,
} from '../helpers/searchHelpers'
import { useLiveSatelliteGroups } from './useLiveSatelliteGroups'
import type { VisibleSatelliteItem } from '../components/SatelliteGlobe'

export const defaultEnabledGroups = createDefaultEnabledGroups(satelliteGroupSources)

type UseSatelliteTrackingOptions = {
  authenticated: boolean
  enabledGroups: Record<SatelliteGroupKey, boolean>
  selectedPreset: GroupPreset
  compactMobileViewport: boolean
  onSessionExpired: () => void
}

export function useSatelliteTracking({
  authenticated,
  enabledGroups,
  selectedPreset,
  compactMobileViewport,
  onSessionExpired,
}: UseSatelliteTrackingOptions) {
  const [discoveredCanonicalGroupKeys, setDiscoveredCanonicalGroupKeys] = useState<string[]>([])
  const [groupDiscoveryReady, setGroupDiscoveryReady] = useState(false)

  const allGroups = useMemo(
    () =>
      buildRuntimeSatelliteGroupSources(
        satelliteGroupSources as readonly SatelliteGroupSource[],
        discoveredCanonicalGroupKeys,
      ),
    [discoveredCanonicalGroupKeys],
  )

  const groupColorMap = useMemo(
    () =>
      Object.fromEntries(
        allGroups.map((group) => [group.key, Color.fromCssColorString(group.color)]),
      ) as Record<SatelliteGroupKey, Color>,
    [allGroups],
  )

  useEffect(() => {
    if (!authenticated) {
      setGroupDiscoveryReady(false)
      setDiscoveredCanonicalGroupKeys([])
      return
    }

    const controller = new AbortController()

    void fetchSatelliteGroupsStats(controller.signal)
      .then(({ stats }) => {
        setDiscoveredCanonicalGroupKeys(Object.keys(stats))
      })
      .catch(() => {
        setDiscoveredCanonicalGroupKeys([])
      })
      .finally(() => {
        setGroupDiscoveryReady(true)
      })

    return () => controller.abort()
  }, [authenticated])

  const effectiveEnabledGroups = useMemo(
    () => buildEnabledGroupsFromPreset(allGroups, selectedPreset) ?? enabledGroups,
    [allGroups, enabledGroups, selectedPreset],
  )

  const activeGroups = useMemo(
    () => allGroups.filter((group) => effectiveEnabledGroups[group.key]),
    [allGroups, effectiveEnabledGroups],
  )

  const {
    groupPositions,
    groupLoading,
    groupErrors,
    isRefreshing,
    hasLoadedOnce,
    refreshIntervalMs,
  } = useLiveSatelliteGroups({
    activeGroups,
    authenticated,
    enabled: groupDiscoveryReady,
    compactMobileViewport,
    onSessionExpired,
  })

  const totalVisibleCount = useMemo(
    () => activeGroups.reduce(
      (total, group) => total + (groupPositions[group.key]?.length ?? 0),
      0,
    ),
    [activeGroups, groupPositions],
  )

  const visibleEntitySatellites = useMemo<VisibleSatelliteItem[]>(
    () => activeGroups.flatMap((group) =>
      group.key === 'starlink'
        ? []
        : (groupPositions[group.key] ?? []).map((satellite) => ({ group, satellite })),
    ),
    [activeGroups, groupPositions],
  )

  const starlinkSatellites = useMemo(
    () => (enabledGroups.starlink ? groupPositions.starlink ?? [] : []),
    [enabledGroups.starlink, groupPositions.starlink],
  )

  const satelliteLookupByEntityId = useMemo(
    () => buildSatelliteLookupByEntityId(allGroups, groupPositions),
    [allGroups, groupPositions],
  )

  const liveEntityIdBySatelliteId = useMemo(
    () => buildLiveEntityIdBySatelliteId(allGroups, groupPositions),
    [allGroups, groupPositions],
  )

  return {
    allGroups,
    groupColorMap,
    effectiveEnabledGroups,
    activeGroups,
    groupPositions,
    groupLoading,
    groupErrors,
    isRefreshing,
    hasLoadedOnce,
    refreshIntervalMs,
    totalVisibleCount,
    visibleEntitySatellites,
    starlinkSatellites,
    satelliteLookupByEntityId,
    liveEntityIdBySatelliteId,
    defaultEnabledGroups,
  }
}
