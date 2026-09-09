import { httpClient } from './httpClient'
import { extractAuthErrorMessage } from '../helpers/appErrorHelpers'

export type AuthUser = {
  id: number
  username: string
  email: string
  role: string
}

export type AuthResponse = {
  authenticated: boolean
  message: string
  user: AuthUser | null
  token?: string | null
}

export async function login(payload: {
  usernameOrEmail: string
  password: string
}): Promise<AuthResponse> {
  const response = await httpClient.post<AuthResponse>('/api/auth/login', payload)
  return response.data
}

export async function register(payload: {
  username: string
  email: string
  password: string
}): Promise<AuthResponse> {
  const response = await httpClient.post<AuthResponse>('/api/auth/register', payload)
  return response.data
}

export async function getCurrentUser(signal?: AbortSignal): Promise<AuthResponse> {
  const response = await httpClient.get<AuthResponse>('/api/auth/me', { signal })
  return response.data
}

export async function logout(): Promise<AuthResponse> {
  const response = await httpClient.post<AuthResponse>('/api/auth/logout')
  return response.data
}

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
      return { user: null, info: '', error: response.message || 'Accesso non riuscito' }
    }

    const me = await getCurrentUser()
    if (!me.authenticated || !me.user) {
      return {
        user: null,
        info: '',
        error: 'Accesso effettuato ma sessione non valida. Riprova il login.',
      }
    }

    return { user: me.user, info: `Benvenuto ${me.user.username}`, error: '' }
  } catch (error) {
    return { user: null, info: '', error: extractAuthErrorMessage(error, 'Errore durante il login') }
  }
}

export async function executeRegisterFlow(payload: {
  username: string
  email: string
  password: string
  passwordConfirm: string
}): Promise<AuthFlowResult> {
  try {
    if (payload.password !== payload.passwordConfirm) {
      return { user: null, info: '', error: 'Le password non coincidono.' }
    }

    const response = await register({
      username: payload.username,
      email: payload.email,
      password: payload.password,
    })
    if (!response.authenticated || !response.user) {
      return { user: null, info: '', error: response.message || 'Registrazione non riuscita' }
    }

    const me = await getCurrentUser()
    if (!me.authenticated || !me.user) {
      return {
        user: null,
        info: '',
        error: 'Registrazione completata ma sessione non valida. Esegui l\'accesso.',
      }
    }

    return { user: me.user, info: `Registrazione completata: ${me.user.username}`, error: '' }
  } catch (error) {
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
    return { error: '' }
  } catch (error) {
    return { error: extractAuthErrorMessage(error, 'Errore durante il logout') }
  }
}
