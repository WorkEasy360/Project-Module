import '@testing-library/jest-dom/vitest'
import { afterEach } from 'vitest'

// How the deployment signs people in is read once per page; every test starts without knowing it.
// Imported lazily: a static import here would load api/auth before a test file's vi.mock of it applies.
afterEach(async () => {
  const { setSignInOptions } = await import('../api/signInOptions')
  setSignInOptions(null)
})

// jsdom has no matchMedia implementation. Default every query to "not matched" (desktop-like);
// individual tests override this per-query when they need to simulate a breakpoint.
if (!window.matchMedia) {
  window.matchMedia = (query: string) =>
    ({
      matches: false,
      media: query,
      onchange: null,
      addListener: () => {},
      removeListener: () => {},
      addEventListener: () => {},
      removeEventListener: () => {},
      dispatchEvent: () => false,
    }) as MediaQueryList
}
