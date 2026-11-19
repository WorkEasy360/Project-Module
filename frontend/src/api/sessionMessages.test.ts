import { describe, expect, it } from 'vitest'
import {
  ACCESS_ENDED_GENERIC,
  READ_ONLY_MESSAGE,
  SSO_ERROR_MESSAGES,
  accessProblemMessage,
  ssoErrorMessage,
} from './sessionMessages'

describe('sessionMessages', () => {
  it('has text for a read-only organization, both as a code and as a reason', () => {
    expect(accessProblemMessage('workeasy360_read_only', null)).toBe(READ_ONLY_MESSAGE)
    expect(accessProblemMessage('workeasy360_access_ended', 'read_only')).toMatch(/read-only/i)
  })

  it.each(['__proto__', 'constructor', 'toString', 'hasOwnProperty', 'valueOf'])(
    'never resolves the prototype key %j to anything but the fallback',
    (key) => {
      expect(ssoErrorMessage(key)).toBe(SSO_ERROR_MESSAGES.refused)
      expect(accessProblemMessage('workeasy360_access_ended', key)).toBe(ACCESS_ENDED_GENERIC)
    },
  )
})
