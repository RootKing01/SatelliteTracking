export type ImportedTrack = {
  id: string
  name: string
  fileName: string
  relativePath: string
  url: string
}

export type ImportedPlaylist = {
  id: string
  label: string
  tracks: ImportedTrack[]
}

type PlaylistDraftTrack = {
  id: string
  name: string
  fileName: string
  relativePath: string
  file: File
}

type PlaylistDraft = {
  id: string
  label: string
  tracks: PlaylistDraftTrack[]
}

type StoredTrack = {
  id: string
  name: string
  fileName: string
  relativePath: string
  dataUrl: string
}

type StoredPlaylist = {
  id: string
  label: string
  tracks: StoredTrack[]
}

type StoredMusicCache = {
  version: 1
  savedAtUtc: string
  playlists: StoredPlaylist[]
}

type StoredMusicState = {
  selectedPlaylistId: string
  selectedTrackIndex: number
  volume: number
}

export type MusicImportBuildResult = {
  playlists: ImportedPlaylist[]
  useFlatPlaylist: boolean
  importedAudioCount: number
  totalFileCount: number
}

export const MUSIC_CACHE_KEY = 'satelliteTracker.music.cache.v1'
export const MUSIC_STATE_KEY = 'satelliteTracker.music.state.v1'

function buildPlaylistId(label: string, index: number) {
  return `${label.toLowerCase().replace(/[^a-z0-9]+/g, '-')}-${index}`
}

function getDisplayName(fileName: string) {
  return fileName.replace(/\.[^.]+$/, '')
}

function filterAudioFiles(files: File[]) {
  return files.filter((file) => {
    const lowerName = file.name.toLowerCase()
    return (
      file.type.startsWith('audio/') ||
      lowerName.endsWith('.mp3') ||
      lowerName.endsWith('.m4a') ||
      lowerName.endsWith('.ogg')
    )
  })
}

function buildPlaylistDrafts(audioFiles: File[]): { drafts: PlaylistDraft[]; useFlatPlaylist: boolean } {
  const folderNames = new Set<string>()
  const trackEntries = audioFiles.map((file) => {
    const relativePath =
      (file as File & { webkitRelativePath?: string }).webkitRelativePath || file.name
    const parts = relativePath.split('/').filter(Boolean)
    const folderName = parts.length > 1 ? parts[0] : ''
    if (folderName) {
      folderNames.add(folderName)
    }

    return {
      relativePath,
      folderName,
      file,
    }
  })

  const groupedTracks = new Map<string, PlaylistDraftTrack[]>()
  const useFlatPlaylist = folderNames.size === 0
  const fallbackPlaylistLabel = 'Brani trovati'

  for (const { relativePath, folderName, file } of trackEntries) {
    const playlistLabel = useFlatPlaylist ? fallbackPlaylistLabel : folderName || fallbackPlaylistLabel
    const track: PlaylistDraftTrack = {
      id: `${relativePath}-${file.size}-${file.lastModified}`,
      name: getDisplayName(file.name),
      fileName: file.name,
      relativePath,
      file,
    }

    const currentTracks = groupedTracks.get(playlistLabel)
    if (currentTracks) {
      currentTracks.push(track)
    } else {
      groupedTracks.set(playlistLabel, [track])
    }
  }

  const drafts = Array.from(groupedTracks.entries())
    .sort(([leftLabel], [rightLabel]) => leftLabel.localeCompare(rightLabel))
    .map(([label, tracks], index) => ({
      id: buildPlaylistId(label, index),
      label,
      tracks: tracks.sort((leftTrack, rightTrack) =>
        leftTrack.relativePath.localeCompare(rightTrack.relativePath),
      ),
    }))

  return { drafts, useFlatPlaylist }
}

function toRuntimePlaylist(draft: PlaylistDraft): ImportedPlaylist {
  return {
    id: draft.id,
    label: draft.label,
    tracks: draft.tracks.map((track) => ({
      id: track.id,
      name: track.name,
      fileName: track.fileName,
      relativePath: track.relativePath,
      url: URL.createObjectURL(track.file),
    })),
  }
}

function fileToDataUrl(file: File): Promise<string> {
  return new Promise((resolve, reject) => {
    const reader = new FileReader()
    reader.onload = () => {
      const value = reader.result
      if (typeof value !== 'string') {
        reject(new Error('Invalid file data'))
        return
      }
      resolve(value)
    }
    reader.onerror = () => reject(reader.error ?? new Error('Unable to read file'))
    reader.readAsDataURL(file)
  })
}

async function toStoredPlaylist(draft: PlaylistDraft): Promise<StoredPlaylist> {
  const tracks: StoredTrack[] = []
  for (const track of draft.tracks) {
    const dataUrl = await fileToDataUrl(track.file)
    tracks.push({
      id: track.id,
      name: track.name,
      fileName: track.fileName,
      relativePath: track.relativePath,
      dataUrl,
    })
  }

  return {
    id: draft.id,
    label: draft.label,
    tracks,
  }
}

export function buildPlaylistsFromFiles(files: File[]): MusicImportBuildResult {
  const audioFiles = filterAudioFiles(files)
  if (audioFiles.length === 0) {
    return {
      playlists: [],
      useFlatPlaylist: false,
      importedAudioCount: 0,
      totalFileCount: files.length,
    }
  }

  const { drafts, useFlatPlaylist } = buildPlaylistDrafts(audioFiles)
  return {
    playlists: drafts.map(toRuntimePlaylist),
    useFlatPlaylist,
    importedAudioCount: audioFiles.length,
    totalFileCount: files.length,
  }
}

export async function persistPlaylistsToLocalStorage(playlists: ImportedPlaylist[]) {
  if (typeof window === 'undefined') {
    return false
  }

  try {
    const fileByTrackId = new Map<string, File>()
    const draftPlaylists: PlaylistDraft[] = []

    // Rebuild temporary draft data using fetch on object URLs.
    for (const playlist of playlists) {
      const draftTracks: PlaylistDraftTrack[] = []
      for (const track of playlist.tracks) {
        let file = fileByTrackId.get(track.id)
        if (!file) {
          const response = await fetch(track.url)
          const blob = await response.blob()
          file = new File([blob], track.fileName, { type: blob.type || 'audio/mpeg' })
          fileByTrackId.set(track.id, file)
        }

        draftTracks.push({
          id: track.id,
          name: track.name,
          fileName: track.fileName,
          relativePath: track.relativePath,
          file,
        })
      }

      draftPlaylists.push({
        id: playlist.id,
        label: playlist.label,
        tracks: draftTracks,
      })
    }

    const storedPlaylists: StoredPlaylist[] = []
    for (const draft of draftPlaylists) {
      storedPlaylists.push(await toStoredPlaylist(draft))
    }

    const payload: StoredMusicCache = {
      version: 1,
      savedAtUtc: new Date().toISOString(),
      playlists: storedPlaylists,
    }

    window.localStorage.setItem(MUSIC_CACHE_KEY, JSON.stringify(payload))
    return true
  } catch {
    return false
  }
}

export function restorePlaylistsFromLocalStorage(): ImportedPlaylist[] {
  if (typeof window === 'undefined') {
    return []
  }

  try {
    const rawPayload = window.localStorage.getItem(MUSIC_CACHE_KEY)
    if (!rawPayload) {
      return []
    }

    const parsed = JSON.parse(rawPayload) as StoredMusicCache
    if (parsed.version !== 1 || !Array.isArray(parsed.playlists)) {
      return []
    }

    return parsed.playlists.map((playlist) => ({
      id: playlist.id,
      label: playlist.label,
      tracks: playlist.tracks.map((track) => ({
        id: track.id,
        name: track.name,
        fileName: track.fileName,
        relativePath: track.relativePath,
        url: track.dataUrl,
      })),
    }))
  } catch {
    return []
  }
}

export function revokePlaylistUrls(playlists: ImportedPlaylist[]) {
  for (const playlist of playlists) {
    for (const track of playlist.tracks) {
      if (track.url.startsWith('blob:')) {
        URL.revokeObjectURL(track.url)
      }
    }
  }
}

export function persistMusicStateToLocalStorage(state: StoredMusicState) {
  if (typeof window === 'undefined') {
    return
  }

  try {
    window.localStorage.setItem(MUSIC_STATE_KEY, JSON.stringify(state))
  } catch {
    // Ignore storage failures.
  }
}

export function restoreMusicStateFromLocalStorage(): StoredMusicState | null {
  if (typeof window === 'undefined') {
    return null
  }

  try {
    const rawPayload = window.localStorage.getItem(MUSIC_STATE_KEY)
    if (!rawPayload) {
      return null
    }

    const parsed = JSON.parse(rawPayload) as StoredMusicState
    if (
      typeof parsed.selectedPlaylistId !== 'string' ||
      typeof parsed.selectedTrackIndex !== 'number' ||
      typeof parsed.volume !== 'number'
    ) {
      return null
    }

    return parsed
  } catch {
    return null
  }
}
