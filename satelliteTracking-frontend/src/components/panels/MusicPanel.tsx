import { useMemo } from 'react'
import { useMusicPlayer } from './MusicPlayerContext'
import { MUSIC_FLOATING_WIDGET_FIXED_POSITION } from '../../helpers/musicPlayerHelpers'
import '../../styles/panels/music-panel.css'

function formatTime(totalSeconds: number) {
  if (!Number.isFinite(totalSeconds) || totalSeconds <= 0) {
    return '0:00'
  }

  const minutes = Math.floor(totalSeconds / 60)
  const seconds = Math.floor(totalSeconds % 60)
  return `${minutes}:${seconds.toString().padStart(2, '0')}`
}

export function MusicPanel() {
  const {
    playlists,
    selectedPlaylist,
    selectedPlaylistId,
    selectedTrackIndex,
    isPlaying,
    volume,
    currentTime,
    duration,
    statusMessage,
    error,
    currentTrack,
    floatingWidgetCollapsed,
    handleImportFolder,
    setSelectedPlaylistId,
    setSelectedTrackIndex,
    setFloatingWidgetCollapsed,
    setVolume,
    togglePlay,
    previousTrack,
    nextTrack,
    seekTo,
  } = useMusicPlayer()

  const selectedTrackLabel = useMemo(() => {
    if (!selectedPlaylist) {
      return 'Carica una cartella per iniziare'
    }

    return `${selectedPlaylist.label} · ${selectedPlaylist.tracks.length} brani`
  }, [selectedPlaylist])

  return (
    <section className="collapsible side-drawer music-panel" aria-label="Lettore musicale">
      <h3>Musica</h3>

      <button
        type="button"
        className={`music-panel-widget-tab ${floatingWidgetCollapsed ? 'is-highlighted' : ''}`}
        onClick={() => setFloatingWidgetCollapsed(false)}
        disabled={!floatingWidgetCollapsed}
      >
        {floatingWidgetCollapsed ? 'Riapri widget radio' : 'Widget radio aperto'}
      </button>

      <div className="music-import-card">
        <p className="music-note">
          Seleziona una cartella con sottocartelle playlist e file audio. Se non ci sono sottocartelle, i brani trovati finiscono in una playlist unica.
        </p>
        <label className="music-folder-picker">
          <span>Importa cartella musica</span>
          <input
            type="file"
            multiple
            accept="audio/*,.mp3,.m4a,.ogg"
            onChange={handleImportFolder}
            {...({ webkitdirectory: '', directory: '' } as unknown as Record<string, string>)}
          />
        </label>
        {statusMessage ? <p className="music-status">{statusMessage}</p> : null}
        {error ? <p className="music-error">{error}</p> : null}
      </div>

      <div className="music-toolbar">
        <label className="music-select-row">
          <span>Playlist</span>
          <select
            value={selectedPlaylist?.id ?? selectedPlaylistId}
            onChange={(event) => {
              const playlistId = event.target.value
              setSelectedPlaylistId(playlistId)
              setSelectedTrackIndex(0)
            }}
          >
            <option value="">Nessuna playlist</option>
            {playlists.map((playlist) => (
              <option key={playlist.id} value={playlist.id}>
                {playlist.label} ({playlist.tracks.length})
              </option>
            ))}
          </select>
        </label>

        <div className="music-transport">
          <button type="button" onClick={previousTrack} disabled={!currentTrack}>
            Indietro
          </button>
          <button
            type="button"
            className={isPlaying ? 'toggle-active' : ''}
            onClick={() => {
              void togglePlay()
            }}
            disabled={!currentTrack}
          >
            {isPlaying ? 'Pausa' : 'Play'}
          </button>
          <button type="button" onClick={nextTrack} disabled={!currentTrack}>
            Avanti
          </button>
        </div>
      </div>

      <section className="music-now-playing">
        <div className="music-now-playing-head">
          <strong>{currentTrack ? currentTrack.name : 'Nessun brano selezionato'}</strong>
          <span>{selectedTrackLabel}</span>
        </div>

        <div className="music-progress-row">
          <span>{formatTime(currentTime)}</span>
          <input
            type="range"
            min={0}
            max={duration || 0}
            step={0.1}
            value={Math.min(currentTime, duration || 0)}
            onChange={(event) => {
              seekTo(Number.parseFloat(event.target.value))
            }}
            disabled={!currentTrack || duration <= 0}
          />
          <span>{formatTime(duration)}</span>
        </div>

        <label className="music-volume-row">
          <span>Volume</span>
          <input
            type="range"
            min={0}
            max={1}
            step={0.01}
            value={volume}
            onChange={(event) => {
              setVolume(Number.parseFloat(event.target.value))
            }}
          />
          <span>{Math.round(volume * 100)}%</span>
        </label>
      </section>

      <div className="music-tracklist" aria-label="Lista brani">
        {selectedPlaylist && selectedPlaylist.tracks.length > 0 ? (
          selectedPlaylist.tracks.map((track, index) => (
            <button
              key={track.id}
              type="button"
              className={`music-track-item ${index === selectedTrackIndex ? 'is-active' : ''}`}
              onClick={() => {
                setSelectedTrackIndex(index)
              }}
            >
              <span>{track.name}</span>
              <small>{track.fileName}</small>
            </button>
          ))
        ) : (
          <p className="music-empty-state">
            Nessuna playlist caricata. Seleziona una cartella che contenga MP3.
          </p>
        )}
      </div>
    </section>
  )
}

export function MusicFloatingPlayer() {
  const {
    selectedPlaylist,
    isPlaying,
    volume,
    currentTime,
    duration,
    statusMessage,
    error,
    currentTrack,
    currentTrackTitle,
    currentTrackTitleIsLong,
    floatingWidgetCollapsed,
    setFloatingWidgetCollapsed,
    setVolume,
    togglePlay,
    previousTrack,
    nextTrack,
    seekTo,
  } = useMusicPlayer()

  const progress = duration > 0 ? Math.min(currentTime / duration, 1) : 0

  if (floatingWidgetCollapsed) {
    return (
      <aside
        className="music-float-player is-collapsed"
        style={MUSIC_FLOATING_WIDGET_FIXED_POSITION}
        aria-label="Widget radio socchiuso"
      >
        <button
          type="button"
          className="music-float-peek-tab"
          onClick={() => setFloatingWidgetCollapsed(false)}
        >
          Radio
        </button>
      </aside>
    )
  }

  return (
    <aside className="music-float-player" style={MUSIC_FLOATING_WIDGET_FIXED_POSITION} aria-label="Controllo musicale flottante">
      <div className="music-float-grip">
        <span className="music-float-label">RADIO LINK</span>
        <button
          type="button"
          className="music-float-collapse-tab"
          onClick={() => setFloatingWidgetCollapsed(true)}
        >
          Socchiudi
        </button>
      </div>

      <div className="music-float-header">
        <div className="music-float-now">
          <strong className={currentTrackTitleIsLong ? 'is-marquee' : ''}>
            {currentTrackTitleIsLong ? (
              <span>
                <span>{currentTrackTitle}</span>
                <span aria-hidden="true">{currentTrackTitle}</span>
              </span>
            ) : (
              currentTrackTitle
            )}
          </strong>
          <p>
            {selectedPlaylist ? `${selectedPlaylist.label} · ${selectedPlaylist.tracks.length} brani` : 'Sidebar per scegliere la playlist'}
          </p>
        </div>
        <button
          type="button"
          className="music-float-play"
          onClick={() => {
            void togglePlay()
          }}
          disabled={!currentTrack}
        >
          {isPlaying ? 'II' : '▶'}
        </button>
      </div>

      <div className="music-float-controls">
        <button type="button" onClick={previousTrack} disabled={!currentTrack}>
          Prev
        </button>
        <button type="button" onClick={nextTrack} disabled={!currentTrack}>
          Next
        </button>
      </div>

      <div className="music-float-strip">
        <div className="music-float-timebar">
          <span>{formatTime(currentTime)}</span>
          <input
            className="music-float-seek"
            type="range"
            min={0}
            max={duration || 0}
            step={0.1}
            value={Math.min(currentTime, duration || 0)}
            onChange={(event) => {
              seekTo(Number.parseFloat(event.target.value))
            }}
            disabled={!currentTrack || duration <= 0}
          />
          <span>{formatTime(duration)}</span>
        </div>

        <div className="music-float-meter">
          <span>{Math.round(progress * 100)}%</span>
          <span>{Math.round(volume * 100)}%</span>
        </div>

        <input
          className="music-float-volume"
          type="range"
          min={0}
          max={1}
          step={0.01}
          value={volume}
          onChange={(event) => {
            setVolume(Number.parseFloat(event.target.value))
          }}
        />
      </div>

      <div className="music-float-footer">
        {statusMessage ? <span>{statusMessage}</span> : null}
        {error ? <strong>{error}</strong> : null}
      </div>
    </aside>
  )
}
