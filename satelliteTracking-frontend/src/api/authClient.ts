import { httpClient } from './httpClient'

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
