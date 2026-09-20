import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { render, screen, waitFor } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { MemoryRouter, Route, Routes } from 'react-router-dom'
import { SignInPage } from './SignInPage'
import { IdentityProvider } from '../../context/IdentityContext'
import { getIdentity, setIdentity } from '../../context/identityStore'
import { dashboardApi } from '../../api/dashboard'
import { ApiError } from '../../api/client'

vi.mock('../../api/dashboard', () => ({ dashboardApi: { get: vi.fn() } }))

const USER = '11111111-2222-4333-8444-555555555555'
const ORG = '99999999-2222-4333-8444-555555555555'

function renderPage(successDelayMs = 0) {
  return render(
    <MemoryRouter initialEntries={['/sign-in']}>
      <IdentityProvider>
        <Routes>
          <Route path="/sign-in" element={<SignInPage successDelayMs={successDelayMs} />} />
          <Route path="/dashboard" element={<div>DASHBOARD</div>} />
        </Routes>
      </IdentityProvider>
    </MemoryRouter>,
  )
}

describe('SignInPage', () => {
  beforeEach(() => setIdentity(null))
  afterEach(() => {
    setIdentity(null)
    vi.clearAllMocks()
  })

  it('rejects non-UUID identifiers without touching the backend or storing anything', async () => {
    renderPage()
    await userEvent.type(screen.getByLabelText('User ID'), 'nope')
    await userEvent.type(screen.getByLabelText('Organization ID'), 'nope')
    await userEvent.click(screen.getByRole('button', { name: /continue/i }))
    expect(await screen.findByRole('alert')).toHaveTextContent(/valid UUIDs/)
    expect(dashboardApi.get).not.toHaveBeenCalled()
    expect(getIdentity()).toBeNull()
  })

  it('verifies the identity with the backend, shows the welcome animation and opens the workspace', async () => {
    vi.mocked(dashboardApi.get).mockImplementation(() =>
      Promise.resolve({ activeProjectCount: 0, archivedProjectCount: 0, byStatus: { PLANNING: 0, ACTIVE: 0, ON_HOLD: 0, COMPLETED: 0, CANCELLED: 0 }, byPriority: { LOW: 0, MEDIUM: 0, HIGH: 0, CRITICAL: 0 } }),
    )
    renderPage(400)
    await userEvent.type(screen.getByLabelText('User ID'), USER)
    await userEvent.type(screen.getByLabelText('Organization ID'), ORG)
    await userEvent.click(screen.getByRole('button', { name: /continue/i }))
    expect(await screen.findByText('Welcome to WorkEasy360')).toBeInTheDocument()
    expect(getIdentity()).toEqual({ userId: USER, orgId: ORG })
    expect(window.localStorage.getItem('projectmodule.identity')).toContain(USER)
    await screen.findByText('DASHBOARD')
  })

  it('does not keep an identity the backend rejects', async () => {
    vi.mocked(dashboardApi.get).mockImplementation(() =>
      Promise.reject(new ApiError(401, { type: 'https://errors.projectmodule/identity-missing', title: 'Unauthorized', status: 401, detail: 'Missing identity headers' }, 'Unauthorized')),
    )
    renderPage()
    await userEvent.type(screen.getByLabelText('User ID'), USER)
    await userEvent.type(screen.getByLabelText('Organization ID'), ORG)
    await userEvent.click(screen.getByRole('button', { name: /continue/i }))
    expect(await screen.findByRole('alert')).toHaveTextContent(/couldn't verify/)
    await waitFor(() => expect(getIdentity()).toBeNull())
    expect(screen.queryByText('DASHBOARD')).not.toBeInTheDocument()
  })

  it('"Remember me" off keeps the identity for this tab only', async () => {
    vi.mocked(dashboardApi.get).mockImplementation(() =>
      Promise.resolve({ activeProjectCount: 0, archivedProjectCount: 0, byStatus: { PLANNING: 0, ACTIVE: 0, ON_HOLD: 0, COMPLETED: 0, CANCELLED: 0 }, byPriority: { LOW: 0, MEDIUM: 0, HIGH: 0, CRITICAL: 0 } }),
    )
    renderPage()
    await userEvent.type(screen.getByLabelText('User ID'), USER)
    await userEvent.type(screen.getByLabelText('Organization ID'), ORG)
    await userEvent.click(screen.getByLabelText('Remember me'))
    await userEvent.click(screen.getByRole('button', { name: /continue/i }))
    await screen.findByText('DASHBOARD')
    expect(window.localStorage.getItem('projectmodule.identity')).toBeNull()
    expect(window.sessionStorage.getItem('projectmodule.identity')).toContain(USER)
  })
})
