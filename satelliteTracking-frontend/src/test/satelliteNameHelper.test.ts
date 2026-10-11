import { afterEach, describe, expect, it, vi } from 'vitest'
import { fetchSatelliteById, fetchSatelliteCatalogByType } from '../api/satelliteClient'
import { fetchAndMapSatelliteNames, updateMissingSatelliteNames } from '../helpers/satelliteNameHelper'

vi.mock('../api/satelliteClient', () => ({
  fetchSatelliteById: vi.fn(),
  fetchSatelliteCatalogByType: vi.fn(),
}))

describe('satelliteNameHelper', () => {
  afterEach(() => {
    vi.clearAllMocks()
  })

  it('reuses a successful catalog request for the same type', async () => {
    vi.mocked(fetchSatelliteCatalogByType).mockResolvedValue([
      { id: 1, objectName: 'ISS', objectId: '1998-067A', noradCatId: 25544 },
    ])

    const first = await fetchAndMapSatelliteNames('ALL')
    const second = await fetchAndMapSatelliteNames('ALL')

    expect(first.map['1']).toBe('ISS')
    expect(second).toBe(first)
    expect(fetchSatelliteCatalogByType).toHaveBeenCalledTimes(1)
  })

  it('does not retry an unavailable satellite id', async () => {
    vi.mocked(fetchSatelliteById).mockResolvedValue(null)
    const setSatelliteNames = vi.fn()

    await updateMissingSatelliteNames(['missing-1'], setSatelliteNames)
    await updateMissingSatelliteNames(['missing-1'], setSatelliteNames)

    expect(fetchSatelliteById).toHaveBeenCalledTimes(1)
    expect(setSatelliteNames).not.toHaveBeenCalled()
  })
})
