import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { render, screen, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { MemoryRouter, Route, Routes } from 'react-router-dom'
import { ProjectsListPage, projectLook } from './ProjectsListPage'
import { filterProjectsByName } from './projectsListUtils'
import { projectsApi } from '../../api/projects'
import { membersApi } from '../../api/members'
import { PeopleProvider } from '../../context/PeopleContext'
import { IdentityProvider } from '../../context/IdentityContext'
import { setIdentity } from '../../context/identityStore'
import type { ProjectSummary } from '../../types/project'

vi.mock('../../api/projects', () => ({ projectsApi: { list: vi.fn(), create: vi.fn() } }))
vi.mock('../../api/members', () => ({ membersApi: { list: vi.fn() } }))
vi.mock('../../api/workspace', () => ({
  workspaceApi: {
    people: vi.fn(() =>
      Promise.resolve([
        { id: 'manoj', organizationId: 'o', email: 'm@x', name: 'Manoj Kumar', workspaceRole: 'ADMIN' },
        { id: 'deepti', organizationId: 'o', email: 'd@x', name: 'Deepti Verma', workspaceRole: 'MEMBER' },
      ]),
    ),
  },
}))

function project(over: Partial<ProjectSummary>): ProjectSummary {
  return {
    id: 'p',
    name: 'P',
    status: 'ACTIVE',
    priority: 'MEDIUM',
    ownerId: 'manoj',
    updatedAt: '2026-11-10T00:00:00Z',
    createdAt: '2026-11-01T00:00:00Z',
    startDate: '2026-11-01',
    targetEndDate: '2026-11-30',
    customerContact: null,
    tags: [],
    taskCount: 0,
    completedTaskCount: 0,
    ...over,
  }
}

const LIVE = [
  project({ id: 'w', name: 'Website Redesign', customerContact: 'Rajni Machinery Store', tags: ['Website', 'Marketing'], taskCount: 20, completedTaskCount: 16 }),
  project({ id: 'l', name: 'Product Launch', customerContact: 'Rachana Plastics', tags: ['Product', 'Sales'], ownerId: 'deepti', taskCount: 20, completedTaskCount: 12, targetEndDate: '2026-12-15' }),
  project({ id: 'm', name: 'Marketing Campaign', customerContact: 'Iconic Traders', status: 'ON_HOLD', tags: ['Marketing', 'Ads'], taskCount: 20, completedTaskCount: 8 }),
  project({ id: 'o', name: 'Operations Setup', status: 'COMPLETED' }),
  project({ id: 'e', name: 'Event Management', status: 'ACTIVE' }),
  project({ id: 'x', name: 'Extra One' }),
]
const ARCHIVED = [project({ id: 'old', name: 'Old Brochure', archived: true, customerContact: 'Sunrise Printers' })]

function renderPage(path = '/projects') {
  return render(
    <MemoryRouter initialEntries={[path]}>
      <IdentityProvider>
        <PeopleProvider>
          <Routes>
            <Route path="/projects" element={<ProjectsListPage />} />
            <Route path="/projects/archived" element={<ProjectsListPage archived />} />
            <Route path="/projects/:id" element={<div>PROJECT PAGE</div>} />
          </Routes>
        </PeopleProvider>
      </IdentityProvider>
    </MemoryRouter>,
  )
}

describe('ProjectsListPage', () => {
  beforeEach(() => {
    window.localStorage.clear()
    setIdentity({ userId: 'manoj', orgId: 'o', token: 't', name: 'Manoj Kumar' })
    vi.mocked(projectsApi.list).mockImplementation(async (_page, _size, _sort, archived) => {
      const content = archived ? ARCHIVED : LIVE
      return { content, page: 0, size: 200, totalElements: content.length, totalPages: 1 }
    })
    vi.mocked(membersApi.list).mockImplementation(async (projectId) =>
      projectId === 'l'
        ? [{ id: 'm1', projectId, userId: 'deepti', role: 'OWNER', joinedAt: '', version: 0 }]
        : [{ id: 'm2', projectId, userId: 'manoj', role: 'OWNER', joinedAt: '', version: 0 }],
    )
  })
  afterEach(() => {
    setIdentity(null)
    vi.clearAllMocks()
  })

  it('shows the counts, then a table with customer, owner, progress, tasks, dates and status', async () => {
    renderPage()
    const counts = screen.getByRole('region', { name: 'Project counts' })
    expect(await within(counts).findByText('6')).toBeInTheDocument()
    const tile = (label: string) => within(counts).getByText(label).parentElement?.querySelector('.ops-tile-value')?.textContent
    expect(tile('Active')).toBe('4')
    expect(tile('On Hold')).toBe('1')
    expect(tile('Completed')).toBe('1')
    expect(tile('Archived')).toBe('1')

    const row = screen.getByText('Website Redesign').closest('tr') as HTMLElement
    expect(within(row).getByText('Rajni Machinery Store')).toBeInTheDocument()
    expect(within(row).getByText('Website')).toBeInTheDocument()
    expect(within(row).getByText('80%')).toBeInTheDocument()
    expect(within(row).getByText('16 / 20')).toBeInTheDocument()
    expect(within(row).getByText('01 Nov 2026')).toBeInTheDocument()
    expect(within(row).getByText('30 Nov 2026')).toBeInTheDocument()
    expect(within(row).getByText('Active')).toBeInTheDocument()
    expect(within(row).getByRole('link', { name: 'Open Website Redesign' })).toHaveAttribute('href', '/projects/w')
  })

  it('pages five at a time, as in the design', async () => {
    renderPage()
    expect(await screen.findByText('Showing 1 – 5 of 6 projects')).toBeInTheDocument()
    await userEvent.click(screen.getByRole('button', { name: 'Next page' }))
    expect(screen.getByText('Showing 6 – 6 of 6 projects')).toBeInTheDocument()
    expect(screen.getByText('Extra One')).toBeInTheDocument()
  })

  it('searches by name, customer or tag, and filters by status and member', async () => {
    renderPage()
    await screen.findByText('Website Redesign')
    const search = screen.getByRole('searchbox', { name: /search projects/i })
    await userEvent.type(search, 'rachana')
    expect(screen.getByText('Product Launch')).toBeInTheDocument()
    expect(screen.queryByText('Website Redesign')).not.toBeInTheDocument()
    await userEvent.clear(search)
    await userEvent.type(search, 'ads')
    expect(screen.getByText('Marketing Campaign')).toBeInTheDocument()
    await userEvent.clear(search)

    await userEvent.selectOptions(screen.getByRole('combobox', { name: 'Status' }), 'ON_HOLD')
    expect(screen.getByText('Showing 1 – 1 of 1 projects')).toBeInTheDocument()
    await userEvent.selectOptions(screen.getByRole('combobox', { name: 'Status' }), '')

    await userEvent.selectOptions(screen.getByRole('combobox', { name: 'Members' }), 'deepti')
    expect(await screen.findByText('Showing 1 – 1 of 1 projects')).toBeInTheDocument()
    expect(screen.getByText('Product Launch')).toBeInTheDocument()
  })

  it('switches to a board grouped by status, and remembers it', async () => {
    renderPage()
    await screen.findByText('Website Redesign')
    await userEvent.click(screen.getByRole('button', { name: /board/i }))
    expect(screen.getByText('On Hold', { selector: '.ops-board-col .ops-status' })).toBeInTheDocument()
    expect(window.localStorage.getItem('projectmodule.projectsView2')).toBe('"board"')
  })

  it('lists archived projects read only', async () => {
    renderPage('/projects/archived')
    expect(screen.getByRole('heading', { name: 'Archived Projects' })).toBeInTheDocument()
    expect(await screen.findByText('Old Brochure')).toBeInTheDocument()
    expect(screen.queryByRole('button', { name: /create project/i })).not.toBeInTheDocument()
    expect(screen.queryByRole('link', { name: 'Open Old Brochure' })).not.toBeInTheDocument()
  })

  it('picks a picture from what the project is about', () => {
    expect(projectLook('Website Redesign')).toEqual({ icon: 'monitor', tone: 'purple' })
    expect(projectLook('Product Launch')).toEqual({ icon: 'megaphone', tone: 'orange' })
    expect(projectLook('Operations Setup')).toEqual({ icon: 'settings', tone: 'green' })
  })

  it('still filters names for other callers', () => {
    expect(filterProjectsByName(LIVE, 'launch').map((p) => p.id)).toEqual(['l'])
  })
})
