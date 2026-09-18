import { describe, expect, it, vi, beforeEach } from 'vitest'
import { render, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { CreateAutomationDialog } from './AutomationsTab'
import { automationsApi } from '../../../api/automations'
import { AUTOMATION_TRIGGER_EVENTS } from '../../../types/automation'

vi.mock('../../../api/automations', () => ({
  automationsApi: { create: vi.fn() },
}))

describe('CreateAutomationDialog', () => {
  beforeEach(() => {
    vi.mocked(automationsApi.create).mockReset()
  })

  it('offers exactly the backend trigger-event catalogue in the dropdown', () => {
    render(<CreateAutomationDialog projectId="proj-1" onClose={vi.fn()} onCreated={vi.fn()} />)
    const select = screen.getByLabelText(/this happens/i) as HTMLSelectElement
    expect(select.options).toHaveLength(AUTOMATION_TRIGGER_EVENTS.length)
  })

  it('shows a recipient field for NOTIFY and switches to a channel field for CHAT_MESSAGE', async () => {
    render(<CreateAutomationDialog projectId="proj-1" onClose={vi.fn()} onCreated={vi.fn()} />)

    expect(screen.getByLabelText(/^notify/i)).toBeInTheDocument()
    expect(screen.queryByLabelText(/^chat channel/i)).not.toBeInTheDocument()

    await userEvent.selectOptions(screen.getByLabelText(/do this/i), 'CHAT_MESSAGE')

    expect(screen.queryByLabelText(/^notify/i)).not.toBeInTheDocument()
    expect(screen.getByLabelText(/^chat channel/i)).toBeInTheDocument()
  })

  it('submits a NOTIFY automation with the recipient and message filled in', async () => {
    vi.mocked(automationsApi.create).mockResolvedValue({
      id: 'a1',
      projectId: 'proj-1',
      name: 'Notify me',
      description: null,
      triggerEvent: 'task.overdue',
      actionType: 'NOTIFY',
      actionRecipientId: 'user-1',
      actionChannelReference: null,
      actionMessage: 'A task is overdue',
      enabled: true,
      archived: false,
      createdAt: '2026-01-01T00:00:00Z',
      updatedAt: '2026-01-01T00:00:00Z',
      version: 0,
    })
    const onCreated = vi.fn()

    render(<CreateAutomationDialog projectId="proj-1" onClose={vi.fn()} onCreated={onCreated} />)

    await userEvent.type(screen.getByLabelText(/^name/i), 'Notify me')
    await userEvent.selectOptions(screen.getByLabelText(/this happens/i), 'task.overdue')
    await userEvent.type(screen.getByLabelText(/^notify/i), 'user-1')
    await userEvent.type(screen.getByLabelText(/^message/i), 'A task is overdue')
    await userEvent.click(screen.getByRole('button', { name: /create automation/i }))

    expect(automationsApi.create).toHaveBeenCalledWith(
      'proj-1',
      expect.objectContaining({
        name: 'Notify me',
        triggerEvent: 'task.overdue',
        actionType: 'NOTIFY',
        actionRecipientId: 'user-1',
        actionChannelReference: null,
        actionMessage: 'A task is overdue',
      }),
    )
    expect(onCreated).toHaveBeenCalled()
  })
})
