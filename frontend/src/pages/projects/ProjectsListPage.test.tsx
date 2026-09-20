import { describe, expect, it, vi, beforeEach } from 'vitest'
import { render, screen, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { MemoryRouter } from 'react-router-dom'
import { ProjectsListPage } from './ProjectsListPage'
import { filterProjectsByName } from './projectsListUtils'
import { projectsApi } from '../../api/projects'
import type { ProjectSummary } from '../../types/project'

vi.mock('../../api/projects', () => ({ projectsApi: { list: vi.fn(), create: vi.fn() } }))

const projects: ProjectSummary[] = [
  { id: 'p1', name: 'Website Redesign', status: 'ACTIVE', priority: 'HIGH', ownerId: 'owner-0001-aaaa', updatedAt: '2026-09-10T00:00:00Z' },
  { id: 'p2', name: 'Mobile App', status: 'PLANNING', priority: 'MEDIUM', ownerId: 'owner-0002-bbbb', updatedAt: '2026-09-09T00:00:00Z' },
  { id: 'p3', name: 'Data Migration', status: 'ON_HOLD', priority: 'LOW', ownerId: 'owner-0003-cccc', updatedAt: '2026-09-08T00:00:00Z' },
]

function renderPage(path = '/projects') {
  return render(
    <MemoryRouter initialEntries={[path]}>
      <ProjectsListPage />
    </MemoryRouter>,
  )
}

const projectLinks = () => screen.getAllByRole('link', { name: /^(Website Redesign|Mobile App|Data Migration)$/ })

describe('filterProjectsByName', () => {
  it('matches case-insensitively and returns everything for a blank query', () => {
    expect(filterProjectsByName(projects, 'MOB').map((p) => p.id)).toEqual(['p2'])
    expect(filterProjectsByName(projects, '  ')).toHaveLength(3)
    expect(filterProjectsByName(projects, 'zzz')).toHaveLength(0)
  })
})

describe('ProjectsListPage', () => {
  beforeEach(() => {
    localStorage.clear()
    vi.mocked(projectsApi.list).mockReset()
    vi.mocked(projectsApi.list).mockImplementation(() =>
      Promise.resolve({ content: [...projects], page: 0, size: 20, totalElements: 3, totalPages: 1 }),
    )
  })

  it('renders the loaded page as cards by default with real status, priority, owner and date', async () => {
    renderPage()
    const grid = await screen.findByRole('list', { name: 'Projects' })
    expect(within(grid).getAllByRole('listitem')).toHaveLength(3)
    const card = within(grid).getByRole('link', { name: 'Website Redesign' }).closest('li')!
    expect(within(card).getByRole('link', { name: 'Website Redesign' })).toHaveAttribute('href', '/projects/p1')
    expect(card).toHaveTextContent('Active')
    expect(card).toHaveTextContent('High')
    expect(card).toHaveTextContent('owner-00…')
    expect(vi.mocked(projectsApi.list)).toHaveBeenCalledWith(0, 20, 'createdAt,DESC')
    expect(screen.queryByText(/Filtered by/)).not.toBeInTheDocument()
  })

  it('filters the loaded page by ?q= (case-insensitive) with a chip, an honest note, and clears it', async () => {
    renderPage('/projects?q=MOB')
    await screen.findByRole('list', { name: 'Projects' })
    expect(projectLinks()).toHaveLength(1)
    expect(screen.getByText('Mobile App')).toBeInTheDocument()
    expect(screen.getByText('Filtered by “MOB”')).toBeInTheDocument()
    expect(screen.getByText(/Filtering applies to the 3 projects loaded on this page/)).toBeInTheDocument()

    await userEvent.click(screen.getByRole('button', { name: 'Clear filter' }))
    expect(screen.queryByText(/Filtered by/)).not.toBeInTheDocument()
    expect(projectLinks()).toHaveLength(3)
    // Clearing the filter never refetches: filtering was client-side over the loaded page.
    expect(vi.mocked(projectsApi.list)).toHaveBeenCalledTimes(1)
  })

  it('shows a page-scoped empty state when nothing on the loaded page matches', async () => {
    renderPage('/projects?q=zzz')
    expect(await screen.findByText('No projects on this page match “zzz”.')).toBeInTheDocument()
    // Both the chip's "×" and the empty state offer to clear; either works.
    await userEvent.click(screen.getAllByRole('button', { name: 'Clear filter' })[1])
    expect(await screen.findByRole('list', { name: 'Projects' })).toBeInTheDocument()
  })

  it('toggles between card and list views and persists the choice in localStorage', async () => {
    renderPage()
    await screen.findByRole('list', { name: 'Projects' })
    await userEvent.click(screen.getByRole('button', { name: 'List view' }))
    expect(screen.queryByRole('list', { name: 'Projects' })).not.toBeInTheDocument()
    expect(screen.getByRole('table')).toBeInTheDocument()
    expect(within(screen.getByRole('table')).getByRole('link', { name: 'Data Migration' })).toHaveAttribute('href', '/projects/p3')
    expect(localStorage.getItem('projectmodule.projectsView')).toBe('list')

    await userEvent.click(screen.getByRole('button', { name: 'Card view' }))
    expect(screen.getByRole('list', { name: 'Projects' })).toBeInTheDocument()
    expect(localStorage.getItem('projectmodule.projectsView')).toBe('card')
  })

  it('restores the stored list view and still applies the q filter to the table', async () => {
    localStorage.setItem('projectmodule.projectsView', 'list')
    renderPage('/projects?q=data')
    const table = await screen.findByRole('table')
    expect(within(table).getAllByRole('link')).toHaveLength(1)
    expect(within(table).getByRole('link', { name: 'Data Migration' })).toBeInTheDocument()
  })

  it('shows the error state with retry when the list API fails', async () => {
    vi.mocked(projectsApi.list).mockImplementation(() => Promise.reject(new Error('boom')))
    renderPage()
    expect(await screen.findByRole('alert')).toHaveTextContent(/something went wrong/i)
    expect(screen.getByRole('button', { name: /try again/i })).toBeInTheDocument()
  })
})
