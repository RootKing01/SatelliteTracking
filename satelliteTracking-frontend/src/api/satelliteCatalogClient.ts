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
  const response = await httpClient.get<SatelliteCatalogItem[]>('/api/satellites/search-by-type', {
    params: { type },
    signal,
  })

  return response.data
}
