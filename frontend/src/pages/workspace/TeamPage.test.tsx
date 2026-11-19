import { afterEach, describe, expect, it, vi, beforeEach } from 'vitest'
import { render, screen, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { MemoryRouter } from 'react-router-dom'
import { TeamPage, type TeamSection } from './TeamPage'
import { IdentityProvider } from '../../context/IdentityContext'
import { PeopleProvider } from '../../context/PeopleContext'
import { setIdentity } from '../../context/identityStore'
import { resetSessionForTest, signInAs } from '../../test/session'
import { workspaceApi } from '../../api/workspace'
import type { Account } from '../../api/auth'

vi.mock('../../api/workspace', async (importOriginal) => ({
  ...(await importOriginal<typeof import('../../api/workspace')>()),
  workspaceApi: {
    people: vi.fn(),
    invitations: vi.fn(),
    invite: vi.fn(),
    revokeInvitation: vi.fn(),
    resetLink: vi.fn(),
  },
}))

const ME = '11111111-2222-4333-8444-555555555555'
const OTHER = 'aaaaaaaa-bbbb-4ccc-8ddd-eeeeeeeeeeee'
const ORG = '99999999-2222-4333-8444-555555555555'

function people(meRole: 'ADMIN' | 'MEMBER'): Account[] {
  return [
    { id: ME, organizationId: ORG, email: 'manoj@acme.example', name: 'Manoj Kumar', workspaceRole: meRole },
    { id: OTHER, organizationId: ORG, email: 'amit@acme.example', name: 'Amit Singh', workspaceRole: 'MEMBER' },
  ]
}

function renderTeam(section: TeamSection) {
  return render(
    <MemoryRouter>
      <IdentityProvider>
        <PeopleProvider>
          <TeamPage section={section} />
        </PeopleProvider>
      </IdentityProvider>
    </MemoryRouter>,
  )
}

describe('TeamPage', () => {
  beforeEach(() => {
    setIdentity({ userId: ME, orgId: ORG, token: 'tok', name: 'Manoj Kumar', email: 'manoj@acme.example' })
    vi.mocked(workspaceApi.people).mockReset().mockResolvedValue(people('ADMIN'))
    vi.mocked(workspaceApi.invitations).mockReset().mockResolvedValue([
      { id: 'i1', email: 'deepti@acme.example', expiresAt: '2026-11-20T10:00:00Z', token: null },
    ])
    vi.mocked(workspaceApi.revokeInvitation).mockReset().mockResolvedValue(undefined)
    vi.mocked(workspaceApi.invite).mockReset().mockResolvedValue({ id: 'i2', email: 'deepti@acme.example', expiresAt: '2026-11-21T10:00:00Z', token: 'tok-new' })
  })

  afterEach(() => {
    setIdentity(null)
    resetSessionForTest()
  })

  it('a Workeasy360 sign-in is told its people are managed in Workeasy360, and cannot invite', async () => {
    setIdentity(null)
    signInAs(ME, { name: 'Manoj Kumar' })
    vi.mocked(workspaceApi.people).mockResolvedValue([])
    renderTeam('members')
    expect(await screen.findByText(/people in your organization are managed in Workeasy360/)).toBeInTheDocument()
    expect(screen.queryByRole('button', { name: /Invite Member/ })).not.toBeInTheDocument()
    expect(workspaceApi.invitations).not.toHaveBeenCalled()
  })

  it('Members lists joined people with role and status, plus invited people for admins', async () => {
    renderTeam('members')
    expect(await screen.findByText('Amit Singh')).toBeInTheDocument()
    expect(screen.getByRole('heading', { name: 'Members' })).toBeInTheDocument()
    const me = screen.getByText(/Manoj Kumar \(you\)/).closest('tr')!
    expect(within(me).getByText('Admin')).toBeInTheDocument()
    expect(within(me).getByText('Active')).toBeInTheDocument()
    const invited = (await screen.findByText('deepti@acme.example')).closest('tr')!
    expect(within(invited).getByText('Invited')).toBeInTheDocument()
    expect(screen.getByRole('button', { name: /Invite Member/ })).toBeInTheDocument()
  })

  it('Members can be filtered by status', async () => {
    renderTeam('members')
    await screen.findByText('deepti@acme.example')
    await userEvent.selectOptions(screen.getByRole('combobox', { name: 'Status' }), 'Invited')
    expect(screen.queryByText('Amit Singh')).not.toBeInTheDocument()
    expect(screen.getByText('deepti@acme.example')).toBeInTheDocument()
  })

  it('a member who is not an admin cannot invite and does not see invitations', async () => {
    vi.mocked(workspaceApi.people).mockResolvedValue(people('MEMBER'))
    renderTeam('members')
    await screen.findByText('Amit Singh')
    expect(screen.queryByRole('button', { name: /Invite Member/ })).not.toBeInTheDocument()
    expect(screen.queryByText('deepti@acme.example')).not.toBeInTheDocument()
  })

  it('Teams says plainly that it is coming, with nothing that looks working', async () => {
    renderTeam('teams')
    expect(screen.getByText('Coming in the next update')).toBeInTheDocument()
    expect(screen.getByRole('heading', { name: 'Teams are not available yet' })).toBeInTheDocument()
    expect(screen.queryByRole('button', { name: /create team/i })).not.toBeInTheDocument()
  })

  it('Invitations: pending status, resend makes a new link, revoke withdraws it', async () => {
    renderTeam('invitations')
    const row = (await screen.findByText('deepti@acme.example')).closest('tr')!
    expect(within(row).getByText('Pending')).toBeInTheDocument()

    await userEvent.click(within(row).getByRole('button', { name: 'Resend' }))
    expect(screen.getByLabelText(/Email address/)).toHaveValue('deepti@acme.example')
    await userEvent.click(screen.getByRole('button', { name: 'Send invitation' }))
    expect(workspaceApi.invite).toHaveBeenCalledWith('deepti@acme.example')
    expect((await screen.findByRole('textbox', { name: 'Invitation link' }) as HTMLInputElement).value).toContain('#/join/tok-new')
    await userEvent.click(screen.getByRole('button', { name: 'Done' }))

    await userEvent.click(within(screen.getByText('deepti@acme.example').closest('tr')!).getByRole('button', { name: 'Revoke' }))
    await userEvent.click(within(screen.getByRole('dialog')).getByRole('button', { name: 'Revoke' }))
    expect(workspaceApi.revokeInvitation).toHaveBeenCalledWith('i1')
  })
})
