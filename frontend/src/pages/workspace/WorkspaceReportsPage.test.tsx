import { describe, expect, it, vi, beforeEach } from 'vitest'
import { render, screen, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { MemoryRouter } from 'react-router-dom'
import { WorkspaceReportsPage } from './WorkspaceReportsPage'
import { IdentityProvider } from '../../context/IdentityContext'
import { setIdentity } from '../../context/identityStore'
import { projectsApi } from '../../api/projects'
import { reportsApi } from '../../api/reports'
import type { ProjectSummaryReport } from '../../types/work'

vi.mock('../../api/projects', () => ({ projectsApi: { list: vi.fn() } }))
vi.mock('../../api/reports', () => ({ reportsApi: { summary: vi.fn() } }))

const projects = [
  { id: 'p1', name: 'Website Redesign', status: 'ACTIVE', priority: 'HIGH', ownerId: 'u', updatedAt: '2026-09-10T00:00:00Z' },
  { id: 'p2', name: 'Mobile App', status: 'PLANNING', priority: 'MEDIUM', ownerId: 'u', updatedAt: '2026-09-09T00:00:00Z' },
  { id: 'p3', name: 'Data Migration', status: 'ON_HOLD', priority: 'LOW', ownerId: 'u', updatedAt: '2026-09-08T00:00:00Z' },
] as const
const empty: ProjectSummaryReport = {
  taskCountByStatus: { TODO: 0, BLOCKED: 0, OVERDUE: 0, COMPLETED: 0 },
  riskCountByStatus: { OPEN: 0, RESOLVED: 0 },
  riskCountByPriority: { LOW: 0, MEDIUM: 0, HIGH: 0, CRITICAL: 0 },
  issueCountByPriority: { LOW: 0, MEDIUM: 0, HIGH: 0, CRITICAL: 0 },
  decisionCount: 0,
  delayedItemCount: 0,
  brokenDependencyCount: 0,
}
const reportsById: Record<string, ProjectSummaryReport> = {
  p1: {
    taskCountByStatus: { TODO: 2, BLOCKED: 1, OVERDUE: 0, COMPLETED: 3 },
    riskCountByStatus: { OPEN: 1, RESOLVED: 2 },
    riskCountByPriority: { LOW: 1, MEDIUM: 0, HIGH: 1, CRITICAL: 0 },
    issueCountByPriority: { LOW: 0, MEDIUM: 2, HIGH: 0, CRITICAL: 1 },
    decisionCount: 4,
    delayedItemCount: 1,
    brokenDependencyCount: 0,
  },
  p2: { ...empty, decisionCount: 1 },
}

function page(totalElements = 3) {
  return { content: [...projects], page: 0, size: 50, totalElements, totalPages: 1 }
}
function renderReports() {
  return render(
    <MemoryRouter>
      <IdentityProvider>
        <WorkspaceReportsPage />
      </IdentityProvider>
    </MemoryRouter>,
  )
}
function cardNames() {
  return within(screen.getByRole('list', { name: 'Project reports' }))
    .getAllByRole('heading', { level: 3 })
    .map((h) => h.textContent)
}

describe('WorkspaceReportsPage', () => {
  beforeEach(() => {
    setIdentity({ userId: '11111111-2222-4333-8444-555555555555', orgId: '99999999-2222-4333-8444-555555555555' })
    vi.mocked(projectsApi.list).mockReset()
    vi.mocked(reportsApi.summary).mockReset()
    vi.mocked(projectsApi.list).mockImplementation(() => Promise.resolve(page()))
    vi.mocked(reportsApi.summary).mockImplementation((id: string) =>
      id === 'p3' ? Promise.reject(new Error('boom')) : Promise.resolve(reportsById[id]),
    )
  })

  it('renders one card per project with exactly the summary report fields, a real progress bar and a link to the full report', async () => {
    renderReports()
    const grid = await screen.findByRole('list', { name: 'Project reports' })
    const website = within(grid).getByRole('link', { name: 'Website Redesign' }).closest('li')!
    expect(within(website).getByRole('link', { name: 'Website Redesign' })).toHaveAttribute('href', '/projects/p1/reports')
    expect(within(website).getByRole('link', { name: /Full report/ })).toHaveAttribute('href', '/projects/p1/reports')
    expect(website).toHaveTextContent('Active')
    // 3 completed of 6 tasks -> 50% (the card exists before its report resolves, so wait for it)
    expect(await within(website).findByRole('progressbar')).toHaveAttribute('aria-valuenow', '50')
    expect(website).toHaveTextContent('3/6 tasks · 50%')
    expect(website).toHaveTextContent('2Todo')
    expect(website).toHaveTextContent('1Blocked')
    expect(website).toHaveTextContent('3Completed')
    expect(website).toHaveTextContent('1Open')
    expect(website).toHaveTextContent('2Resolved')
    expect(website).toHaveTextContent('4Decisions')
    expect(website).toHaveTextContent('1Delayed items')
    expect(website).toHaveTextContent('0Broken dependencies')

    // A project with no tasks never gets a fabricated percentage.
    const mobile = within(grid).getByRole('link', { name: 'Mobile App' }).closest('li')!
    expect(within(mobile).queryByRole('progressbar')).not.toBeInTheDocument()
    expect(mobile).toHaveTextContent('No tasks yet.')
    expect(mobile).toHaveTextContent('1Decisions')

    // The failing project keeps its card, with the error inline.
    const failed = within(grid).getByRole('link', { name: 'Data Migration' }).closest('li')!
    expect(failed).toHaveTextContent('Report could not be loaded')
    expect(screen.getByRole('alert')).toHaveTextContent('1 project could not be loaded')
    expect(within(screen.getByRole('alert')).getByRole('button', { name: 'Retry' })).toBeInTheDocument()
  })

  it('"Across your projects" sums the loaded reports client-side and says so', async () => {
    renderReports()
    const strip = await screen.findByRole('region', { name: 'Across your projects' })
    await within(strip).findByText('Computed from the loaded project reports (2 of 3).')
    const value = (label: string) => within(strip).getByText(label).previousSibling
    expect(value('Tasks')).toHaveTextContent('6')
    expect(value('Completed tasks')).toHaveTextContent('3')
    expect(value('Blocked tasks')).toHaveTextContent('1')
    expect(value('Overdue tasks')).toHaveTextContent('0')
    expect(value('Open risks')).toHaveTextContent('1')
    expect(value('Issues')).toHaveTextContent('3')
    expect(value('Decisions')).toHaveTextContent('5')
    expect(value('Delayed items')).toHaveTextContent('1')
    expect(value('Broken dependencies')).toHaveTextContent('0')
  })

  it('sorts by name or status and filters by status', async () => {
    renderReports()
    await screen.findByRole('list', { name: 'Project reports' })
    expect(cardNames()).toEqual(['Data Migration', 'Mobile App', 'Website Redesign'])
    await userEvent.selectOptions(screen.getByLabelText('Sort by'), 'name,DESC')
    expect(cardNames()).toEqual(['Website Redesign', 'Mobile App', 'Data Migration'])
    await userEvent.selectOptions(screen.getByLabelText('Sort by'), 'status')
    expect(cardNames()).toEqual(['Mobile App', 'Website Redesign', 'Data Migration'])
    await userEvent.selectOptions(screen.getByLabelText('Status'), 'ACTIVE')
    expect(cardNames()).toEqual(['Website Redesign'])
    expect(screen.getByText('Showing 1 of 3 loaded')).toBeInTheDocument()
    await userEvent.selectOptions(screen.getByLabelText('Status'), 'CANCELLED')
    expect(screen.getByText('No projects match this filter.')).toBeInTheDocument()
  })

  it('notes the page limit, and shows error / empty states for the project list', async () => {
    vi.mocked(projectsApi.list).mockImplementation(() => Promise.resolve(page(75)))
    const { unmount } = renderReports()
    expect(await screen.findByRole('note')).toHaveTextContent('3 most recently updated projects out of 75')
    unmount()

    vi.mocked(projectsApi.list).mockImplementation(() => Promise.reject(new Error('down')))
    const second = renderReports()
    expect(await screen.findByRole('alert')).toHaveTextContent(/something went wrong/i)
    second.unmount()

    vi.mocked(projectsApi.list).mockImplementation(() => Promise.resolve({ ...page(0), content: [] }))
    renderReports()
    expect(await screen.findByText('No projects yet')).toBeInTheDocument()
    expect(vi.mocked(reportsApi.summary)).toHaveBeenCalledTimes(3)
  })
})
