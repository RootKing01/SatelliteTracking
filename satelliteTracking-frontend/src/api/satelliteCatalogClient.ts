export async function fetchSatelliteById(id: string | number, signal?: AbortSignal): Promise<SatelliteCatalogItem | null> {
  try {
    const response = await httpClient.get<SatelliteCatalogItem>(`/api/satellites/${id}`, { signal })
    return response.data
  } catch (err) {
    console.error('[satelliteCatalogClient] fetchSatelliteById error', id, err)
    return null
  }
}
import { httpClient } from './httpClient'

export type SatelliteCatalogItem = {
  id: number
  objectName: string
  objectId: string
  noradCatId: number
}

export async function fetchSatelliteCatalogByType(
  type: string,
  signal?: AbortSignal,
): Promise<SatelliteCatalogItem[]> {
  try {
    let url = '/api/satellites'
    let config: any = { signal }
    // Se type è passato e diverso da 'ALL', usa search-by-type
    if (type && type.toUpperCase() !== 'ALL') {
      url = '/api/satellites/search-by-type'
      config.params = { type }
    }
    const response = await httpClient.get<SatelliteCatalogItem[]>(url, config)
    console.log('[satelliteCatalogClient] response', response)
    return response.data
  } catch (err) {
    console.error('[satelliteCatalogClient] fetch error', err)
    throw err
  }
}
