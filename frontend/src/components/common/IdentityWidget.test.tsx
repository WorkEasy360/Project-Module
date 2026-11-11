import { afterEach, describe, expect, it, vi } from 'vitest'
import { render, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { IdentityProvider } from '../../context/IdentityContext'
import { IdentityWidget } from './IdentityWidget'
import { getIdentity, setIdentity } from '../../context/identityStore'
import { authApi } from '../../api/auth'

vi.mock('../../api/auth', () => ({ authApi: { logout: vi.fn(() => Promise.resolve()) } }))

const ADA = { userId: 'u-1', orgId: 'o-1', token: 'tok', email: 'ada@example.com', name: 'Ada Lovelace' }

function renderWidget() {
  return render(
    <IdentityProvider>
      <IdentityWidget />
    </IdentityProvider>,
  )
}

describe('IdentityWidget', () => {
  afterEach(() => {
    setIdentity(null)
    vi.clearAllMocks()
  })

  it('renders nothing when nobody is signed in', () => {
    const { container } = renderWidget()
    expect(container).toBeEmptyDOMElement()
  })

  it('shows the signed-in name, role and initials rather than raw ids', () => {
    setIdentity(ADA)
    renderWidget()
    expect(screen.getByText('Ada Lovelace')).toBeInTheDocument()
    expect(screen.getByText('Member')).toBeInTheDocument()
    expect(screen.getByText('AL')).toBeInTheDocument()
    expect(screen.queryByText(/u-1/)).not.toBeInTheDocument()
  })

  it('Sign out ends the session on the server and in this browser', async () => {
    setIdentity(ADA)
    renderWidget()
    await userEvent.click(screen.getByRole('button', { name: /ada lovelace/i }))
    await userEvent.click(screen.getByRole('button', { name: /sign out/i }))
    expect(authApi.logout).toHaveBeenCalledTimes(1)
    expect(getIdentity()).toBeNull()
  })

  it('signing out still works when the server cannot be reached', async () => {
    vi.mocked(authApi.logout).mockRejectedValueOnce(new Error('offline'))
    setIdentity(ADA)
    renderWidget()
    await userEvent.click(screen.getByRole('button', { name: /ada lovelace/i }))
    await userEvent.click(screen.getByRole('button', { name: /sign out/i }))
    expect(getIdentity()).toBeNull()
  })
})
