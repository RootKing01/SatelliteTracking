import { memo, useEffect, useState } from 'react'
import type { OrekitStatusResponse, SystemHealthResponse } from '../../api/systemStatusClient'

type PanelTopSectionProps = {
  username: string
  orekitStatus: OrekitStatusResponse | null
  orekitStatusLoading: boolean
  orekitStatusError: string
  systemHealth: SystemHealthResponse | null
  systemHealthLoading: boolean
  systemHealthError: string
  onLogout: () => void
}

function PanelTopSectionBase({
  username,
  orekitStatus,
  orekitStatusLoading,
  orekitStatusError,
  systemHealth,
  systemHealthLoading,
  systemHealthError,
  onLogout,
  onPingSystemHealth,
}: PanelTopSectionProps & { onPingSystemHealth?: () => void }) {
  const [notifCount, setNotifCount] = useState<number>(0)

  useEffect(() => {
    const handler = (ev: Event) => {
      const detail = (ev as CustomEvent<number>).detail
      setNotifCount(typeof detail === 'number' ? detail : 0)
    }
    window.addEventListener('communityNotificationsCount', handler as EventListener)
    return () => window.removeEventListener('communityNotificationsCount', handler as EventListener)
  }, [])

  const toggleNotifications = () => {
    window.dispatchEvent(new CustomEvent('toggleCommunityNotifications'))
  }
  return (
    <section className="panel-top-shell">
      <div className="panel-header">
        <h1>Satellite Tracker</h1>
        <div style={{display: 'flex', gap: 8, alignItems: 'center'}}>
          <button
            type="button"
            className="panel-notifications"
            onClick={toggleNotifications}
            aria-label={`Notifiche community, ${notifCount} non lette`}
          >
            Notifiche{notifCount > 0 ? <span style={{marginLeft:8}} className="panel-badge">{notifCount}</span> : null}
          </button>

          <button
            type="button"
            className="panel-logout"
            onClick={onLogout}
          >
            Logout ({username})
          </button>
        </div>
      </div>

      <div className="panel-status-row">
        <span className="panel-badge">
          <span className="live-dot" />
          Live
        </span>
        <span
          className={`orekit-badge ${
            orekitStatus?.orekitDataLoaded
              ? 'orekit-badge-loaded'
              : orekitStatusError
                ? 'orekit-badge-error'
                : orekitStatusLoading
                  ? 'orekit-badge-pending'
                  : 'orekit-badge-fallback'
          }`}
          title={
            orekitStatus
              ? `Path: ${orekitStatus.orekitDataPath}`
              : orekitStatusError || 'Stato Orekit non disponibile'
          }
        >
          <span className="orekit-dot" />
          {orekitStatus?.orekitDataLoaded
            ? 'Orekit ON'
            : orekitStatusError
              ? 'Orekit N/A'
              : orekitStatusLoading
                ? 'Orekit ...'
                : 'Orekit OFF'}
        </span>
        <span
          className={`system-health-badge ${
            systemHealth?.status === 'UP'
              ? 'system-health-up'
              : systemHealth?.status === 'DEGRADED'
                ? 'system-health-degraded'
                : systemHealthError
                  ? 'system-health-error'
                  : systemHealthLoading
                    ? 'system-health-pending'
                    : 'system-health-down'
          }`}
          title={
            systemHealth
              ? `API: ${systemHealth.components.api}, DB: ${systemHealth.components.database}, Orekit: ${systemHealth.components.orekit}`
              : systemHealthError || 'Stato sistema non disponibile'
          }
        >
          <span className="system-health-dot" />
          {systemHealth?.status
            ? `System ${systemHealth.status}`
            : systemHealthError
              ? 'System N/A'
              : systemHealthLoading
                ? 'System ...'
                : 'System DOWN'}
        </span>
        <span
          className={`system-health-badge system-health-ping-minimal ${
            systemHealthLoading
              ? 'system-health-pending'
              : systemHealth?.status === 'UP'
                ? 'system-health-up'
                : systemHealth?.status === 'DEGRADED'
                  ? 'system-health-degraded'
                  : systemHealth?.status === 'DOWN'
                    ? 'system-health-down'
                    : systemHealthError
                      ? 'system-health-error'
                      : 'system-health-pending'
          }`}
          title="Ping System Health"
          onClick={systemHealthLoading ? undefined : onPingSystemHealth}
          style={{ cursor: systemHealthLoading ? 'wait' : 'pointer', userSelect: 'none' }}
        >
          <span className="system-health-dot" />
          Check Health
        </span>
      </div>
    </section>
  )
}

export const PanelTopSection = memo(PanelTopSectionBase)
