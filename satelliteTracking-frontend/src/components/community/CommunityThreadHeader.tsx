import type { CommunityThread, CommunityThreadReadState } from '../../api/communityClient'

type CommunityThreadHeaderProps = {
  activeThread: CommunityThread | null
  readState: CommunityThreadReadState | null
  activeTarget: { targetType: string; targetId: string; label: string } | null
  selectedSatelliteName: string | null
  commentsCount: number
  commentsLoading: boolean
  handleToggleLike: (threadId: number) => void
  handleResumeReading: () => void
  handleGoToLatest: () => void
  ensureThread: (targetType: string, targetId: string) => void
  onFocusSatellite: (satelliteId: number) => void
}

export function CommunityThreadHeader({
  activeThread,
  readState,
  activeTarget,
  selectedSatelliteName,
  commentsCount,
  commentsLoading,
  handleToggleLike,
  handleResumeReading,
  handleGoToLatest,
  ensureThread,
  onFocusSatellite,
}: CommunityThreadHeaderProps) {
  return (
    <>
      <strong>
        {activeThread
          ? (
              activeThread.targetType === 'SATELLITE' && selectedSatelliteName
                ? `Thread attivo: ${selectedSatelliteName}`
                : `Thread attivo: ${activeThread.title}`
            )
          : activeTarget
            ? `Nessun thread aperto. Premi "Apri o crea thread satellite" per ${activeTarget.label}`
            : 'Seleziona un thread dall\'elenco oppure un satellite'}
      </strong>

      {activeThread ? (
        <div className="community-inline-actions">
          <button
            type="button"
            className={activeThread.likedByMe ? 'community-like-active' : ''}
            onClick={() => handleToggleLike(activeThread.id)}
          >
            {activeThread.likedByMe ? 'Unlike' : 'Like'} ({activeThread.likesCount})
          </button>
          {activeThread.targetType === 'SATELLITE' ? (
            <button
              type="button"
              className="community-focus-button"
              onClick={() => {
                const parsedId = Number(activeThread.targetId)
                if (Number.isFinite(parsedId)) onFocusSatellite(parsedId)
              }}
            >
              Metti a fuoco satellite
            </button>
          ) : null}
          <button
            type="button"
            className="community-read-button"
            onClick={handleResumeReading}
            disabled={!readState?.lastReadCommentId}
          >
            Riprendi lettura
          </button>
          <button
            type="button"
            className="community-read-button"
            onClick={handleGoToLatest}
            disabled={commentsCount === 0}
          >
            Vai all'ultimo messaggio
          </button>
        </div>
      ) : null}

      {activeTarget ? (
        <button
          type="button"
          className="community-thread-cta"
          disabled={commentsLoading}
          onClick={() => ensureThread(activeTarget.targetType, activeTarget.targetId)}
        >
          {commentsLoading ? 'Caricamento thread...' : 'Apri o crea thread satellite corrente'}
        </button>
      ) : null}
    </>
  )
}