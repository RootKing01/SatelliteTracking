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

type StoredMusicCacheManifestPlaylist = {
  id: string
  label: string
  trackKeys: string[]
}

type StoredMusicCacheManifest = {
  version: 3
  savedAtUtc: string
  storage: 'indexeddb'
  playlists: StoredMusicCacheManifestPlaylist[]
}

type StoredIndexedDbTrack = {
  id: string
  name: string
  fileName: string
  relativePath: string
  blob: Blob
}

type StoredMusicState = {
  selectedPlaylistId: string
  selectedTrackIndex: number
  volume: number
  floatingWidgetCollapsed?: boolean
}

export type MusicUiState = {
  selectedPlaylistId: string
  selectedTrackIndex: number
  volume: number
  floatingWidgetCollapsed: boolean
}

export type MusicImportBuildResult = {
  playlists: ImportedPlaylist[]
  useFlatPlaylist: boolean
  importedAudioCount: number
  totalFileCount: number
}

export const MUSIC_CACHE_KEY = 'satelliteTracker.music.cache.v1'
const MUSIC_CACHE_MANIFEST_KEY = 'satelliteTracker.music.cache.manifest.v3'
const MUSIC_CACHE_DB_NAME = 'satelliteTracker.music.db.v1'
const MUSIC_CACHE_STORE_NAME = 'tracks'
export const MUSIC_STATE_KEY = 'satelliteTracker.music.state.v1'
export const MUSIC_FLOATING_WIDGET_FIXED_POSITION = {
  left: 10,
  top: 58,
}

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

function buildCacheTrackKey(libraryId: string, playlistIndex: number, trackIndex: number) {
  return `${MUSIC_CACHE_DB_NAME}.${libraryId}.${playlistIndex}.${trackIndex}`
}

function buildCacheManifestKey() {
  return MUSIC_CACHE_MANIFEST_KEY
}

function hasIndexedDbSupport() {
  return typeof window !== 'undefined' && typeof window.indexedDB !== 'undefined'
}

function openMusicCacheDatabase() {
  return new Promise<IDBDatabase | null>((resolve) => {
    if (!hasIndexedDbSupport()) {
      resolve(null)
      return
    }

    const request = window.indexedDB.open(MUSIC_CACHE_DB_NAME, 1)
    request.onupgradeneeded = () => {
      request.result.createObjectStore(MUSIC_CACHE_STORE_NAME)
    }
    request.onerror = () => resolve(null)
    request.onsuccess = () => resolve(request.result)
  })
}

function getTransactionCompletion(transaction: IDBTransaction) {
  return new Promise<void>((resolve, reject) => {
    transaction.oncomplete = () => resolve()
    transaction.onabort = () => reject(transaction.error ?? new Error('IndexedDB transaction aborted'))
    transaction.onerror = () => reject(transaction.error ?? new Error('IndexedDB transaction failed'))
  })
}

function getIndexedDbRecord(database: IDBDatabase, key: string) {
  return new Promise<StoredIndexedDbTrack | null>((resolve, reject) => {
    const transaction = database.transaction(MUSIC_CACHE_STORE_NAME, 'readonly')
    const store = transaction.objectStore(MUSIC_CACHE_STORE_NAME)
    const request = store.get(key)
    request.onsuccess = () => resolve((request.result as StoredIndexedDbTrack | undefined) ?? null)
    request.onerror = () => reject(request.error ?? new Error('Unable to read indexed music cache entry'))
  })
}

async function removeIndexedDbRecords(keys: string[]) {
  const database = await openMusicCacheDatabase()
  if (!database) {
    return
  }

  const transaction = database.transaction(MUSIC_CACHE_STORE_NAME, 'readwrite')
  const store = transaction.objectStore(MUSIC_CACHE_STORE_NAME)
  for (const key of keys) {
    store.delete(key)
  }

  await getTransactionCompletion(transaction)
  database.close()
}

async function writeIndexedDbRecords(records: Array<{ key: string; track: StoredIndexedDbTrack }>) {
  const database = await openMusicCacheDatabase()
  if (!database) {
    return false
  }

  try {
    const transaction = database.transaction(MUSIC_CACHE_STORE_NAME, 'readwrite')
    const store = transaction.objectStore(MUSIC_CACHE_STORE_NAME)
    for (const record of records) {
      store.put(record.track, record.key)
    }

    await getTransactionCompletion(transaction)
    return true
  } finally {
    database.close()
  }
}

async function restoreMusicCacheFromManifest(): Promise<ImportedPlaylist[]> {
  if (typeof window === 'undefined') {
    return []
  }

  try {
    const rawManifest = window.localStorage.getItem(buildCacheManifestKey())
    if (!rawManifest) {
      return []
    }

    const parsedManifest = JSON.parse(rawManifest) as StoredMusicCacheManifest
    if (parsedManifest.version !== 3 || parsedManifest.storage !== 'indexeddb' || !Array.isArray(parsedManifest.playlists)) {
      return []
    }

    const database = await openMusicCacheDatabase()
    if (!database) {
      return []
    }

    const playlists: ImportedPlaylist[] = []
    for (const playlist of parsedManifest.playlists) {
      const tracks: ImportedTrack[] = []
      for (const trackKey of playlist.trackKeys) {
        const storedTrack = await getIndexedDbRecord(database, trackKey)
        if (!storedTrack) {
          database.close()
          return []
        }

        tracks.push({
          id: storedTrack.id,
          name: storedTrack.name,
          fileName: storedTrack.fileName,
          relativePath: storedTrack.relativePath,
          url: URL.createObjectURL(storedTrack.blob),
        })
      }

      playlists.push({
        id: playlist.id,
        label: playlist.label,
        tracks,
      })
    }

    database.close()
    return playlists
  } catch {
    return []
  }
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

export async function replaceMusicLibraryInLocalStorageFromFiles(files: File[]) {
  if (typeof window === 'undefined') {
    return false
  }

  const audioFiles = filterAudioFiles(files)
  if (audioFiles.length === 0) {
    return false
  }

  const { drafts } = buildPlaylistDrafts(audioFiles)
  const storedPlaylists: StoredPlaylist[] = []

  for (const draft of drafts) {
    storedPlaylists.push(await toStoredPlaylist(draft))
  }

  let previousManifest: StoredMusicCacheManifest | StoredMusicCache | null = null
  try {
    const previousManifestRaw = window.localStorage.getItem(buildCacheManifestKey())
    previousManifest = previousManifestRaw ? JSON.parse(previousManifestRaw) as StoredMusicCacheManifest | StoredMusicCache : null
  } catch {
    previousManifest = null
  }

  if (hasIndexedDbSupport()) {
    const libraryId = `${Date.now().toString(36)}-${Math.random().toString(36).slice(2, 8)}`
    const indexedDbRecords: Array<{ key: string; track: StoredIndexedDbTrack }> = []
    const manifestPlaylists: StoredMusicCacheManifestPlaylist[] = []

    for (const [playlistIndex, draft] of drafts.entries()) {
      const trackKeys: string[] = []
      for (const [trackIndex, track] of draft.tracks.entries()) {
        const trackKey = buildCacheTrackKey(libraryId, playlistIndex, trackIndex)
        const blob = await track.file.arrayBuffer().then((buffer) => new Blob([buffer], { type: track.file.type || 'audio/mpeg' }))
        indexedDbRecords.push({
          key: trackKey,
          track: {
            id: track.id,
            name: track.name,
            fileName: track.fileName,
            relativePath: track.relativePath,
            blob,
          },
        })
        trackKeys.push(trackKey)
      }

      manifestPlaylists.push({
        id: draft.id,
        label: draft.label,
        trackKeys,
      })
    }

    const databaseWriteSucceeded = await writeIndexedDbRecords(indexedDbRecords)
    if (!databaseWriteSucceeded) {
      return false
    }

    const payload: StoredMusicCacheManifest = {
      version: 3,
      savedAtUtc: new Date().toISOString(),
      storage: 'indexeddb',
      playlists: manifestPlaylists,
    }

    try {
      window.localStorage.setItem(buildCacheManifestKey(), JSON.stringify(payload))
      window.localStorage.removeItem(MUSIC_CACHE_KEY)
    } catch {
      await removeIndexedDbRecords(indexedDbRecords.map((record) => record.key))
      return false
    }

    if (previousManifest && 'version' in previousManifest) {
      if (previousManifest.version === 3 && previousManifest.storage === 'indexeddb') {
        await removeIndexedDbRecords(previousManifest.playlists.flatMap((playlist) => playlist.trackKeys))
      } else if (previousManifest.version === 1) {
        try {
          window.localStorage.removeItem(MUSIC_CACHE_KEY)
        } catch {
          // Ignore storage failures.
        }
      }
    }

    return true
  }

  try {
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

export async function replaceMusicLibraryInLocalStorage(playlists: ImportedPlaylist[]) {
  if (typeof window === 'undefined') {
    return false
  }

  try {
    window.localStorage.removeItem(MUSIC_CACHE_KEY)
  } catch {
    // Ignore storage failures.
  }

  return persistPlaylistsToLocalStorage(playlists)
}

export async function restorePlaylistsFromLocalStorage(): Promise<ImportedPlaylist[]> {
  if (typeof window === 'undefined') {
    return []
  }

  try {
    const manifestRestoredPlaylists = await restoreMusicCacheFromManifest()
    if (manifestRestoredPlaylists.length > 0) {
      return manifestRestoredPlaylists
    }

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

export function normalizeTrackTitle(trackName: string | null | undefined) {
  const normalized = (trackName ?? '').trim()
  if (!normalized) {
    return 'No signal'
  }

  return normalized
}

export function shouldUseMarqueeTitle(title: string, threshold = 24) {
  return title.trim().length > threshold
}

export function toMusicUiState(state: StoredMusicState | null): MusicUiState | null {
  if (!state) {
    return null
  }

  const normalizedVolume = Number.isFinite(state.volume)
    ? Math.max(0, Math.min(1, state.volume))
    : 0.85

  return {
    selectedPlaylistId: state.selectedPlaylistId,
    selectedTrackIndex: Math.max(0, state.selectedTrackIndex),
    volume: normalizedVolume,
    floatingWidgetCollapsed: Boolean(state.floatingWidgetCollapsed),
  }
}
