import { httpClient } from './httpClient'

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

export async function fetchSystemHealth(signal?: AbortSignal): Promise<SystemHealthResponse> {
  const response = await httpClient.get<SystemHealthResponse>('/api/system/health', {
    signal,
  })
  return response.data
}
