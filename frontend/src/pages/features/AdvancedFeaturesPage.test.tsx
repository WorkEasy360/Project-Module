import { describe, expect, it, vi } from 'vitest'
import { render, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { MemoryRouter } from 'react-router-dom'
import { AdvancedFeaturesPage } from './AdvancedFeaturesPage'
import { ProjectWorkspaceContext, type ProjectWorkspaceValue } from '../../context/ProjectWorkspaceContext'
import type { Project, ProjectMember } from '../../types/project'

const project: Project = {
  id: 'p1',
  organizationId: 'o1',
  name: 'Website',
  description: null,
  status: 'ACTIVE',
  priority: 'MEDIUM',
  startDate: null,
  targetEndDate: null,
  ownerId: 'u1',
  archived: false,
  archivedAt: null,
  archivedBy: null,
  createdAt: '2026-01-01T00:00:00Z',
  updatedAt: '2026-01-01T00:00:00Z',
  version: 0,
}

function renderInProject(role: ProjectMember['role']) {
  const member: ProjectMember = { id: 'm1', projectId: 'p1', userId: 'u1', role, joinedAt: '', version: 0 }
  const value: ProjectWorkspaceValue = {
    project,
    reloadProject: vi.fn(),
    currentMember: member,
    can: (p) => (role === 'OWNER' ? true : role === 'VIEWER' ? p === 'VIEW_PROJECT' : true),
  }
  return render(
    <MemoryRouter initialEntries={['/projects/p1/more']}>
      <ProjectWorkspaceContext.Provider value={value}>
        <AdvancedFeaturesPage />
      </ProjectWorkspaceContext.Provider>
    </MemoryRouter>,
  )
}

describe('AdvancedFeaturesPage', () => {
  it('lists advanced project features with plain-language names and links to real routes', () => {
    renderInProject('OWNER')
    expect(screen.getByRole('heading', { name: 'Task Relationships' })).toBeInTheDocument()
    expect(screen.getByText('Dependency Analysis')).toBeInTheDocument()
    const openLinks = screen.getAllByRole('link', { name: /^open$/i })
    expect(openLinks.some((a) => a.getAttribute('href') === '/projects/p1/gantt')).toBe(true)
  })

  it('marks a feature as requiring permission for a VIEWER and explains how access is granted', async () => {
    renderInProject('VIEWER')
    const badges = screen.getAllByText('Requires permission')
    expect(badges.length).toBeGreaterThan(0)

    await userEvent.click(screen.getAllByRole('button', { name: /request access/i })[0])
    expect(await screen.findByText(/no in-app access-request workflow yet/i)).toBeInTheDocument()
  })

  it('shows Activity as coming soon without an Open link', () => {
    renderInProject('OWNER')
    const card = screen.getByRole('heading', { name: 'Activity' }).closest('article')!
    expect(card).toHaveTextContent('Coming soon')
    expect(card.querySelector('a')).toBeNull()
  })

  it('never lists workspace-level features such as Templates (they are direct sidebar links)', () => {
    renderInProject('OWNER')
    expect(screen.queryByRole('heading', { name: /templates/i })).not.toBeInTheDocument()
    expect(screen.getByRole('heading', { name: 'Gantt' })).toBeInTheDocument()
  })
})
