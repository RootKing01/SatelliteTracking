import type { SatelliteCatalogItem } from '../api/satelliteCatalogClient'
import type { SatelliteGroupKey, SatelliteGroupSource } from '../api/groups/types'
import type { SatellitePosition } from '../types/satellite'

export type SatelliteSearchScope = 'enabled' | 'all' | SatelliteGroupKey

export type SearchResultItem = {
  entityId: string
  groupKey: SatelliteGroupKey
  groupLabel: string
  satelliteId: number
  satelliteName: string
  objectId: string
  noradCatId: number
  hasLivePosition: boolean
}

type BuildSearchResultItemsParams = {
  allGroups: readonly SatelliteGroupSource[]
  enabledGroups: Record<SatelliteGroupKey, boolean>
  groupPositions: Partial<Record<SatelliteGroupKey, SatellitePosition[]>>
  catalogByGroup: Partial<Record<SatelliteGroupKey, SatelliteCatalogItem[]>>
  searchScope: SatelliteSearchScope
  searchQuery: string
}

export function buildSearchResultItems({
  allGroups,
  enabledGroups,
  groupPositions,
  catalogByGroup,
  searchScope,
  searchQuery,
}: BuildSearchResultItemsParams) {
  const normalizedQuery = searchQuery.trim().toLowerCase()

  const groupsToSearch =
    searchScope === 'enabled'
      ? allGroups.filter((group) => enabledGroups[group.key])
      : searchScope === 'all'
        ? allGroups
        : allGroups.filter((group) => group.key === searchScope)

  const liveItems = groupsToSearch.flatMap((group) =>
    (groupPositions[group.key] ?? []).map((satellite) => ({
      entityId: `${group.key}-${satellite.satelliteId}`,
      groupKey: group.key,
      groupLabel: group.label,
      satelliteId: satellite.satelliteId,
      satelliteName: satellite.satelliteName,
      objectId: satellite.objectId,
      noradCatId: satellite.noradCatId,
      hasLivePosition: true,
    })),
  )

  const mergedByEntityId = new Map<string, SearchResultItem>()
  for (const item of liveItems) {
    mergedByEntityId.set(item.entityId, item)
  }

  if (searchScope !== 'enabled') {
    for (const group of groupsToSearch) {
      for (const satellite of catalogByGroup[group.key] ?? []) {
        const entityId = `${group.key}-${satellite.id}`
        if (!mergedByEntityId.has(entityId)) {
          mergedByEntityId.set(entityId, {
            entityId,
            groupKey: group.key,
            groupLabel: group.label,
            satelliteId: satellite.id,
            satelliteName: satellite.objectName,
            objectId: satellite.objectId,
            noradCatId: satellite.noradCatId,
            hasLivePosition: false,
          })
        }
      }
    }
  }

  const baseItems = Array.from(mergedByEntityId.values())

  if (normalizedQuery.length < 2) {
    return baseItems.sort((a, b) => a.satelliteName.localeCompare(b.satelliteName)).slice(0, 30)
  }

  const filteredItems = normalizedQuery
    ? baseItems.filter((item) => {
        const searchableText = `${item.satelliteName} ${item.noradCatId} ${item.objectId}`
        return searchableText.toLowerCase().includes(normalizedQuery)
      })
    : baseItems

  return filteredItems
    .sort((a, b) => a.satelliteName.localeCompare(b.satelliteName))
    .slice(0, 90)
}
