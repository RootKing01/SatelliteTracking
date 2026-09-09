import {
  fetchVisibleUpcomingPasses,
  fetchVisibleUpcomingPassesByCity,
  type UpcomingPass,
} from '../api/satelliteClient'

export type VisibilityQueryInput = {
  city: string
  hours: number
  minElevation: number
  latitude: number | null
  longitude: number | null
  altitude: number | null
}

export async function fetchVisibilityPasses(input: VisibilityQueryInput): Promise<UpcomingPass[]> {
  const normalizedCity = input.city.trim()
  if (normalizedCity.length > 0) {
    return (
      await fetchVisibleUpcomingPassesByCity({
        city: normalizedCity,
        hours: input.hours,
        minElevation: input.minElevation,
        observingCondition: 'any',
        maxMagnitude: 3.0,
      })
    ).passes
  }

  return fetchVisibleUpcomingPasses({
    hours: input.hours,
    minElevation: input.minElevation,
    observingCondition: 'any',
    maxMagnitude: 3.0,
    latitude: input.latitude ?? undefined,
    longitude: input.longitude ?? undefined,
    altitude: input.altitude ?? undefined,
  })
}

export function buildVisibilitySummaryInfo(resultsCount: number) {
  return resultsCount === 0
    ? 'Nessun passaggio visibile nei parametri selezionati.'
    : `Trovati ${resultsCount} passaggi visibili.`
}

export type VisibilityErrorResetState = {
  allResults: UpcomingPass[]
  previewResults: UpcomingPass[]
  closeOverlay: boolean
}

export function createVisibilityErrorResetState(): VisibilityErrorResetState {
  return {
    allResults: [],
    previewResults: [],
    closeOverlay: true,
  }
}

export function buildVisibilityQueryLocationLabel(
  city: string,
  latitude: number | null,
  longitude: number | null,
) {
  if (city.trim()) {
    return `Citta: ${city.trim()}`
  }
  if (latitude !== null && longitude !== null) {
    return `Coordinate browser: ${latitude.toFixed(4)}, ${longitude.toFixed(4)}`
  }
  return 'Posizione default backend (San Marcellino)'
}

export function filterVisibilityResults(results: UpcomingPass[], query: string) {
  const normalized = query.trim().toLowerCase()
  if (!normalized) {
    return results
  }

  return results.filter((pass) => {
    const text = `${pass.satelliteName} ${pass.satelliteId} ${pass.observingCondition} ${pass.visibility}`
    return text.toLowerCase().includes(normalized)
  })
}

export function downloadVisibilityResultsCsv(results: UpcomingPass[]) {
  const escapeCsv = (value: string | number) => `"${String(value).replaceAll('"', '""')}"`

  const header = [
    'index',
    'satelliteName',
    'satelliteId',
    'riseTime',
    'setTime',
    'maxElevationDeg',
    'estimatedMagnitude',
    'observingCondition',
    'visibility',
  ]

  const rows = results.map((pass, index) => [
    index + 1,
    pass.satelliteName,
    pass.satelliteId,
    new Date(pass.riseTime).toLocaleString('it-IT'),
    new Date(pass.setTime).toLocaleString('it-IT'),
    pass.maxElevation.toFixed(1),
    pass.estimatedMagnitude.toFixed(1),
    pass.observingCondition,
    pass.visibility,
  ])

  const csv = [header, ...rows]
    .map((line) => line.map((cell) => escapeCsv(cell)).join(','))
    .join('\n')

  const blob = new Blob([csv], { type: 'text/csv;charset=utf-8;' })
  const url = URL.createObjectURL(blob)
  const link = document.createElement('a')
  const timestamp = new Date().toISOString().replaceAll(':', '-').slice(0, 19)
  link.href = url
  link.download = `passaggi-visibili-${timestamp}.csv`
  document.body.appendChild(link)
  link.click()
  link.remove()
  URL.revokeObjectURL(url)
}
