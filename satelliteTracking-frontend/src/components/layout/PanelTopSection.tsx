import { memo } from 'react'
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
  notificationCount: number
  onToggleNotifications: () => void
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
  notificationCount,
  onToggleNotifications,
  onPingSystemHealth,
}: PanelTopSectionProps & { onPingSystemHealth?: () => void }) {
  return (
    <section className="panel-top-shell">
      <div className="panel-header">
        <h1>Satellite Tracker</h1>
        <div style={{display: 'flex', gap: 8, alignItems: 'center'}}>
          <button
            type="button"
            className="panel-notifications"
            onClick={onToggleNotifications}
            aria-label={`Notifiche community, ${notificationCount} non lette`}
          >
            Notifiche{notificationCount > 0 ? <span style={{marginLeft:8}} className="panel-badge">{notificationCount}</span> : null}
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
          className={`status-badge ${
            orekitStatus?.orekitDataLoaded
              ? 'status-badge--success'
              : orekitStatusError
                ? 'status-badge--error'
                : orekitStatusLoading
                  ? 'status-badge--pending'
                  : 'status-badge--warning'
          }`}
          title={
            orekitStatus
              ? `Path: ${orekitStatus.orekitDataPath}`
              : orekitStatusError || 'Stato Orekit non disponibile'
          }
        >
          <span className="status-badge-dot" />
          {orekitStatus?.orekitDataLoaded
            ? 'Orekit ON'
            : orekitStatusError
              ? 'Orekit N/A'
              : orekitStatusLoading
                ? 'Orekit ...'
                : 'Orekit OFF'}
        </span>
        <span
          className={`status-badge ${
            systemHealth?.status === 'UP'
              ? 'status-badge--success'
              : systemHealth?.status === 'DEGRADED'
                ? 'status-badge--warning'
                : systemHealthError
                  ? 'status-badge--error'
                  : systemHealthLoading
                    ? 'status-badge--pending'
                    : 'status-badge--error'
          }`}
          title={
            systemHealth
              ? `API: ${systemHealth.components.api}, DB: ${systemHealth.components.database}, Orekit: ${systemHealth.components.orekit}`
              : systemHealthError || 'Stato sistema non disponibile'
          }
        >
          <span className="status-badge-dot" />
          {systemHealth?.status
            ? `System ${systemHealth.status}`
            : systemHealthError
              ? 'System N/A'
              : systemHealthLoading
                ? 'System ...'
                : 'System DOWN'}
        </span>
        <span
          className={`status-badge status-badge--minimal ${
            systemHealthLoading
              ? 'status-badge--pending'
              : systemHealth?.status === 'UP'
                ? 'status-badge--success'
                : systemHealth?.status === 'DEGRADED'
                  ? 'status-badge--warning'
                  : systemHealth?.status === 'DOWN'
                    ? 'status-badge--error'
                    : systemHealthError
                      ? 'status-badge--error'
                      : 'status-badge--pending'
          }`}
          title="Ping System Health"
          onClick={systemHealthLoading ? undefined : onPingSystemHealth}
          aria-disabled={systemHealthLoading}
        >
          <span className="status-badge-dot" />
          Controlla salute
        </span>
      </div>
    </section>
  )
}

export const PanelTopSection = memo(PanelTopSectionBase)
