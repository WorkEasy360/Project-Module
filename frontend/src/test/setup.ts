import '@testing-library/jest-dom/vitest'

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
