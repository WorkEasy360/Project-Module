import { describe, expect, it } from 'vitest'
import { fitZoom, panelZoom } from './fitToWindow'

describe('fitZoom', () => {
  it('shows the design at its own size in a 1672 x 941 window', () => {
    expect(fitZoom(1672, 941)).toBe(1)
  })

  it('shrinks to fit a smaller laptop window, by whichever side is tighter', () => {
    expect(fitZoom(1366, 768)).toBeCloseTo(0.816, 3)
    expect(fitZoom(1536, 700)).toBeCloseTo(0.744, 3)
  })

  it('grows on a large monitor, within limits', () => {
    expect(fitZoom(1920, 1080)).toBeCloseTo(1.148, 3)
    expect(fitZoom(3840, 2160)).toBe(1.35)
    expect(fitZoom(1200, 300)).toBe(0.6)
  })

  it('leaves phones and narrow windows to their own layout', () => {
    expect(fitZoom(390, 844)).toBe(1)
    expect(fitZoom(1024, 768)).toBe(1)
  })
})

describe('panelZoom', () => {
  it('keeps the assistant panel at a comfortable size when the app is scaled down', () => {
    expect(0.816 * panelZoom(0.816)).toBeCloseTo(0.88, 2)
    expect(panelZoom(1.148)).toBe(1)
  })
})
