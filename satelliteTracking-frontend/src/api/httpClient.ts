import axios from 'axios'

const configuredApiBaseUrl = import.meta.env.VITE_API_BASE_URL?.trim()

const isLocalClient =
  typeof window !== 'undefined' &&
  ['localhost', '127.0.0.1'].includes(window.location.hostname)

const apiBaseUrl =
  isLocalClient && configuredApiBaseUrl
    ? configuredApiBaseUrl
    : ''

export const httpClient = axios.create({
  baseURL: apiBaseUrl,
  timeout: 15000,
  withCredentials: true,
})

const csrfCookieName = 'XSRF-TOKEN'
const csrfHeaderName = 'X-XSRF-TOKEN'
const csrfMethods = new Set(['POST', 'PUT', 'PATCH', 'DELETE'])
let csrfRequest: Promise<unknown> | null = null

function readCookie(name: string): string | null {
  if (typeof document === 'undefined') return null

  const encodedName = `${encodeURIComponent(name)}=`
  const cookie = document.cookie
    .split('; ')
    .find((value) => value.startsWith(encodedName) || value.startsWith(`${name}=`))

  if (!cookie) return null

  const value = cookie.slice(cookie.indexOf('=') + 1)
  return decodeURIComponent(value)
}

async function ensureCsrfCookie() {
  if (readCookie(csrfCookieName)) return
  if (!csrfRequest) {
    csrfRequest = httpClient.get('/api/auth/csrf').finally(() => {
      csrfRequest = null
    })
  }
  await csrfRequest
}

httpClient.interceptors.request.use(async (config) => {
  const method = config.method?.toUpperCase()
  if (!method || !csrfMethods.has(method)) return config

  await ensureCsrfCookie()
  const csrfToken = readCookie(csrfCookieName)
  if (csrfToken) {
    config.headers.set(csrfHeaderName, csrfToken)
  }

  return config
})
