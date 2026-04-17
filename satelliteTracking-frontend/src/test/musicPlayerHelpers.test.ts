import { beforeEach, describe, expect, it, vi } from 'vitest'
import {
  replaceMusicLibraryInLocalStorageFromFiles,
  restorePlaylistsFromLocalStorage,
} from '../helpers/musicPlayerHelpers'

describe('musicPlayerHelpers cache', () => {
  beforeEach(() => {
    const storage = new Map<string, string>()

    Object.defineProperty(window, 'localStorage', {
      configurable: true,
      value: {
        getItem: vi.fn((key: string) => storage.get(key) ?? null),
        setItem: vi.fn((key: string, value: string) => {
          storage.set(key, value)
        }),
        removeItem: vi.fn((key: string) => {
          storage.delete(key)
        }),
        clear: vi.fn(() => {
          storage.clear()
        }),
      },
    })
  })

  it('stores and restores a music library from the uploaded files', async () => {
    const file = new File(['audio-bytes'], 'track.mp3', { type: 'audio/mpeg' })

    const persisted = await replaceMusicLibraryInLocalStorageFromFiles([file])

    expect(persisted).toBe(true)

    const restoredPlaylists = await restorePlaylistsFromLocalStorage()
    expect(restoredPlaylists).toHaveLength(1)
    expect(restoredPlaylists[0]?.tracks).toHaveLength(1)
    expect(restoredPlaylists[0]?.tracks[0]?.fileName).toBe('track.mp3')
    expect(restoredPlaylists[0]?.tracks[0]?.url.startsWith('data:')).toBe(true)
  })

  it('replaces the previous stored library when a new folder is imported', async () => {
    const firstFile = new File(['first-audio'], 'first.mp3', { type: 'audio/mpeg' })
    const secondFile = new File(['second-audio'], 'second.mp3', { type: 'audio/mpeg' })

    await replaceMusicLibraryInLocalStorageFromFiles([firstFile])
    await replaceMusicLibraryInLocalStorageFromFiles([secondFile])

    const restoredPlaylists = await restorePlaylistsFromLocalStorage()
    expect(restoredPlaylists).toHaveLength(1)
    expect(restoredPlaylists[0]?.tracks).toHaveLength(1)
    expect(restoredPlaylists[0]?.tracks[0]?.fileName).toBe('second.mp3')
  })
})
