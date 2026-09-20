import { describe, expect, it, vi, beforeEach } from 'vitest'
import { render, screen, waitFor, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { MemoryRouter } from 'react-router-dom'
import { TeamPage } from './TeamPage'
import { IdentityProvider } from '../../context/IdentityContext'
import { setIdentity } from '../../context/identityStore'
import { projectsApi } from '../../api/projects'
import { membersApi } from '../../api/members'
import type { ProjectMember } from '../../types/project'

vi.mock('../../api/projects', () => ({ projectsApi: { list: vi.fn() } }))
vi.mock('../../api/members', () => ({ membersApi: { list: vi.fn() } }))

const ME = '11111111-2222-4333-8444-555555555555'
const OTHER = 'aaaaaaaa-bbbb-4ccc-8ddd-eeeeeeeeeeee'
const projects = [
  { id: 'p1', name: 'Website Redesign', status: 'ACTIVE', priority: 'HIGH', ownerId: ME, updatedAt: '2026-09-10T00:00:00Z' },
  { id: 'p2', name: 'Mobile App', status: 'PLANNING', priority: 'MEDIUM', ownerId: OTHER, updatedAt: '2026-09-09T00:00:00Z' },
  { id: 'p3', name: 'Data Migration', status: 'ON_HOLD', priority: 'LOW', ownerId: ME, updatedAt: '2026-09-08T00:00:00Z' },
] as const
const m = (over: Partial<ProjectMember>): ProjectMember => ({
  id: 'x', projectId: 'p1', userId: ME, role: 'MEMBER', joinedAt: '2026-09-01T00:00:00Z', version: 0, ...over,
})
const membersById: Record<string, ProjectMember[]> = {
  p1: [m({ id: 'm1', userId: ME, role: 'OWNER' }), m({ id: 'm2', userId: OTHER, role: 'MANAGER' })],
  p2: [m({ id: 'm3', projectId: 'p2', userId: OTHER, role: 'OWNER' }), m({ id: 'm4', projectId: 'p2', userId: ME, role: 'MEMBER' })],
}

function page(totalElements = 3) {
  return { content: [...projects], page: 0, size: 50, totalElements, totalPages: 1 }
}
function renderTeam() {
  return render(
    <MemoryRouter>
      <IdentityProvider>
        <TeamPage />
      </IdentityProvider>
    </MemoryRouter>,
  )
}

describe('TeamPage', () => {
  beforeEach(() => {
    setIdentity({ userId: ME, orgId: '99999999-2222-4333-8444-555555555555' })
    vi.mocked(projectsApi.list).mockReset()
    vi.mocked(membersApi.list).mockReset()
    vi.mocked(projectsApi.list).mockImplementation(() => Promise.resolve(page()))
    vi.mocked(membersApi.list).mockImplementation((id: string) =>
      id === 'p3' ? Promise.reject(new Error('boom')) : Promise.resolve(membersById[id]),
    )
  })

  it('groups members by user id with short-id avatars, highlights the current identity, and links roles to project members', async () => {
    renderTeam()
    const people = await screen.findByRole('list', { name: 'People' })
    expect(vi.mocked(projectsApi.list)).toHaveBeenCalledWith(0, 50, 'updatedAt,DESC')
    const rows = within(people).getAllByRole('listitem').filter((li) => li.classList.contains('ws-person'))
    expect(rows).toHaveLength(2)
    // The current identity comes first and is marked "You"; ids are shown as their first 8 chars only.
    expect(rows[0]).toHaveClass('is-me')
    expect(within(rows[0]).getByText('You')).toBeInTheDocument()
    expect(within(rows[0]).getByText('11111111')).toBeInTheDocument()
    expect(rows[0]).not.toHaveTextContent(ME)
    expect(rows[0]).toHaveTextContent('2 projects')
    expect(within(rows[0]).getByRole('link', { name: 'Website Redesign' })).toHaveAttribute('href', '/projects/p1/members')
    expect(within(rows[0]).getByRole('link', { name: 'Mobile App' })).toHaveAttribute('href', '/projects/p2/members')
    expect(rows[0]).toHaveTextContent('Owner')
    expect(rows[0]).toHaveTextContent('Member')
    expect(rows[1]).toHaveTextContent('aaaaaaaa')
    expect(rows[1]).not.toHaveClass('is-me')

    // Summary chips are derived from the loaded member lists (p3 failed, so 2 projects).
    const chips = screen.getByLabelText('Team summary')
    expect(chips).toHaveTextContent('2 people')
    expect(chips).toHaveTextContent('2 projects')
    expect(chips).toHaveTextContent('2 owners')
    expect(chips).toHaveTextContent('1 managers')
  })

  it('shows a failing project inline with Retry instead of blanking the page', async () => {
    renderTeam()
    const alert = await screen.findByRole('alert')
    expect(alert).toHaveTextContent('1 project could not be loaded')
    expect(within(alert).getByRole('link', { name: 'Data Migration' })).toHaveAttribute('href', '/projects/p3')
    expect(alert).toHaveTextContent('Something went wrong. Please try again.')
    expect(screen.getByRole('list', { name: 'People' })).toBeInTheDocument()

    vi.mocked(membersApi.list).mockImplementation((id: string) => Promise.resolve(membersById[id] ?? []))
    await userEvent.click(within(alert).getByRole('button', { name: 'Retry' }))
    await waitFor(() => expect(screen.queryByRole('alert')).not.toBeInTheDocument())
    expect(vi.mocked(membersApi.list)).toHaveBeenCalledTimes(6)
  })

  it('search by user id / project name and the role filter narrow the people list', async () => {
    renderTeam()
    await screen.findByRole('list', { name: 'People' })
    await userEvent.type(screen.getByRole('searchbox'), 'aaaa')
    expect(screen.getByText('1 person')).toBeInTheDocument()
    expect(screen.queryByText('11111111')).not.toBeInTheDocument()
    await userEvent.clear(screen.getByRole('searchbox'))
    await userEvent.type(screen.getByRole('searchbox'), 'mobile')
    // Both people are on Mobile App, but only that project's role is listed for them.
    expect(screen.getByText('2 people')).toBeInTheDocument()
    expect(screen.queryByRole('link', { name: 'Website Redesign' })).not.toBeInTheDocument()
    await userEvent.click(screen.getByRole('button', { name: 'Reset filters' }))
    await userEvent.selectOptions(screen.getByLabelText('Role'), 'MANAGER')
    expect(screen.getByText('1 person')).toBeInTheDocument()
    expect(screen.getByText('aaaaaaaa')).toBeInTheDocument()
    await userEvent.selectOptions(screen.getByLabelText('Role'), 'VIEWER')
    expect(screen.getByText('No people match these filters.')).toBeInTheDocument()
  })

  it('"By project" view shows a card per project with members, an inline error, and a Manage members link', async () => {
    renderTeam()
    await screen.findByRole('list', { name: 'People' })
    await userEvent.click(screen.getByRole('button', { name: 'By project' }))
    const grid = screen.getByRole('list', { name: 'Projects' })
    const cards = within(grid).getAllByRole('listitem').filter((li) => li.classList.contains('ws-card'))
    expect(cards).toHaveLength(3)
    const website = cards.find((c) => c.textContent?.includes('Website Redesign'))!
    expect(within(website).getByRole('link', { name: /Manage members/ })).toHaveAttribute('href', '/projects/p1/members')
    expect(website).toHaveTextContent('11111111 (you)')
    expect(website).toHaveTextContent('Manager')
    expect(website).toHaveTextContent('2 members')
    expect(website).not.toHaveTextContent(OTHER)
    const failed = cards.find((c) => c.textContent?.includes('Data Migration'))!
    expect(failed).toHaveTextContent('Members could not be loaded')
    // With a filter active, only projects that still have a matching member are shown.
    await userEvent.selectOptions(screen.getByLabelText('Role'), 'MANAGER')
    expect(within(screen.getByRole('list', { name: 'Projects' })).getAllByRole('listitem').filter((li) => li.classList.contains('ws-card'))).toHaveLength(1)
  })

  it('tells the user when the organisation has more projects than the page loads', async () => {
    vi.mocked(projectsApi.list).mockImplementation(() => Promise.resolve(page(120)))
    renderTeam()
    expect(await screen.findByRole('note')).toHaveTextContent('3 most recently updated projects out of 120')
  })

  it('shows the error state when the project list fails and an empty state for an org with no projects', async () => {
    vi.mocked(projectsApi.list).mockImplementation(() => Promise.reject(new Error('down')))
    const { unmount } = renderTeam()
    expect(await screen.findByRole('alert')).toHaveTextContent(/something went wrong/i)
    unmount()
    vi.mocked(projectsApi.list).mockImplementation(() => Promise.resolve({ ...page(0), content: [] }))
    renderTeam()
    expect(await screen.findByText('No projects yet')).toBeInTheDocument()
    expect(screen.getByRole('link', { name: 'Go to Projects' })).toHaveAttribute('href', '/projects')
    expect(vi.mocked(membersApi.list)).not.toHaveBeenCalled()
  })
})
