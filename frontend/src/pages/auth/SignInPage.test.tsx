import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { render, screen, waitFor } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { MemoryRouter, Route, Routes } from 'react-router-dom'
import { SignInPage } from './SignInPage'
import { IdentityProvider } from '../../context/IdentityContext'
import { getIdentity, setIdentity } from '../../context/identityStore'
import { authApi } from '../../api/auth'
import { ApiError } from '../../api/client'

vi.mock('../../api/auth', () => ({
  authApi: {
    login: vi.fn(),
    register: vi.fn(),
    logout: vi.fn(() => Promise.resolve()),
    config: vi.fn(() => Promise.resolve({ googleClientId: '', emailEnabled: true })),
    forgotPassword: vi.fn(() => Promise.resolve()),
    resetPassword: vi.fn(() => Promise.resolve()),
    previewInvitation: vi.fn(),
    acceptInvitation: vi.fn(),
    claimEarlierSignIn: vi.fn(),
  },
}))

const SESSION = {
  token: 'session-token',
  expiresAt: '2030-01-01T00:00:00Z',
  account: { id: 'user-1', organizationId: 'org-1', email: 'ada@example.com', name: 'Ada', workspaceRole: 'ADMIN' as const },
}

function problem(status: number, detail: string) {
  return new ApiError(status, { type: 't', title: 't', status, detail }, detail)
}

function renderPage(successDelayMs = 0, path = '/sign-in') {
  return render(
    <MemoryRouter initialEntries={[path]}>
      <IdentityProvider>
        <Routes>
          <Route path="/sign-in" element={<SignInPage successDelayMs={successDelayMs} />} />
          <Route path="/join/:inviteToken" element={<SignInPage successDelayMs={successDelayMs} />} />
          <Route path="/reset/:resetToken" element={<SignInPage successDelayMs={successDelayMs} />} />
          <Route path="/dashboard" element={<div>DASHBOARD</div>} />
        </Routes>
      </IdentityProvider>
    </MemoryRouter>,
  )
}

async function fillSignIn(email = 'ada@example.com', password = 'correct horse') {
  await userEvent.type(screen.getByLabelText('Email'), email)
  await userEvent.type(screen.getByLabelText('Password'), password)
}

describe('SignInPage', () => {
  beforeEach(() => setIdentity(null))
  afterEach(() => {
    setIdentity(null)
    vi.clearAllMocks()
  })

  it('asks for email and password, not for ids', () => {
    renderPage()
    expect(screen.getByLabelText('Email')).toBeInTheDocument()
    expect(screen.getByLabelText('Password')).toHaveAttribute('type', 'password')
    expect(screen.queryByLabelText(/user id/i)).not.toBeInTheDocument()
    expect(screen.queryByLabelText(/organization id/i)).not.toBeInTheDocument()
  })

  it('rejects a malformed email without calling the backend', async () => {
    renderPage()
    await fillSignIn('nope')
    await userEvent.click(screen.getByRole('button', { name: /^sign in/i }))
    expect(await screen.findByRole('alert')).toHaveTextContent(/valid email/)
    expect(authApi.login).not.toHaveBeenCalled()
    expect(getIdentity()).toBeNull()
  })

  it('signs in, shows the welcome animation and opens the workspace', async () => {
    vi.mocked(authApi.login).mockResolvedValue(SESSION)
    renderPage(400)
    await fillSignIn()
    await userEvent.click(screen.getByRole('button', { name: /^sign in/i }))
    expect(await screen.findByText((_, el) => el?.tagName === 'STRONG' && el.textContent === 'Welcome to WorkEasyProject')).toBeInTheDocument()
    expect(authApi.login).toHaveBeenCalledWith({ email: 'ada@example.com', password: 'correct horse', rememberMe: true })
    expect(getIdentity()).toMatchObject({ userId: 'user-1', orgId: 'org-1', token: 'session-token', name: 'Ada' })
    expect(window.localStorage.getItem('projectmodule.identity')).toContain('session-token')
    expect(window.localStorage.getItem('projectmodule.identity')).not.toContain('correct horse')
    await screen.findByText('DASHBOARD')
  })

  it('shows the backend message for a refused sign-in, stores nothing and clears the password', async () => {
    vi.mocked(authApi.login).mockRejectedValue(problem(401, 'Incorrect email or password'))
    renderPage()
    await fillSignIn()
    await userEvent.click(screen.getByRole('button', { name: /^sign in/i }))
    expect(await screen.findByRole('alert')).toHaveTextContent('Incorrect email or password')
    await waitFor(() => expect(getIdentity()).toBeNull())
    expect(screen.getByLabelText('Password')).toHaveValue('')
    expect(screen.queryByText('DASHBOARD')).not.toBeInTheDocument()
  })

  it('"Remember me" off keeps the session for this tab only', async () => {
    vi.mocked(authApi.login).mockResolvedValue(SESSION)
    renderPage()
    await fillSignIn()
    await userEvent.click(screen.getByLabelText('Remember me'))
    await userEvent.click(screen.getByRole('button', { name: /^sign in/i }))
    await screen.findByText('DASHBOARD')
    expect(authApi.login).toHaveBeenCalledWith(expect.objectContaining({ rememberMe: false }))
    expect(window.localStorage.getItem('projectmodule.identity')).toBeNull()
    expect(window.sessionStorage.getItem('projectmodule.identity')).toContain('session-token')
  })

  it('asks for a reset link without saying whether the address has an account', async () => {
    renderPage()
    await userEvent.click(await screen.findByRole('button', { name: 'Forgot password?' }))
    await userEvent.type(screen.getByLabelText('Email'), 'ada@example.com')
    await userEvent.click(screen.getByRole('button', { name: /send reset link/i }))
    expect(authApi.forgotPassword).toHaveBeenCalledWith('ada@example.com')
    expect(await screen.findByRole('status')).toHaveTextContent(/if that address has an account/i)
  })

  it('hides "Forgot password" when this deployment has no email', async () => {
    vi.mocked(authApi.config).mockResolvedValueOnce({ googleClientId: '', emailEnabled: false })
    renderPage()
    await waitFor(() => expect(authApi.config).toHaveBeenCalled())
    expect(screen.queryByRole('button', { name: 'Forgot password?' })).not.toBeInTheDocument()
  })

  it('a reset link asks only for the new password, then returns to sign in', async () => {
    renderPage(0, '/reset/abc')
    expect(screen.queryByLabelText('Email')).not.toBeInTheDocument()
    await userEvent.type(screen.getByLabelText('New password'), 'a new password')
    await userEvent.click(screen.getByRole('button', { name: /set new password/i }))
    expect(authApi.resetPassword).toHaveBeenCalledWith({ token: 'abc', newPassword: 'a new password' })
    expect(await screen.findByRole('heading', { name: 'Welcome back' })).toBeInTheDocument()
  })

  it('an invitation fixes the email and joins the workspace', async () => {
    vi.mocked(authApi.previewInvitation).mockResolvedValue({ email: 'ben@example.com', expiresAt: '2030-01-01' })
    vi.mocked(authApi.acceptInvitation).mockResolvedValue(SESSION)
    renderPage(0, '/join/tok')
    await waitFor(() => expect(screen.getByLabelText('Email')).toHaveValue('ben@example.com'))
    expect(screen.getByLabelText('Email')).toHaveAttribute('readonly')
    await userEvent.type(screen.getByLabelText('Full name'), 'Ben')
    await userEvent.type(screen.getByLabelText('Password'), 'correct horse')
    await userEvent.click(screen.getByRole('button', { name: /join workspace/i }))
    await screen.findByText('DASHBOARD')
    expect(authApi.acceptInvitation).toHaveBeenCalledWith({ token: 'tok', name: 'Ben', password: 'correct horse', rememberMe: true })
  })

  it('says so when an invitation link no longer works', async () => {
    vi.mocked(authApi.previewInvitation).mockRejectedValue(problem(422, 'This invitation link is invalid or has expired'))
    renderPage(0, '/join/old')
    expect(await screen.findByRole('alert')).toHaveTextContent(/invalid or has expired/)
    expect(screen.getByRole('button', { name: /join workspace/i })).toBeDisabled()
  })

  it('the eye button reveals the password', async () => {
    renderPage()
    await userEvent.click(screen.getByRole('button', { name: 'Show password' }))
    expect(screen.getByLabelText('Password')).toHaveAttribute('type', 'text')
  })

  it('creates an account and signs straight in', async () => {
    vi.mocked(authApi.register).mockResolvedValue(SESSION)
    renderPage()
    await userEvent.click(screen.getByRole('button', { name: 'Create an account' }))
    await userEvent.type(screen.getByLabelText('Full name'), 'Ada')
    await fillSignIn()
    await userEvent.click(screen.getByRole('button', { name: /^create account/i }))
    await screen.findByText('DASHBOARD')
    expect(authApi.register).toHaveBeenCalledWith({
      name: 'Ada',
      email: 'ada@example.com',
      password: 'correct horse',
      rememberMe: true,
    })
    expect(getIdentity()?.token).toBe('session-token')
  })

  it('checks the name and password length before creating an account', async () => {
    renderPage()
    await userEvent.click(screen.getByRole('button', { name: 'Create an account' }))
    await fillSignIn('ada@example.com', 'short')
    await userEvent.click(screen.getByRole('button', { name: /^create account/i }))
    expect(await screen.findByRole('alert')).toHaveTextContent(/your name/)
    await userEvent.type(screen.getByLabelText('Full name'), 'Ada')
    await userEvent.click(screen.getByRole('button', { name: /^create account/i }))
    expect(await screen.findByRole('alert')).toHaveTextContent(/at least 8/)
    expect(authApi.register).not.toHaveBeenCalled()
  })

  it('says so when the email already has an account, not that something changed elsewhere', async () => {
    vi.mocked(authApi.register).mockRejectedValue(problem(409, 'An account with this email already exists'))
    renderPage()
    await userEvent.click(screen.getByRole('button', { name: 'Create an account' }))
    await userEvent.type(screen.getByLabelText('Full name'), 'Ada')
    await fillSignIn()
    await userEvent.click(screen.getByRole('button', { name: /^create account/i }))
    expect(await screen.findByRole('alert')).toHaveTextContent('An account with this email already exists')
  })
})

describe('SignInPage for people who used the app before email sign-in', () => {
  const EARLIER = JSON.stringify({ userId: 'old-user', orgId: 'old-org' })
  beforeEach(() => {
    setIdentity(null)
    window.localStorage.setItem('projectmodule.identity', EARLIER)
  })
  afterEach(() => {
    setIdentity(null)
    window.localStorage.clear()
    vi.clearAllMocks()
  })

  it('keeps their workspace: they choose an email and password once, with their old ids', async () => {
    vi.mocked(authApi.claimEarlierSignIn).mockResolvedValue({ ...SESSION, account: { ...SESSION.account, id: 'old-user', organizationId: 'old-org' } })
    renderPage()
    expect(screen.getByRole('heading', { name: /one quick step/i })).toBeInTheDocument()
    expect(screen.getByText(/stay exactly as they are/)).toBeInTheDocument()
    await userEvent.type(screen.getByLabelText('Full name'), 'Ada Lovelace')
    await fillSignIn()
    await userEvent.click(screen.getByRole('button', { name: /keep my workspace/i }))
    await screen.findByText('DASHBOARD')
    expect(authApi.claimEarlierSignIn).toHaveBeenCalledWith({ userId: 'old-user', organizationId: 'old-org', name: 'Ada Lovelace',
      email: 'ada@example.com', password: 'correct horse', rememberMe: true })
    expect(getIdentity()).toMatchObject({ userId: 'old-user', orgId: 'old-org', token: 'session-token' })
  })

  it('when the workspace was already moved over, it asks them to sign in instead', async () => {
    vi.mocked(authApi.claimEarlierSignIn).mockRejectedValue(problem(409, 'Your workspace already has an account.'))
    renderPage()
    await userEvent.type(screen.getByLabelText('Full name'), 'Ada')
    await fillSignIn()
    await userEvent.click(screen.getByRole('button', { name: /keep my workspace/i }))
    expect(await screen.findByRole('status')).toHaveTextContent(/already has an email sign-in/)
    expect(screen.getByRole('button', { name: /^sign in/i })).toBeInTheDocument()
  })

  it('they can start a new workspace instead, which forgets the old ids', async () => {
    renderPage()
    await userEvent.click(screen.getByRole('button', { name: /start a new workspace/i }))
    expect(screen.getByRole('button', { name: /^create account/i })).toBeInTheDocument()
    expect(window.localStorage.getItem('projectmodule.identity')).toBeNull()
  })

  it('is not offered once the server has switched it off', async () => {
    vi.mocked(authApi.config).mockResolvedValueOnce({ googleClientId: '', emailEnabled: true, legacyHandover: false })
    renderPage()
    await waitFor(() => expect(screen.queryByRole('button', { name: /keep my workspace/i })).not.toBeInTheDocument())
    expect(screen.getByRole('heading', { name: 'Welcome back' })).toBeInTheDocument()
  })
})
