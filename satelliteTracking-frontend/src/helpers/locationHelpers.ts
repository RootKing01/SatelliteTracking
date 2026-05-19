import { getBrowserGeolocationPrecheckError, requestBrowserLocation } from './browserGeolocationHelpers'
import { extractGeolocationErrorMessage } from './appErrorHelpers'
import type { Dispatch, SetStateAction } from 'react'

export function handleUseBrowserLocationImpl(options: {
  locatingBrowser: boolean
  setLocatingBrowser: Dispatch<SetStateAction<boolean>>
  setSightingsError: Dispatch<SetStateAction<string>>
  setSightingLatitude: Dispatch<SetStateAction<number | null>>
  setSightingLongitude: Dispatch<SetStateAction<number | null>>
  setSightingAltitude: Dispatch<SetStateAction<number | null>>
  setSightingInfo: Dispatch<SetStateAction<string>>
}) {
  const {
    locatingBrowser,
    setLocatingBrowser,
    setSightingsError,
    setSightingLatitude,
    setSightingLongitude,
    setSightingAltitude,
    setSightingInfo,
  } = options

  if (locatingBrowser) {
    return
  }

  const precheckError = getBrowserGeolocationPrecheckError(Boolean(navigator.geolocation), window.isSecureContext)
  if (precheckError) {
    setSightingsError(precheckError)
    return
  }

  const geolocation = navigator.geolocation
  if (!geolocation) {
    setSightingsError('Geolocalizzazione non disponibile nel browser.')
    return
  }

  setLocatingBrowser(true)
  setSightingsError('')

  void requestBrowserLocation(geolocation)
    .then((location) => {
      setSightingLatitude(location.latitude)
      setSightingLongitude(location.longitude)
      setSightingAltitude(location.altitude)
      setSightingInfo('Posizione browser acquisita.')
    })
    .catch((error) => {
      setSightingsError(extractGeolocationErrorMessage(error as GeolocationPositionError))
    })
    .finally(() => {
      setLocatingBrowser(false)
    })
}

export function handleUseBrowserLocationForVisibilityImpl(options: {
  visibilityLocatingBrowser: boolean
  setVisibilityLocatingBrowser: Dispatch<SetStateAction<boolean>>
  setVisibilityError: Dispatch<SetStateAction<string>>
  setVisibilityLatitude: Dispatch<SetStateAction<number | null>>
  setVisibilityLongitude: Dispatch<SetStateAction<number | null>>
  setVisibilityAltitude: Dispatch<SetStateAction<number | null>>
  setVisibilityInfo: Dispatch<SetStateAction<string>>
}) {
  const {
    visibilityLocatingBrowser,
    setVisibilityLocatingBrowser,
    setVisibilityError,
    setVisibilityLatitude,
    setVisibilityLongitude,
    setVisibilityAltitude,
    setVisibilityInfo,
  } = options

  if (visibilityLocatingBrowser) {
    return
  }

  const precheckError = getBrowserGeolocationPrecheckError(Boolean(navigator.geolocation), window.isSecureContext)
  if (precheckError) {
    setVisibilityError(precheckError)
    return
  }

  const geolocation = navigator.geolocation
  if (!geolocation) {
    setVisibilityError('Geolocalizzazione non disponibile nel browser.')
    return
  }

  setVisibilityLocatingBrowser(true)
  setVisibilityError('')

  void requestBrowserLocation(geolocation)
    .then((location) => {
      setVisibilityLatitude(location.latitude)
      setVisibilityLongitude(location.longitude)
      setVisibilityAltitude(location.altitude)
      setVisibilityInfo('Posizione browser attiva per il calcolo visibilita.')
    })
    .catch((error) => {
      setVisibilityError(extractGeolocationErrorMessage(error as GeolocationPositionError))
    })
    .finally(() => {
      setVisibilityLocatingBrowser(false)
    })
}

export default {}
