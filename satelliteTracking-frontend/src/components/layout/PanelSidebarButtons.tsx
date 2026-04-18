export type SidebarPane = 'groups' | 'satellites' | 'visibility' | 'sightings' | 'music' | 'community'

type PanelSidebarButtonsProps = {
  openPane: SidebarPane | null
  onTogglePane: (pane: SidebarPane) => void
}

type SidebarTabIconProps = {
  variant: SidebarPane
}

function SidebarTabIcon({ variant }: SidebarTabIconProps) {
  switch (variant) {
    case 'groups':
      return (
        <svg viewBox="0 0 24 24" aria-hidden="true" focusable="false">
          <circle cx="5" cy="8" r="1.7" />
          <circle cx="12" cy="4.5" r="1.7" />
          <circle cx="19" cy="9" r="1.7" />
          <circle cx="8" cy="17" r="1.7" />
          <circle cx="17" cy="16" r="1.7" />
          <path d="M6.5 8.1 10.6 5.4" />
          <path d="M13.5 5.1 17.5 8.1" />
          <path d="M6.8 9.7 8.8 15" />
          <path d="M18.2 10.5 17.2 14.3" />
        </svg>
      )
    case 'satellites':
      return (
        <svg viewBox="0 0 24 24" aria-hidden="true" focusable="false">
          <circle cx="12" cy="12" r="5.2" />
          <circle cx="12" cy="12" r="1.8" />
          <path d="M12 2.5v3" />
          <path d="M12 18.5v3" />
          <path d="M2.5 12h3" />
          <path d="M18.5 12h3" />
          <path d="M5.1 5.1l2.1 2.1" />
          <path d="M16.8 16.8l2.1 2.1" />
          <path d="M18.9 5.1l-2.1 2.1" />
          <path d="M7.2 16.8 5.1 18.9" />
        </svg>
      )
    case 'visibility':
      return (
        <svg viewBox="0 0 24 24" aria-hidden="true" focusable="false">
          <path d="M2.8 12s3.5-6.2 9.2-6.2S21.2 12 21.2 12 17.7 18.2 12 18.2 2.8 12 2.8 12Z" />
          <circle cx="12" cy="12" r="3.2" />
        </svg>
      )
    case 'sightings':
      return (
        <svg viewBox="0 0 24 24" aria-hidden="true" focusable="false">
          <path d="M5 8.5h4.2l1 7H5.8L5 8.5Z" />
          <path d="M14.8 8.5H19l-.8 7h-4.4l1-7Z" />
          <path d="M9.2 10.5h5.6" />
          <path d="M12 8.5v7" />
          <path d="M5 11.6h14" />
        </svg>
      )
    case 'community':
      return (
        <svg viewBox="0 0 24 24" aria-hidden="true" focusable="false">
          <path d="M4.5 6.8h10.2a2 2 0 0 1 2 2v4.2a2 2 0 0 1-2 2H11l-3.5 2.7V15H4.5a2 2 0 0 1-2-2V8.8a2 2 0 0 1 2-2Z" />
          <path d="M14.5 9.2h5a2 2 0 0 1 2 2v3a2 2 0 0 1-2 2h-1.9V18l-2.6-1.8" />
        </svg>
      )
    case 'music':
      return (
        <svg viewBox="0 0 24 24" aria-hidden="true" focusable="false">
          <path d="M14.2 4.5v10.1" />
          <path d="M14.2 4.5 19 3.1v10.1" />
          <circle cx="9.1" cy="16.2" r="2.8" />
          <circle cx="16.8" cy="14.8" r="2.8" />
          <path d="M16.8 14.8V5.1" />
        </svg>
      )
    default:
      return null
  }
}

export function PanelSidebarButtons({ openPane, onTogglePane }: PanelSidebarButtonsProps) {
  return (
    <nav className="sidebar-tabs" aria-label="Pannelli laterali">
      <button
        type="button"
        className={openPane === 'groups' ? 'tab-active' : ''}
        aria-expanded={openPane === 'groups'}
        aria-controls="panel-groups"
        onClick={() => onTogglePane('groups')}
        title="Costellazioni"
      >
        <span className="tab-icon tab-icon-constellation" aria-hidden="true">
          <SidebarTabIcon variant="groups" />
        </span>
      </button>
      <button
        type="button"
        className={openPane === 'satellites' ? 'tab-active' : ''}
        aria-expanded={openPane === 'satellites'}
        aria-controls="panel-satellites"
        onClick={() => onTogglePane('satellites')}
        title="Gestione vista"
      >
        <span className="tab-icon tab-icon-view" aria-hidden="true">
          <SidebarTabIcon variant="satellites" />
        </span>
      </button>
      <button
        type="button"
        className={openPane === 'visibility' ? 'tab-active' : ''}
        aria-expanded={openPane === 'visibility'}
        aria-controls="panel-visibility"
        onClick={() => onTogglePane('visibility')}
        title="Visibilita"
      >
        <span className="tab-icon tab-icon-visibility" aria-hidden="true">
          <SidebarTabIcon variant="visibility" />
        </span>
      </button>
      <button
        type="button"
        className={openPane === 'sightings' ? 'tab-active' : ''}
        aria-expanded={openPane === 'sightings'}
        aria-controls="panel-sightings"
        onClick={() => onTogglePane('sightings')}
        title="Avvistamenti"
      >
        <span className="tab-icon tab-icon-sighting" aria-hidden="true">
          <SidebarTabIcon variant="sightings" />
        </span>
      </button>
      <button
        type="button"
        className={openPane === 'community' ? 'tab-active' : ''}
        aria-expanded={openPane === 'community'}
        aria-controls="panel-community"
        onClick={() => onTogglePane('community')}
        title="Community"
      >
        <span className="tab-icon tab-icon-community" aria-hidden="true">
          <SidebarTabIcon variant="community" />
        </span>
      </button>
      <button
        type="button"
        className={openPane === 'music' ? 'tab-active' : ''}
        aria-expanded={openPane === 'music'}
        aria-controls="panel-music"
        onClick={() => onTogglePane('music')}
        title="Musica"
      >
        <span className="tab-icon tab-icon-music" aria-hidden="true">
          <SidebarTabIcon variant="music" />
        </span>
      </button>
    </nav>
  )
}
