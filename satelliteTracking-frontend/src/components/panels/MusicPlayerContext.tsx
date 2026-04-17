import { createContext, useContext, useEffect, useMemo, useRef, useState, type ChangeEvent, type ReactNode, type RefObject } from 'react'
import {
  buildPlaylistsFromFiles,
  persistMusicStateToLocalStorage,
  persistPlaylistsToLocalStorage,
  restoreMusicStateFromLocalStorage,
  restorePlaylistsFromLocalStorage,
  revokePlaylistUrls,
  type ImportedPlaylist,
  type ImportedTrack,
} from '../../helpers/musicPlayerHelpers'

type MusicPlayerContextValue = {
  playlists: ImportedPlaylist[]
  selectedPlaylistId: string
  selectedTrackIndex: number
  isPlaying: boolean
  volume: number
  currentTime: number
  duration: number
  statusMessage: string
  error: string
  selectedPlaylist: ImportedPlaylist | null
  currentTrack: ImportedTrack | null
  hasPlaylists: boolean
  handleImportFolder: (event: ChangeEvent<HTMLInputElement>) => void
  setSelectedPlaylistId: (value: string) => void
  setSelectedTrackIndex: (value: number) => void
  setVolume: (value: number) => void
  togglePlay: () => Promise<void>
  previousTrack: () => void
  nextTrack: () => void
  seekTo: (value: number) => void
  audioRef: RefObject<HTMLAudioElement | null>
}

const MusicPlayerContext = createContext<MusicPlayerContextValue | null>(null)

export function MusicPlayerProvider({ children }: { children: ReactNode }) {
  const audioRef = useRef<HTMLAudioElement>(null)
  const [playlists, setPlaylists] = useState<ImportedPlaylist[]>([])
  const [selectedPlaylistId, setSelectedPlaylistId] = useState('')
  const [selectedTrackIndex, setSelectedTrackIndex] = useState(0)
  const [isPlaying, setIsPlaying] = useState(false)
  const [volume, setVolumeState] = useState(0.85)
  const [currentTime, setCurrentTime] = useState(0)
  const [duration, setDuration] = useState(0)
  const [statusMessage, setStatusMessage] = useState('Seleziona una cartella di playlist con mp3.')
  const [error, setError] = useState('')

  useEffect(() => {
    const restoredPlaylists = restorePlaylistsFromLocalStorage()
    const restoredState = restoreMusicStateFromLocalStorage()

    if (restoredPlaylists.length > 0) {
      setPlaylists(restoredPlaylists)
      setSelectedPlaylistId(restoredState?.selectedPlaylistId ?? restoredPlaylists[0]?.id ?? '')
      setSelectedTrackIndex(Math.max(0, restoredState?.selectedTrackIndex ?? 0))
      setVolumeState(
        restoredState && Number.isFinite(restoredState.volume)
          ? Math.max(0, Math.min(1, restoredState.volume))
          : 0.85,
      )
      setStatusMessage('Playlist ripristinate dalla sessione precedente.')
    }
  }, [])

  const selectedPlaylist = useMemo(
    () => playlists.find((playlist) => playlist.id === selectedPlaylistId) ?? playlists[0] ?? null,
    [playlists, selectedPlaylistId],
  )

  const currentTrack = useMemo(
    () => selectedPlaylist?.tracks[selectedTrackIndex] ?? null,
    [selectedPlaylist, selectedTrackIndex],
  )

  useEffect(() => {
    return () => {
      revokePlaylistUrls(playlists)
    }
  }, [playlists])

  useEffect(() => {
    persistMusicStateToLocalStorage({
      selectedPlaylistId,
      selectedTrackIndex,
      volume,
    })
  }, [selectedPlaylistId, selectedTrackIndex, volume])

  useEffect(() => {
    const audio = audioRef.current
    if (!audio || !currentTrack) {
      return
    }

    audio.src = currentTrack.url
    audio.load()
    setCurrentTime(0)

    if (isPlaying) {
      void audio.play().catch(() => {
        setIsPlaying(false)
        setError('Impossibile avviare la riproduzione. Premi play manualmente.')
      })
    }
  }, [currentTrack?.url, isPlaying])

  useEffect(() => {
    if (selectedPlaylist && selectedTrackIndex >= selectedPlaylist.tracks.length) {
      setSelectedTrackIndex(0)
    }
  }, [selectedPlaylist, selectedTrackIndex])

  const handleImportFolder = (event: ChangeEvent<HTMLInputElement>) => {
    try {
      const files = Array.from(event.target.files ?? [])
      event.target.value = ''

      if (files.length === 0) {
        return
      }

      const result = buildPlaylistsFromFiles(files)
      if (result.importedAudioCount === 0) {
        setError('Nessun file audio trovato nella cartella selezionata.')
        setStatusMessage('Importa una cartella che contenga mp3.')
        return
      }

      setPlaylists(result.playlists)
      setSelectedPlaylistId(result.playlists[0]?.id ?? '')
      setSelectedTrackIndex(0)
      setCurrentTime(0)
      setDuration(0)
      setError('')
      setIsPlaying(false)
      setStatusMessage(
        result.useFlatPlaylist
          ? `Importati ${result.importedAudioCount} brani in una playlist unica.`
          : `Importate ${result.playlists.length} playlist da ${result.totalFileCount} file.`,
      )

      void persistPlaylistsToLocalStorage(result.playlists).then((persisted) => {
        if (!persisted) {
          setStatusMessage((previous) => `${previous} Cache locale non disponibile per questa libreria.`)
        }
      })
    } catch {
      setError('Impossibile importare la cartella musicale selezionata.')
      setStatusMessage('Controlla che la cartella contenga file audio leggibili.')
    }
  }

  const togglePlay = async () => {
    const audio = audioRef.current
    if (!audio || !currentTrack) {
      return
    }

    if (isPlaying) {
      audio.pause()
      return
    }

    try {
      await audio.play()
    } catch {
      setError('Riproduzione bloccata: prova a premere di nuovo Play.')
    }
  }

  const previousTrack = () => {
    if (!selectedPlaylist || selectedPlaylist.tracks.length === 0) {
      return
    }

    setSelectedTrackIndex((prev) => (prev - 1 + selectedPlaylist.tracks.length) % selectedPlaylist.tracks.length)
  }

  const nextTrack = () => {
    if (!selectedPlaylist || selectedPlaylist.tracks.length === 0) {
      return
    }

    setSelectedTrackIndex((prev) => (prev + 1) % selectedPlaylist.tracks.length)
  }

  const seekTo = (value: number) => {
    const audio = audioRef.current
    if (!audio || !Number.isFinite(value)) {
      return
    }

    audio.currentTime = value
    setCurrentTime(value)
  }

  const setVolume = (value: number) => {
    const audio = audioRef.current
    if (!audio || !Number.isFinite(value)) {
      return
    }

    audio.volume = value
    setVolumeState(value)
  }

  const value: MusicPlayerContextValue = {
    playlists,
    selectedPlaylistId,
    selectedTrackIndex,
    isPlaying,
    volume,
    currentTime,
    duration,
    statusMessage,
    error,
    selectedPlaylist,
    currentTrack,
    hasPlaylists: playlists.length > 0,
    handleImportFolder,
    setSelectedPlaylistId,
    setSelectedTrackIndex,
    setVolume,
    togglePlay,
    previousTrack,
    nextTrack,
    seekTo,
    audioRef,
  }

  return (
    <MusicPlayerContext.Provider value={value}>
      {children}
      <audio
        ref={audioRef}
        preload="metadata"
        onPlay={() => setIsPlaying(true)}
        onPause={() => setIsPlaying(false)}
        onEnded={nextTrack}
        onLoadedMetadata={() => {
          const audio = audioRef.current
          if (audio) {
            setDuration(Number.isFinite(audio.duration) ? audio.duration : 0)
          }
        }}
        onTimeUpdate={() => {
          const audio = audioRef.current
          if (audio) {
            setCurrentTime(audio.currentTime)
            setDuration(Number.isFinite(audio.duration) ? audio.duration : 0)
          }
        }}
        onError={() => {
          setError('Errore nella riproduzione del file audio selezionato.')
          setIsPlaying(false)
        }}
        onVolumeChange={() => {
          const audio = audioRef.current
          if (audio) {
            setVolumeState(audio.volume)
          }
        }}
      />
    </MusicPlayerContext.Provider>
  )
}

export function useMusicPlayer() {
  const context = useContext(MusicPlayerContext)
  if (!context) {
    throw new Error('useMusicPlayer must be used within MusicPlayerProvider')
  }

  return context
}
