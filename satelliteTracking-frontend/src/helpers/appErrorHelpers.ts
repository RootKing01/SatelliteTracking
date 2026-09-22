import { isAxiosError } from 'axios'

export function extractAuthErrorMessage(error: unknown, fallbackMessage: string) {
  if (isAxiosError(error)) {
    const responseData = error.response?.data as { message?: string; error?: string; detail?: string } | undefined
    if (responseData?.message) {
      return responseData.message
    }
    if (responseData?.detail) {
      return responseData.detail
    }
    if (responseData?.error) {
      return responseData.error
    }
  }
  return fallbackMessage
}

export function extractGeolocationErrorMessage(error: GeolocationPositionError | null | undefined) {
  if (!window.isSecureContext) {
    return 'Geolocalizzazione bloccata: apri il sito in HTTPS (su smartphone HTTP non e consentito).'
  }

  if (!error) {
    return 'Impossibile acquisire la posizione browser.'
  }

  if (error.code === error.PERMISSION_DENIED) {
    return 'Permesso geolocalizzazione negato. Abilitalo nelle impostazioni del browser.'
  }

  if (error.code === error.POSITION_UNAVAILABLE) {
    return 'Posizione non disponibile. Verifica GPS/rete e riprova.'
  }

  if (error.code === error.TIMEOUT) {
    return 'Timeout geolocalizzazione. Riprova con segnale GPS migliore.'
  }

  return error.message || 'Impossibile acquisire la posizione browser.'
}
