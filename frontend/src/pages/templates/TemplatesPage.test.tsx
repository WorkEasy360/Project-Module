import { describe, expect, it, vi, beforeEach } from 'vitest'
import { render, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { MemoryRouter } from 'react-router-dom'
import { TemplatesPage } from './TemplatesPage'
import { templatesApi } from '../../api/templates'
import type { ProjectTemplate } from '../../types/template'

vi.mock('../../api/templates', () => ({
  templatesApi: { list: vi.fn(), create: vi.fn(), update: vi.fn(), archive: vi.fn(), apply: vi.fn() },
}))

const base = { organizationId: 'o1', description: null, archived: false, createdAt: '', updatedAt: '', version: 0 }
const withPriority: ProjectTemplate = { ...base, id: 'tp1', name: 'hello', defaultPriority: 'HIGH' }
const withoutPriority: ProjectTemplate = { ...base, id: 'tp2', name: 'legacy', defaultPriority: null }

function page(content: ProjectTemplate[]) {
  vi.mocked(templatesApi.list).mockResolvedValue({ content, page: 0, size: 20, totalElements: content.length, totalPages: 1 })
  return render(
    <MemoryRouter>
      <TemplatesPage />
    </MemoryRouter>,
  )
}

describe('TemplatesPage — default priority rule', () => {
  beforeEach(() => vi.clearAllMocks())

  it('creates a template with the (required, pre-selected) default priority', async () => {
    vi.mocked(templatesApi.create).mockResolvedValue(withPriority)
    page([])
    await userEvent.click((await screen.findAllByRole('button', { name: /new template/i }))[0])
    await userEvent.type(screen.getByLabelText(/^name/i), 'hello')
    await userEvent.selectOptions(screen.getByLabelText(/default priority/i), 'HIGH')
    await userEvent.click(screen.getByRole('button', { name: /^save$/i }))
    expect(templatesApi.create).toHaveBeenCalledWith({ name: 'hello', description: null, defaultPriority: 'HIGH' })
  })

  it('never offers a "None" priority, so an unusable template cannot be created from the UI', async () => {
    page([])
    await userEvent.click((await screen.findAllByRole('button', { name: /new template/i }))[0])
    const options = Array.from((screen.getByLabelText(/default priority/i) as HTMLSelectElement).options).map((o) => o.value)
    expect(options).not.toContain('')
    expect(options).toEqual(['LOW', 'MEDIUM', 'HIGH', 'CRITICAL'])
  })

  it('applies a template with a priority and navigates to the created project', async () => {
    vi.mocked(templatesApi.apply).mockResolvedValue({ id: 'newp', name: 'hello copy' } as never)
    page([withPriority])
    await userEvent.click(await screen.findByRole('button', { name: /^apply$/i }))
    await userEvent.click(screen.getByRole('checkbox'))
    await userEvent.click(screen.getByRole('button', { name: /create project from template/i }))
    expect(templatesApi.apply).toHaveBeenCalledWith('tp1', { name: 'hello copy', description: null })
  })

  it('blocks applying a legacy template without a priority and offers to set one instead of calling the API', async () => {
    page([withoutPriority])
    expect(await screen.findByText(/not set — can't apply/i)).toBeInTheDocument()
    await userEvent.click(screen.getByRole('button', { name: /^apply$/i }))
    expect(screen.getByText(/has no default priority/i)).toBeInTheDocument()
    expect(screen.queryByRole('button', { name: /create project from template/i })).not.toBeInTheDocument()

    await userEvent.click(screen.getByRole('button', { name: /set a default priority/i }))
    expect(screen.getByRole('heading', { name: /edit template/i })).toBeInTheDocument()
    expect(templatesApi.apply).not.toHaveBeenCalled()
  })
})
