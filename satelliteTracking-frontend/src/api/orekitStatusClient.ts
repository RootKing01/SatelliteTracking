import { httpClient } from './httpClient'

export type OrekitStatusResponse = {
  orekitDataLoaded: boolean
  orekitDataPath: string
  status: 'loaded' | 'fallback'
  checkedAt: string
}

export async function fetchOrekitStatus(signal?: AbortSignal): Promise<OrekitStatusResponse> {
  const response = await httpClient.get<OrekitStatusResponse>('/api/system/orekit-status', {
    signal,
  })
  return response.data
}
