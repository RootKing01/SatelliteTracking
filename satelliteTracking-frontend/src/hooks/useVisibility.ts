import { useMemo, useState } from 'react'
import type { UpcomingPass } from '../api/satelliteClient'
import { useCalculateVisibility } from './useInteractionHandlers'
import { buildVisibilityQueryLocationLabel, filterVisibilityResults } from '../helpers/visibilityHelpers'

type VisibilityOptions = {
  setAuthUser: Parameters<typeof useCalculateVisibility>[0]['setAuthUser']
  setAuthInfo: Parameters<typeof useCalculateVisibility>[0]['setAuthInfo']
  setAuthError: Parameters<typeof useCalculateVisibility>[0]['setAuthError']
  clearSessionState: () => void
}

export function useVisibility(options: VisibilityOptions) {
  const [visibilityHours, setVisibilityHours] = useState(12)
  const [visibilityMinElevation, setVisibilityMinElevation] = useState(10)
  const [visibilityLoading, setVisibilityLoading] = useState(false)
  const [visibilityError, setVisibilityError] = useState('')
  const [visibilityInfo, setVisibilityInfo] = useState('')
  const [visibilityCity, setVisibilityCity] = useState('')
  const [visibilityResults, setVisibilityResults] = useState<UpcomingPass[]>([])
  const [visibilityAllResults, setVisibilityAllResults] = useState<UpcomingPass[]>([])
  const [visibilityOverlayOpen, setVisibilityOverlayOpen] = useState(false)
  const [visibilityOverlayQuery, setVisibilityOverlayQuery] = useState('')
  const [visibilityLatitude, setVisibilityLatitude] = useState<number | null>(null)
  const [visibilityLongitude, setVisibilityLongitude] = useState<number | null>(null)
  const [visibilityAltitude, setVisibilityAltitude] = useState<number | null>(null)
  const [visibilityLocatingBrowser, setVisibilityLocatingBrowser] = useState(false)

  const handleCalculateVisibility = useCalculateVisibility({
    getVisibilityLoading: () => visibilityLoading,
    setVisibilityLoading,
    setVisibilityError,
    setVisibilityInfo,
    visibilityCity,
    visibilityHours,
    visibilityMinElevation,
    getVisibilityLatitude: () => visibilityLatitude,
    getVisibilityLongitude: () => visibilityLongitude,
    getVisibilityAltitude: () => visibilityAltitude,
    setVisibilityAllResults,
    setVisibilityResults,
    setVisibilityOverlayOpen,
    ...options,
  })

  const visibilityQueryLocationLabel = useMemo(
    () => buildVisibilityQueryLocationLabel(visibilityCity, visibilityLatitude, visibilityLongitude),
    [visibilityCity, visibilityLatitude, visibilityLongitude],
  )
  const visibilityOverlayFilteredResults = useMemo(
    () => filterVisibilityResults(visibilityAllResults, visibilityOverlayQuery),
    [visibilityAllResults, visibilityOverlayQuery],
  )

  return {
    visibilityHours, setVisibilityHours, visibilityMinElevation, setVisibilityMinElevation,
    visibilityLoading, visibilityError, visibilityInfo, visibilityCity, setVisibilityCity,
    visibilityResults, visibilityAllResults, visibilityOverlayOpen, setVisibilityOverlayOpen,
    visibilityOverlayQuery, setVisibilityOverlayQuery, visibilityLatitude, setVisibilityLongitude,
    visibilityLongitude, visibilityAltitude, visibilityLocatingBrowser, setVisibilityLocatingBrowser,
    setVisibilityError, setVisibilityInfo, setVisibilityLatitude, setVisibilityAltitude,
    setVisibilityAllResults, setVisibilityResults, handleCalculateVisibility,
    visibilityQueryLocationLabel, visibilityOverlayFilteredResults,
  }
}
