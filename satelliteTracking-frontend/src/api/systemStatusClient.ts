import { isAxiosError } from 'axios'
import { httpClient } from './httpClient'

export type OrekitStatusResponse = {
  orekitDataLoaded: boolean
  orekitDataPath: string
  status: 'loaded' | 'fallback'
  checkedAt: string
}

export type SystemHealthStatus = 'UP' | 'DEGRADED' | 'DOWN'

export type SystemHealthResponse = {
  status: SystemHealthStatus
  checkedAt: string
  components: {
    api: string
    database: string
    orekit: string
  }
  orekitDataPath: string
}

export async function fetchOrekitStatus(signal?: AbortSignal): Promise<OrekitStatusResponse> {
  const response = await httpClient.get<OrekitStatusResponse>('/api/system/orekit-status', { signal })
  return response.data
}

export async function fetchSystemHealth(signal?: AbortSignal): Promise<SystemHealthResponse> {
  const response = await httpClient.get<SystemHealthResponse>('/api/system/health', { signal })
  return response.data
}

export type OrekitStatusLoadResult = {
  status: OrekitStatusResponse | null
  error: string
}

export type SystemHealthLoadResult = {
  status: SystemHealthResponse | null
  error: string
}

export async function loadOrekitStatus(signal?: AbortSignal): Promise<OrekitStatusLoadResult> {
  try {
    return { status: await fetchOrekitStatus(signal), error: '' }
  } catch (error) {
    return {
      status: null,
      error: isAxiosError(error) && error.response?.status === 401
        ? 'Stato Orekit non autorizzato'
        : 'Stato Orekit non disponibile',
    }
  }
}

export async function loadSystemHealth(signal?: AbortSignal): Promise<SystemHealthLoadResult> {
  try {
    return { status: await fetchSystemHealth(signal), error: '' }
  } catch (error) {
    return {
      status: null,
      error: isAxiosError(error) && error.response?.status === 401
        ? 'Stato sistema non autorizzato'
        : 'Stato sistema non disponibile',
    }
  }
}
