import { extractGeolocationErrorMessage } from './appErrorHelpers'
import type { Dispatch, SetStateAction } from 'react'

const browserLocationOptions: PositionOptions = {
  enableHighAccuracy: true,
  maximumAge: 120000,
  timeout: 10000,
}

type BrowserLocation = {
  latitude: number
  longitude: number
  altitude: number
}

function getBrowserGeolocationPrecheckError(hasGeolocation: boolean, isSecureContext: boolean) {
  if (!hasGeolocation) {
    return 'Geolocalizzazione non disponibile nel browser.'
  }
  if (!isSecureContext) {
    return 'Geolocalizzazione bloccata: usa HTTPS o localhost (su smartphone HTTP non funziona).'
  }
  return ''
}

function requestBrowserLocation(
  geolocation: Geolocation,
  fallbackAltitude = 30,
): Promise<BrowserLocation> {
  return new Promise((resolve, reject) => {
    geolocation.getCurrentPosition(
      (position) => {
        resolve({
          latitude: position.coords.latitude,
          longitude: position.coords.longitude,
          altitude: position.coords.altitude ?? fallbackAltitude,
        })
      },
      reject,
      browserLocationOptions,
    )
  })
}

type BrowserLocationHandlerOptions = {
  locatingBrowser: boolean
  setLocatingBrowser: Dispatch<SetStateAction<boolean>>
  setError: Dispatch<SetStateAction<string>>
  setLatitude: Dispatch<SetStateAction<number | null>>
  setLongitude: Dispatch<SetStateAction<number | null>>
  setAltitude: Dispatch<SetStateAction<number | null>>
  setInfo: Dispatch<SetStateAction<string>>
  successMessage: string
}

function handleBrowserLocation(options: BrowserLocationHandlerOptions) {
  if (options.locatingBrowser) return

  const precheckError = getBrowserGeolocationPrecheckError(
    Boolean(navigator.geolocation),
    window.isSecureContext,
  )
  if (precheckError) {
    options.setError(precheckError)
    return
  }

  const geolocation = navigator.geolocation
  if (!geolocation) {
    options.setError('Geolocalizzazione non disponibile nel browser.')
    return
  }

  options.setLocatingBrowser(true)
  options.setError('')

  void requestBrowserLocation(geolocation)
    .then((location) => {
      options.setLatitude(location.latitude)
      options.setLongitude(location.longitude)
      options.setAltitude(location.altitude)
      options.setInfo(options.successMessage)
    })
    .catch((error) => {
      options.setError(extractGeolocationErrorMessage(error as GeolocationPositionError))
    })
    .finally(() => options.setLocatingBrowser(false))
}

export function handleUseBrowserLocationImpl(options: {
  locatingBrowser: boolean
  setLocatingBrowser: Dispatch<SetStateAction<boolean>>
  setSightingsError: Dispatch<SetStateAction<string>>
  setSightingLatitude: Dispatch<SetStateAction<number | null>>
  setSightingLongitude: Dispatch<SetStateAction<number | null>>
  setSightingAltitude: Dispatch<SetStateAction<number | null>>
  setSightingInfo: Dispatch<SetStateAction<string>>
}) {
  handleBrowserLocation({
    locatingBrowser: options.locatingBrowser,
    setLocatingBrowser: options.setLocatingBrowser,
    setError: options.setSightingsError,
    setLatitude: options.setSightingLatitude,
    setLongitude: options.setSightingLongitude,
    setAltitude: options.setSightingAltitude,
    setInfo: options.setSightingInfo,
    successMessage: 'Posizione browser acquisita.',
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
  handleBrowserLocation({
    locatingBrowser: options.visibilityLocatingBrowser,
    setLocatingBrowser: options.setVisibilityLocatingBrowser,
    setError: options.setVisibilityError,
    setLatitude: options.setVisibilityLatitude,
    setLongitude: options.setVisibilityLongitude,
    setAltitude: options.setVisibilityAltitude,
    setInfo: options.setVisibilityInfo,
    successMessage: 'Posizione browser attiva per il calcolo visibilita.',
  })
}

export default {}
