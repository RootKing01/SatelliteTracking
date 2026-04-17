import { isAxiosError } from 'axios'
import { fetchOrekitStatus, type OrekitStatusResponse } from '../api/orekitStatusClient'
import { fetchSystemHealth, type SystemHealthResponse } from '../api/systemHealthClient'

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
    const status = await fetchOrekitStatus(signal)
    return { status, error: '' }
  } catch (error) {
    return {
      status: null,
      error:
        isAxiosError(error) && error.response?.status === 401
          ? 'Stato Orekit non autorizzato'
          : 'Stato Orekit non disponibile',
    }
  }
}

export async function loadSystemHealth(signal?: AbortSignal): Promise<SystemHealthLoadResult> {
  try {
    const status = await fetchSystemHealth(signal)
    return { status, error: '' }
  } catch (error) {
    return {
      status: null,
      error:
        isAxiosError(error) && error.response?.status === 401
          ? 'Stato sistema non autorizzato'
          : 'Stato sistema non disponibile',
    }
  }
}