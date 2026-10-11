import { fetchSatelliteCatalogByType, fetchSatelliteById } from '../api/satelliteClient'

type SatelliteNameMap = Record<string, string>
type SatelliteCatalogMapping = { map: SatelliteNameMap; list: Awaited<ReturnType<typeof fetchSatelliteCatalogByType>> }

const catalogCache = new Map<string, Promise<SatelliteCatalogMapping>>()
const missingSatelliteRequests = new Map<string, Promise<string | null>>()
const unavailableSatelliteIds = new Set<string>()

function buildSatelliteNameMap(list: Awaited<ReturnType<typeof fetchSatelliteCatalogByType>>): SatelliteNameMap {
  const map: SatelliteNameMap = {}
  for (const sat of list) {
    map[String(sat.id)] = sat.objectName
    map[String(sat.noradCatId)] = sat.objectName
    if (sat.objectId) map[String(sat.objectId)] = sat.objectName
  }
  return map
}

function getCatalogMapping(type: string): Promise<SatelliteCatalogMapping> {
  const cacheKey = type.toUpperCase() === 'ALL' ? 'ALL' : type
  const cached = catalogCache.get(cacheKey)
  if (cached) return cached

  const request = fetchSatelliteCatalogByType(type).then((list) => ({
    list,
    map: buildSatelliteNameMap(list),
  }))
  catalogCache.set(cacheKey, request)
  request.catch(() => catalogCache.delete(cacheKey))
  return request
}

export async function updateMissingSatelliteNames(
  missingIds: string[],
  setSatelliteNames: (fn: (prev: Record<string, string>) => Record<string, string>) => void
) {
  const idsToFetch = [...new Set(missingIds)].filter(
    (id) => !unavailableSatelliteIds.has(id) && !missingSatelliteRequests.has(id),
  )

  await Promise.all(idsToFetch.map(async (id) => {
    const request = fetchSatelliteById(id)
      .then((sat) => {
        if (!sat?.objectName) {
          unavailableSatelliteIds.add(id)
          return null
        }

        setSatelliteNames((prev) => ({ ...prev, [id]: sat.objectName }))
        return sat.objectName
      })
      .catch((error) => {
        unavailableSatelliteIds.add(id)
        console.error('Errore fetchSatelliteById', id, error)
        return null
      })

    missingSatelliteRequests.set(id, request)
    await request
    missingSatelliteRequests.delete(id)
  }))
}

/**
 * Fetches the satellite catalog and returns a mapping from satellite IDs to names.
 * @param type Satellite type (default: 'ALL')
 */
export async function fetchAndMapSatelliteNames(type: string = 'ALL') {
  return getCatalogMapping(type)
}
