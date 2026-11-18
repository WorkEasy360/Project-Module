import { afterEach, describe, expect, it, vi } from 'vitest'
import { authApi } from './auth'
import { getSignInOptions, loadSignInOptions, setSignInOptions, toSignInOptions } from './signInOptions'

describe('signInOptions: how this deployment signs people in', () => {
  afterEach(() => {
    vi.restoreAllMocks()
    setSignInOptions(null)
  })

  it.each([
    [undefined, false, false],
    [{ enabled: false, signInMode: 'sso_only' as const }, false, false],
    [{ enabled: true, signInMode: 'sso_optional' as const }, true, false],
    [{ enabled: true, signInMode: 'sso_only' as const }, true, true],
  ])('workeasy360 %j: offered %s, SSO-only %s', (workeasy360, offered, ssoOnly) => {
    const options = toSignInOptions({ googleClientId: '', emailEnabled: true, workeasy360 })
    expect(options.workeasy360).toBe(offered)
    expect(options.ssoOnly).toBe(ssoOnly)
  })

  it('a server that does not answer is "Workeasy360 not offered, not SSO-only"', () => {
    expect(toSignInOptions(null)).toMatchObject({ workeasy360: false, ssoOnly: false })
  })

  it('reads the configuration once per page; later loads share the answer', async () => {
    const config = vi.spyOn(authApi, 'config').mockResolvedValue({
      googleClientId: 'g',
      emailEnabled: true,
      workeasy360: { enabled: true, signInMode: 'sso_optional' },
    })
    const [a, b] = await Promise.all([loadSignInOptions(), loadSignInOptions()])
    await loadSignInOptions()
    expect(config).toHaveBeenCalledTimes(1)
    expect(a).toBe(b)
    expect(getSignInOptions()).toMatchObject({ workeasy360: true, ssoOnly: false, config: { googleClientId: 'g' } })
  })

  it('a failed read counts as not offered, and the next load asks again', async () => {
    const config = vi
      .spyOn(authApi, 'config')
      .mockRejectedValueOnce(new TypeError('offline'))
      .mockResolvedValueOnce({ googleClientId: '', emailEnabled: true, workeasy360: { enabled: true, signInMode: 'sso_only' } })
    expect(await loadSignInOptions()).toMatchObject({ workeasy360: false, ssoOnly: false })
    expect(await loadSignInOptions()).toMatchObject({ workeasy360: true, ssoOnly: true })
    expect(config).toHaveBeenCalledTimes(2)
  })

  it('options put in place meanwhile are not overwritten by a read that was overtaken', async () => {
    let answer: (value: Awaited<ReturnType<typeof authApi.config>>) => void = () => undefined
    vi.spyOn(authApi, 'config').mockImplementation(() => new Promise((resolve) => { answer = resolve }))
    const pending = loadSignInOptions()
    setSignInOptions(toSignInOptions({ googleClientId: '', emailEnabled: true, workeasy360: { enabled: true, signInMode: 'sso_only' } }))
    answer({ googleClientId: '', emailEnabled: true })
    await pending
    expect(getSignInOptions()?.ssoOnly).toBe(true)
  })
})
