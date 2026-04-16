import { useMemo } from 'react'
import '../../styles/panels/compass-panel.css'

type CompassPanelProps = {
  headingDeg: number
  hideOnMobile?: boolean
}

export function CompassPanel({ headingDeg, hideOnMobile = false }: CompassPanelProps) {
  const normalizedHeadingDeg = useMemo(
    () => ((headingDeg % 360) + 360) % 360,
    [headingDeg],
  )

  const compassDialLabels = useMemo(
    () =>
      Array.from({ length: 12 }, (_, index) => {
        const deg = index * 30
        const angleRad = ((deg - 90) * Math.PI) / 180
        const x = 60 + Math.cos(angleRad) * 41
        const y = 60 + Math.sin(angleRad) * 41

        let label = String(deg)
        if (deg === 0) label = 'N'
        if (deg === 90) label = 'E'
        if (deg === 180) label = 'S'
        if (deg === 270) label = 'W'

        return { deg, x, y, label }
      }),
    [],
  )

  return (
    <section
      className={`viewer-compass ${hideOnMobile ? 'viewer-compass-mobile-hidden' : ''}`}
      aria-label="Bussola 360 gradi"
    >
      <svg viewBox="0 0 120 120" role="img" aria-hidden="true">
        <circle cx="60" cy="60" r="54" className="viewer-compass-ring" />
        <circle cx="60" cy="60" r="43" className="viewer-compass-inner-ring" />
        {Array.from({ length: 72 }, (_, index) => {
          const deg = index * 5
          const major = deg % 30 === 0
          return (
            <line
              key={`tick-${deg}`}
              x1="60"
              y1={major ? '7' : '10'}
              x2="60"
              y2={major ? '16' : '14'}
              className={major ? 'viewer-compass-tick-major' : 'viewer-compass-tick-minor'}
              transform={`rotate(${deg} 60 60)`}
            />
          )
        })}
        {compassDialLabels.map((item) => (
          <text
            key={`label-${item.deg}`}
            x={item.x}
            y={item.y}
            textAnchor="middle"
            dominantBaseline="middle"
            className={`viewer-compass-label ${item.deg % 90 === 0 ? 'viewer-compass-label-cardinal' : ''}`}
          >
            {item.label}
          </text>
        ))}
        <g transform={`rotate(${normalizedHeadingDeg} 60 60)`}>
          <polygon points="60,18 55,62 65,62" className="viewer-compass-needle" />
          <polygon points="60,102 56,68 64,68" className="viewer-compass-tail" />
        </g>
        <circle cx="60" cy="60" r="5" className="viewer-compass-center" />
      </svg>
      <p className="viewer-compass-readout">{normalizedHeadingDeg.toFixed(1)}deg</p>
    </section>
  )
}
