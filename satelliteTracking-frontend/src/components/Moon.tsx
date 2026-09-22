import { useMemo } from 'react'
import { Cartesian3, Color, HorizontalOrigin, NearFarScalar, VerticalOrigin } from 'cesium'
import { Entity } from 'resium'

type MoonProps = {
  show?: boolean
}

// Approximate moon position and simple phase calculation
export const computeMoonPosition = () => {
  const MOON_MEAN_DISTANCE_M = 384400000
  const LUNAR_PERIOD_SEC = 27.321661 * 24 * 3600
  const nowSec = Date.now() / 1000
  const phase = (nowSec % LUNAR_PERIOD_SEC) / LUNAR_PERIOD_SEC
  const lon = ((phase * 360 + 180) % 360) - 180
  const lat = 0
  // Simple phase angle (0..1)
  const phaseFrac = phase
  // distance variation small; keep mean
  const distance = MOON_MEAN_DISTANCE_M
  return { lon, lat, altMeters: distance, phase: phaseFrac }
}

const createMoonTexture = (phase: number) => {
  const light = Math.round(220 - Math.abs(phase - 0.5) * 200)
  const dark = Math.round(120 - Math.abs(phase - 0.5) * 60)
  const svg = `
    <svg xmlns='http://www.w3.org/2000/svg' width='256' height='256' viewBox='0 0 256 256'>
      <defs>
        <radialGradient id='g' cx='40%' cy='35%'>
          <stop offset='0%' stop-color='rgb(${light},${light},${light})' />
          <stop offset='60%' stop-color='rgb(${Math.max(180,dark)},${Math.max(170,dark)},${Math.max(150,dark)})' />
          <stop offset='100%' stop-color='rgb(${dark},${dark},${dark})' />
        </radialGradient>
      </defs>
      <rect width='100%' height='100%' rx='128' fill='url(#g)' />
      <g opacity='0.9'>
        <ellipse cx='98' cy='86' rx='18' ry='14' fill='#c9b994' opacity='0.75'/>
        <ellipse cx='150' cy='120' rx='28' ry='22' fill='#bfb392' opacity='0.7'/>
        <ellipse cx='118' cy='172' rx='12' ry='10' fill='#b9ad86' opacity='0.75'/>
      </g>
      <circle cx='128' cy='128' r='120' fill='none' stroke='rgba(0,0,0,0.06)' stroke-width='2' />
    </svg>
  `.trim()

  return `data:image/svg+xml;charset=utf-8,${encodeURIComponent(svg)}`
}

export default function Moon({ show = true }: MoonProps) {
  const { lon, lat, altMeters, phase } = computeMoonPosition()

  const moonImage = useMemo(() => createMoonTexture(phase), [phase])
  const haloImage = useMemo(() => {
    const svg = `
      <svg xmlns='http://www.w3.org/2000/svg' width='200' height='200' viewBox='0 0 200 200'>
        <defs>
          <radialGradient id='h' cx='50%' cy='50%'>
            <stop offset='0%' stop-color='rgba(127,249,255,0.55)' />
            <stop offset='60%' stop-color='rgba(127,249,255,0.16)' />
            <stop offset='100%' stop-color='rgba(127,249,255,0.00)' />
          </radialGradient>
        </defs>
        <rect width='100%' height='100%' fill='none' />
        <circle cx='100' cy='100' r='80' fill='url(#h)' />
      </svg>
    `.trim()
    return `data:image/svg+xml;charset=utf-8,${encodeURIComponent(svg)}`
  }, [])

  if (!show) {
    return null
  }

  const position = Cartesian3.fromDegrees(lon, lat, altMeters)

  return (
    <>
      <Entity
        id="moon-entity"
        name="Moon"
        description={`Moon — phase ${(phase * 100).toFixed(1)}% — distance ${(altMeters / 1000).toFixed(0)} km`}
        position={position}
        billboard={{
          image: moonImage,
          width: 34,
          height: 34,
          horizontalOrigin: HorizontalOrigin.CENTER,
          verticalOrigin: VerticalOrigin.CENTER,
          scaleByDistance: new NearFarScalar(2500000, 0.85, 45000000, 0.35),
        }}
      />

      <Entity
        id="moon-halo"
        name="Moon Halo"
        position={position}
        billboard={{
          image: haloImage,
          width: 130,
          height: 130,
          horizontalOrigin: HorizontalOrigin.CENTER,
          verticalOrigin: VerticalOrigin.CENTER,
          color: Color.fromAlpha(Color.fromCssColorString('#7ff9ff'), 0.20),
          scaleByDistance: new NearFarScalar(2500000, 0.7, 45000000, 0.2),
        }}
      />
    </>
  )
}
