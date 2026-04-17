const browserLocationOptions: PositionOptions = {
  enableHighAccuracy: true,
  maximumAge: 120000,
  timeout: 10000,
}

export type BrowserLocation = {
  latitude: number
  longitude: number
  altitude: number
}

export function getBrowserGeolocationPrecheckError(
  hasGeolocation: boolean,
  isSecureContext: boolean,
) {
  if (!hasGeolocation) {
    return 'Geolocalizzazione non disponibile nel browser.'
  }

  if (!isSecureContext) {
    return 'Geolocalizzazione bloccata: usa HTTPS o localhost (su smartphone HTTP non funziona).'
  }

  return ''
}

export function requestBrowserLocation(
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
      (error) => {
        reject(error)
      },
      browserLocationOptions,
    )
  })
}