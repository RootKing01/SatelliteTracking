import axios from 'axios'

const configuredApiBaseUrl = import.meta.env.VITE_API_BASE_URL?.trim()

const isRemoteClient =
  typeof window !== 'undefined' &&
  !['localhost', '127.0.0.1'].includes(window.location.hostname)

const apiBaseUrl =
  configuredApiBaseUrl && !(isRemoteClient && configuredApiBaseUrl.includes('localhost'))
    ? configuredApiBaseUrl
    : ''

export const httpClient = axios.create({
  baseURL: apiBaseUrl,
  timeout: 15000,
  withCredentials: true,
})
