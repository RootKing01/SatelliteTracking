import { useEffect, useState } from 'react'

/**
 * Hook that returns the current height (px) of the floating music widget
 * element with class `.music-float-player`. If missing, returns 0.
 */
export function useMusicWidgetHeight(): number {
  const [height, setHeight] = useState(0)

  useEffect(() => {
    let ro: ResizeObserver | null = null
    const update = () => {
      const el = document.querySelector('.music-float-player') as HTMLElement | null
      if (el) setHeight(el.offsetHeight)
      else setHeight(0)
    }

    update()

    const el = document.querySelector('.music-float-player') as HTMLElement | null
    if (el && typeof ResizeObserver !== 'undefined') {
      ro = new ResizeObserver(() => update())
      ro.observe(el)
    }

    window.addEventListener('resize', update)
    const mo = new MutationObserver(update)
    mo.observe(document.body, { attributes: true, childList: true, subtree: true })

    return () => {
      if (ro && el) ro.unobserve(el)
      window.removeEventListener('resize', update)
      mo.disconnect()
    }
  }, [])

  return height
}

/**
 * Compute `top` CSS value for floating quick-zoom controls given measured
 * music widget height. Returns a string like '72px' or undefined when not needed.
 */
export function computeQuickZoomTop(musicWidgetHeight: number, extra = 70): number | undefined {
  if (!musicWidgetHeight || musicWidgetHeight <= 0) return undefined
  return musicWidgetHeight + extra
}

export default useMusicWidgetHeight
