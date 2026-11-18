import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import {
  abortSigningOut,
  AUTO_START_GUARD_MS,
  autoStartedRecently,
  beginSigningOut,
  clearAutoStartRecord,
  decideAutoStart,
  isSigningOut,
  recordAutoStart,
  signedOutCookieNames,
  signedOutMarkerPresent,
} from './autoSignIn'
import { safeNextRoute, workeasy360StartUrl } from './session'

const GONE = 'Thu, 01 Jan 1970 00:00:00 GMT'

function clearCookies() {
  for (const name of ['ep_signed_out', 'ep_auto_start_at']) {
    document.cookie = `${name}=; expires=${GONE}; path=/`
  }
}

describe('automatic sign-in: when it is allowed', () => {
  beforeEach(() => {
    clearCookies()
    window.sessionStorage.clear()
  })
  afterEach(() => {
    clearCookies()
    window.sessionStorage.clear()
    abortSigningOut()
    vi.restoreAllMocks()
  })

  it('starts when there is nothing against it', () => {
    expect(decideAutoStart(false)).toBe('start')
  })

  it('does not start after a sign-out of Easy Project in this browser (the marker)', () => {
    document.cookie = 'ep_signed_out=1; path=/'
    expect(signedOutMarkerPresent()).toBe(true)
    expect(decideAutoStart(false)).toBe('signed-out')
  })

  it('reads the __Host- prefixed marker the server leaves over https as well', () => {
    // jsdom runs on http, where a __Host- cookie cannot be stored, so the cookie string is supplied directly.
    vi.spyOn(document, 'cookie', 'get').mockReturnValue('theme=dark; __Host-ep_signed_out=1; other=x')
    expect(signedOutMarkerPresent()).toBe(true)
    expect(decideAutoStart(false)).toBe('signed-out')
  })

  it('over https honours ONLY the __Host- marker: an unprefixed one can be planted by a sibling subdomain', () => {
    expect(signedOutCookieNames('https:')).toEqual(['__Host-ep_signed_out'])
    // A sibling site sets ep_signed_out with Domain=workeasy360.com; the server never clears that one.
    vi.spyOn(document, 'cookie', 'get').mockReturnValue('theme=dark; ep_signed_out=1')
    expect(signedOutMarkerPresent('https:')).toBe(false)
    vi.spyOn(document, 'cookie', 'get').mockReturnValue('theme=dark; __Host-ep_signed_out=1')
    expect(signedOutMarkerPresent('https:')).toBe(true)
  })

  it('over plain http both names are honoured (the local profile writes the plain one)', () => {
    expect(signedOutCookieNames('http:')).toEqual(['__Host-ep_signed_out', 'ep_signed_out'])
    vi.spyOn(document, 'cookie', 'get').mockReturnValue('ep_signed_out=1')
    expect(signedOutMarkerPresent('http:')).toBe(true)
  })

  it('does not start while a sign-out is in progress, before any marker cookie exists', () => {
    expect(isSigningOut()).toBe(false)
    beginSigningOut()
    expect(isSigningOut()).toBe(true)
    expect(signedOutMarkerPresent()).toBe(false)
    expect(decideAutoStart(false)).toBe('signed-out')
  })

  it('a sign-out that did not happen (the request failed) suppresses nothing', () => {
    beginSigningOut()
    abortSigningOut()
    expect(decideAutoStart(false)).toBe('start')
  })

  it('is not fooled by similarly named cookies', () => {
    vi.spyOn(document, 'cookie', 'get').mockReturnValue('x_ep_signed_out=1; ep_signed_out_not=1; __Host-ep_session=abc')
    expect(signedOutMarkerPresent()).toBe(false)
  })

  it('does not start when this browser started automatically less than 60 seconds ago (the loop guard)', () => {
    const now = 1_000_000_000_000
    recordAutoStart(now)
    expect(autoStartedRecently(now + 1)).toBe(true)
    expect(autoStartedRecently(now + AUTO_START_GUARD_MS - 1)).toBe(true)
    expect(decideAutoStart(false, now + 30_000)).toBe('recent-start')
  })

  it('may start again once 60 seconds have passed', () => {
    const now = 1_000_000_000_000
    recordAutoStart(now)
    expect(autoStartedRecently(now + AUTO_START_GUARD_MS)).toBe(false)
    expect(decideAutoStart(false, now + AUTO_START_GUARD_MS + 1)).toBe('start')
  })

  it('does not start when the session could not be checked: that is not the same as no session', () => {
    expect(decideAutoStart(true)).toBe('session-unknown')
  })

  it('the marker wins over everything else', () => {
    document.cookie = 'ep_signed_out=1; path=/'
    expect(decideAutoStart(false)).toBe('signed-out')
    recordAutoStart()
    expect(decideAutoStart(false)).toBe('signed-out')
  })

  it('forgets the guard when asked (a session exists now)', () => {
    recordAutoStart(1_000)
    clearAutoStartRecord()
    expect(autoStartedRecently(1_001)).toBe(false)
  })

  it('keeps the guard in a session cookie when sessionStorage is unavailable, and clears it from there too', () => {
    vi.spyOn(Storage.prototype, 'setItem').mockImplementation(() => {
      throw new DOMException('blocked', 'SecurityError')
    })
    vi.spyOn(Storage.prototype, 'getItem').mockImplementation(() => {
      throw new DOMException('blocked', 'SecurityError')
    })
    const now = Date.now()
    recordAutoStart(now)
    expect(document.cookie).toContain(`ep_auto_start_at=${now}`)
    expect(autoStartedRecently(now + 10)).toBe(true)
    clearAutoStartRecord()
    expect(document.cookie).not.toContain('ep_auto_start_at')
    expect(autoStartedRecently(now + 10)).toBe(false)
  })

  it('treats a garbage guard value as no guard', () => {
    window.sessionStorage.setItem('ep.autoSignInStartedAt', 'not-a-number')
    expect(autoStartedRecently()).toBe(false)
    expect(decideAutoStart(false)).toBe('start')
  })
})

describe('the route carried to Workeasy360 as next', () => {
  it.each([
    ['/', '/'],
    ['/dashboard', '/dashboard'],
    ['/projects/9b2f/tasks?status=OPEN', '/projects/9b2f/tasks?status=OPEN'],
    ['/#/projects/1', '/projects/1'],
  ])('keeps %j', (route, expected) => {
    expect(safeNextRoute(route)).toBe(expected)
  })

  // Built from character codes, so no escape sequence has to survive an editor or a shell.
  const BACKSLASH = String.fromCharCode(92)
  const TAB = String.fromCharCode(9)
  const NEWLINE = String.fromCharCode(10)
  const NUL = String.fromCharCode(0)
  const DEL = String.fromCharCode(127)
  const NBSP = String.fromCharCode(0xa0)
  const LINE_SEPARATOR = String.fromCharCode(0x2028)

  it.each([
    [''], ['//evil.example'], ['///evil.example'], ['https://evil.example/'], ['javascript:alert(1)'], ['evil.example'],
    ['/' + BACKSLASH + 'evil.example'], [BACKSLASH + 'evil.example'], ['/' + BACKSLASH + BACKSLASH + 'evil.example'],
    ['/' + TAB + '/evil.example'], ['/' + NEWLINE + '/evil.example'], ['/ /evil.example'],
    ['/a' + NUL + 'b'], ['/a' + DEL + 'b'], ['/a' + NBSP + 'b'], ['/a' + LINE_SEPARATOR + 'b'],
    ['/%2f/evil.example'], ['/%2F/evil.example'], ['/%5cevil.example'], ['/%09/evil.example'], ['/%0a/evil.example'],
    ['/%00'], ['/%7f'],
    ['/sign-in'], ['/sign-in/'], ['/sign-in?sso_error=expired'], ['/SIGN-IN'],
    ['/api'], ['/api/v1/projects'], ['/API/v1/projects'], ['/api/auth/session'],
    ['/projects#x'], ['/#//evil.example'], ['/#/sign-in'],
  ])('never forwards the hostile or non-route value %j', (route) => {
    expect(safeNextRoute(route)).toBe('/')
    expect(workeasy360StartUrl(route)).toBe('/api/auth/workeasy360/start?next=%2F')
  })

  it('never forwards a value over 2,048 characters, and keeps one of exactly 2,048', () => {
    expect(safeNextRoute('/' + 'a'.repeat(2047))).toHaveLength(2048)
    expect(safeNextRoute('/' + 'a'.repeat(2048))).toBe('/')
  })

  it('builds the start URL with the route encoded and no organization hint', () => {
    const url = workeasy360StartUrl('/projects/123/tasks?q=a%20b&x=1')
    expect(url).toBe('/api/auth/workeasy360/start?next=' + encodeURIComponent('/projects/123/tasks?q=a%20b&x=1'))
    expect(url).not.toContain('organization')
  })
})
