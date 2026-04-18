import { getCurrentUser, login, logout, register, type AuthUser } from '../api/authClient'

import { extractAuthErrorMessage } from './appErrorHelpers'

export type AuthFlowResult = {
  user: AuthUser | null
  info: string
  error: string
}

export async function executeLoginFlow(payload: {
  usernameOrEmail: string
  password: string
}): Promise<AuthFlowResult> {
  try {
    const response = await login(payload)
    if (!response.authenticated || !response.user) {
      // Niente più gestione token: solo cookie
      return {
        user: null,
        info: '',
        error: response.message || 'Accesso non riuscito',
      }
    }

    // Niente più gestione token: solo cookie

    // Verifica immediata della sessione per evitare stato UI "loggato" senza cookie valido.
    const me = await getCurrentUser()
    if (!me.authenticated || !me.user) {
      // Niente più gestione token: solo cookie
      return {
        user: null,
        info: '',
        error: 'Accesso effettuato ma sessione non valida. Riprova il login.',
      }
    }

    return {
      user: me.user,
      info: `Benvenuto ${me.user.username}`,
      error: '',
    }
  } catch (error) {
    // Niente più gestione token: solo cookie
    return {
      user: null,
      info: '',
      error: extractAuthErrorMessage(error, 'Errore durante il login'),
    }
  }
}

export async function executeRegisterFlow(payload: {
  username: string
  email: string
  password: string
}): Promise<AuthFlowResult> {
  try {
    const response = await register(payload)
    if (!response.authenticated || !response.user) {
      clearAuthToken()
      return {
        user: null,
        info: '',
        error: response.message || 'Registrazione non riuscita',
      }
    }

    setAuthToken(response.token)

    const me = await getCurrentUser()
    if (!me.authenticated || !me.user) {
      clearAuthToken()
      return {
        user: null,
        info: '',
        error: 'Registrazione completata ma sessione non valida. Esegui l\'accesso.',
      }
    }

    return {
      user: me.user,
      info: `Registrazione completata: ${me.user.username}`,
      error: '',
    }
  } catch (error) {
    // Niente più gestione token: solo cookie
    return {
      user: null,
      info: '',
      error: extractAuthErrorMessage(error, 'Errore durante la registrazione'),
    }
  }
}

export async function executeLogoutFlow(): Promise<{ error: string }> {
  try {
    await logout()
    // Niente più gestione token: solo cookie
    return { error: '' }
  } catch (error) {
    clearAuthToken()
    return { error: extractAuthErrorMessage(error, 'Errore durante il logout') }
  }
}