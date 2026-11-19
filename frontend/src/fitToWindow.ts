/**
 * Scales the whole app so each page fits the browser window the way the design does: the design
 * is laid out for a 1672 x 941 window, and on a smaller laptop or a larger monitor the page is
 * zoomed to match, instead of being cropped or left with empty space. Phones and narrow windows
 * keep their own responsive layout and are not zoomed.
 */
export const DESIGN_WIDTH = 1672
export const DESIGN_HEIGHT = 941
const MIN_ZOOM = 0.6
const MAX_ZOOM = 1.35
const RESPONSIVE_BELOW = 1100

export function fitZoom(width: number, height: number): number {
  if (width < RESPONSIVE_BELOW) return 1
  const zoom = Math.min(width / DESIGN_WIDTH, height / DESIGN_HEIGHT)
  return Math.round(Math.max(MIN_ZOOM, Math.min(MAX_ZOOM, zoom)) * 1000) / 1000
}

/** The assistant panel never shows smaller than this share of its full size, however far the app is scaled down. */
const PANEL_SCALE = 0.88

/** Extra zoom for the assistant panel, so it stays readable when the app is scaled down. */
export function panelZoom(appZoom: number): number {
  return appZoom < PANEL_SCALE ? Math.round((PANEL_SCALE / appZoom) * 1000) / 1000 : 1
}

export function startFitToWindow(): void {
  if (typeof window === 'undefined' || typeof document === 'undefined') return
  const root = document.documentElement
  const apply = () => {
    const zoom = fitZoom(window.innerWidth, window.innerHeight)
    if (zoom === 1) {
      root.style.removeProperty('zoom')
    } else {
      root.style.setProperty('zoom', String(zoom))
    }
    // Full-height layouts divide by this, so they still fill exactly one window when zoomed.
    root.style.setProperty('--app-zoom', String(zoom))
    // The assistant panel is read closely, so it never shrinks below a comfortable size.
    const panel = panelZoom(zoom)
    root.style.setProperty('--panel-zoom', String(panel))
    root.style.setProperty('--panel-effective', String(Math.round(zoom * panel * 1000) / 1000))
  }
  apply()
  window.addEventListener('resize', apply)
}
