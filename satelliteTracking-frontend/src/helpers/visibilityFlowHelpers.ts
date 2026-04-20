import {
  fetchVisibleUpcomingPasses,
  fetchVisibleUpcomingPassesByCity,
  type UpcomingPass,
} from '../api/satelliteVisibilityClient'

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
  const hasCity = normalizedCity.length > 0

  if (hasCity) {
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