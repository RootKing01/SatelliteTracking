/**
 * Crea una mappa id->nome da una lista di satelliti.
 */
export function buildSatelliteNameMap(list: any[]): Record<string, string> {
  const map: Record<string, string> = {}
  for (const sat of list) {
    map[String(sat.id)] = sat.objectName
    map[String(sat.noradCatId)] = sat.objectName
    if (sat.objectId) map[String(sat.objectId)] = sat.objectName
  }
  return map
}

/**
 * Aggiorna la mappa dei nomi satelliti per gli id mancanti.
 * Chiama setSatelliteNames solo se trova nuovi nomi.
 */
export async function updateMissingSatelliteNames(
  missingIds: string[],
  setSatelliteNames: (fn: (prev: Record<string, string>) => Record<string, string>) => void
) {
  for (const id of missingIds) {
    try {
      const sat = await fetchSatelliteById(id)
      if (sat && sat.objectName) {
        setSatelliteNames(prev => ({ ...prev, [id]: sat.objectName }))
      }
    } catch (err) {
      console.error('Errore fetchSatelliteById', id, err)
    }
  }
}
import { fetchSatelliteCatalogByType, fetchSatelliteById } from '../api/satelliteCatalogClient'

/**
 * Fetches the satellite catalog and returns a mapping from satellite IDs to names.
 * @param type Satellite type (default: 'ALL')
 */
export async function fetchAndMapSatelliteNames(type: string = 'ALL') {
  const list = await fetchSatelliteCatalogByType(type)
  const map: Record<string, string> = {}
  for (const sat of list) {
    map[String(sat.id)] = sat.objectName
    map[String(sat.noradCatId)] = sat.objectName
    if (sat.objectId) map[String(sat.objectId)] = sat.objectName
  }
  return { map, list }
}
