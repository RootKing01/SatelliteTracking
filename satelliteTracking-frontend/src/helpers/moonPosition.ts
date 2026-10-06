const MOON_MEAN_DISTANCE_M = 384400000
const LUNAR_PERIOD_SEC = 27.321661 * 24 * 3600

export function computeMoonPosition() {
  const phase = ((Date.now() / 1000) % LUNAR_PERIOD_SEC) / LUNAR_PERIOD_SEC
  const lon = ((phase * 360 + 180) % 360) - 180
  return { lon, lat: 0, altMeters: MOON_MEAN_DISTANCE_M, phase }
}
