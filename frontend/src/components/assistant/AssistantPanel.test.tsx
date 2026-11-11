import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { render, screen, waitFor } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { MemoryRouter, Route, Routes } from 'react-router-dom'
import { AssistantPanel } from './AssistantPanel'
import { assistantApi } from '../../api/assistant'
import { IdentityProvider } from '../../context/IdentityContext'

vi.mock('../../api/assistant', () => ({
  assistantApi: { status: vi.fn(), chat: vi.fn(), decide: vi.fn() },
}))

const reply = (over: Partial<Awaited<ReturnType<typeof assistantApi.chat>>>) => ({
  conversationId: 'c1',
  messages: [],
  pendingChanges: [],
  navigateTo: null,
  awaitingApproval: false,
  ...over,
})

function renderPanel() {
  return render(
    <IdentityProvider>
    <MemoryRouter initialEntries={['/dashboard']}>
      <Routes>
        <Route path="*" element={<AssistantPanel />} />
      </Routes>
      <Routes>
        <Route path="/timesheet" element={<div>TIMESHEET</div>} />
        <Route path="*" element={null} />
      </Routes>
    </MemoryRouter>
    </IdentityProvider>,
  )
}

async function openAndSend(text: string) {
  await userEvent.click(screen.getByRole('button', { name: 'Open the assistant' }))
  await userEvent.type(screen.getByLabelText('Message the assistant'), text)
  await userEvent.click(screen.getByRole('button', { name: 'Send' }))
}

describe('AssistantPanel', () => {
  beforeEach(() => {
    window.sessionStorage.clear()
    vi.mocked(assistantApi.status).mockResolvedValue({ enabled: true })
  })
  afterEach(() => vi.clearAllMocks())

  it('sends the question with where the person is, and shows the answer', async () => {
    vi.mocked(assistantApi.chat).mockResolvedValue(reply({ messages: ['You have 2 overdue tasks.'] }))
    renderPanel()
    await openAndSend('Anything overdue?')
    expect(await screen.findByText('You have 2 overdue tasks.')).toBeInTheDocument()
    expect(assistantApi.chat).toHaveBeenCalledWith(
      expect.objectContaining({ conversationId: null, message: 'Anything overdue?', context: expect.stringContaining('page /dashboard') }),
    )
  })

  it('shows a proposed change and makes it only when approved', async () => {
    vi.mocked(assistantApi.chat).mockResolvedValue(reply({ pendingChanges: ['Create task "Write post" in Apollo'], awaitingApproval: true }))
    vi.mocked(assistantApi.decide).mockResolvedValue(reply({ messages: ['Created it.'] }))
    renderPanel()
    await openAndSend('Add a task to write the post')
    expect(await screen.findByText('Create task "Write post" in Apollo')).toBeInTheDocument()
    expect(assistantApi.decide).not.toHaveBeenCalled()
    await userEvent.click(screen.getByRole('button', { name: 'Approve' }))
    expect(assistantApi.decide).toHaveBeenCalledWith('c1', true)
    expect(await screen.findByText('Created it.')).toBeInTheDocument()
    expect(screen.queryByRole('button', { name: 'Approve' })).not.toBeInTheDocument()
  })

  it('declining sends a decline', async () => {
    vi.mocked(assistantApi.chat).mockResolvedValue(reply({ pendingChanges: ['Log 1h on "Write post"'], awaitingApproval: true }))
    vi.mocked(assistantApi.decide).mockResolvedValue(reply({ messages: ['Okay, I left it.'] }))
    renderPanel()
    await openAndSend('Log an hour')
    await userEvent.click(await screen.findByRole('button', { name: 'Decline' }))
    expect(assistantApi.decide).toHaveBeenCalledWith('c1', false)
  })

  it('opens the page the assistant chose', async () => {
    vi.mocked(assistantApi.chat).mockResolvedValue(reply({ messages: ['Here it is.'], navigateTo: '/timesheet' }))
    renderPanel()
    await openAndSend('Open my timesheet')
    expect(await screen.findByText('TIMESHEET')).toBeInTheDocument()
  })

  it('says plainly when it is not set up, and cannot be used', async () => {
    vi.mocked(assistantApi.status).mockResolvedValue({ enabled: false })
    renderPanel()
    await userEvent.click(screen.getByRole('button', { name: 'Open the assistant' }))
    expect(await screen.findByText(/isn't set up yet/)).toBeInTheDocument()
    await waitFor(() => expect(screen.getByLabelText('Message the assistant')).toBeDisabled())
  })

  it('shows the server\'s reason when something goes wrong', async () => {
    const { ApiError } = await import('../../api/client')
    vi.mocked(assistantApi.chat).mockRejectedValue(
      new ApiError(503, { type: 't', title: 't', status: 503, detail: 'The assistant is busy right now. Try again in a minute.' }, 'x'),
    )
    renderPanel()
    await openAndSend('Hi')
    expect(await screen.findByText(/busy right now/)).toBeInTheDocument()
  })
})

describe('AssistantPanel voice and hand-off', () => {
  beforeEach(() => {
    window.sessionStorage.clear()
    vi.mocked(assistantApi.status).mockResolvedValue({ enabled: true })
  })
  afterEach(() => {
    vi.clearAllMocks()
    delete (window as unknown as { webkitSpeechRecognition?: unknown }).webkitSpeechRecognition
  })

  /** A microphone that says each phrase in turn, one per listening turn, then stays silent. */
  function fakeMicrophone(phrases: string[]) {
    const starts: number[] = []
    class FakeRecognition {
      lang = ''
      interimResults = false
      continuous = false
      onresult: ((e: unknown) => void) | null = null
      onend: (() => void) | null = null
      onerror: ((e: unknown) => void) | null = null
      ended = false
      start() {
        starts.push(Date.now())
        const phrase = phrases.shift()
        setTimeout(() => {
          if (phrase) this.onresult?.({ results: [Object.assign([{ transcript: phrase }], { isFinal: true })] })
          this.end()
        }, 0)
      }
      stop() {
        this.end()
      }
      end() {
        if (this.ended) return
        this.ended = true
        this.onend?.()
      }
    }
    ;(window as unknown as { webkitSpeechRecognition: unknown }).webkitSpeechRecognition = FakeRecognition
    return starts
  }

  it('hears a question and sends it', async () => {
    fakeMicrophone(['What is overdue'])
    vi.mocked(assistantApi.chat).mockResolvedValue(reply({ messages: ['Two tasks are overdue.'] }))
    renderPanel()
    await userEvent.click(screen.getByRole('button', { name: 'Open the assistant' }))
    await userEvent.click(await screen.findByRole('button', { name: 'Speak to the assistant' }))
    expect(await screen.findByText('Two tasks are overdue.')).toBeInTheDocument()
    expect(assistantApi.chat).toHaveBeenCalledWith(expect.objectContaining({ message: 'What is overdue' }))
  })

  it('keeps the conversation going by voice until the person says stop', async () => {
    const starts = fakeMicrophone(['What is overdue', 'And what is due tomorrow', 'stop'])
    vi.mocked(assistantApi.chat)
      .mockResolvedValueOnce(reply({ messages: ['Two tasks are overdue.'] }))
      .mockResolvedValueOnce(reply({ messages: ['One task is due tomorrow.'] }))
    renderPanel()
    await userEvent.click(screen.getByRole('button', { name: 'Open the assistant' }))
    await userEvent.click(await screen.findByRole('button', { name: 'Speak to the assistant' }))
    expect(await screen.findByText('One task is due tomorrow.')).toBeInTheDocument()
    expect(await screen.findByText('Voice chat ended.')).toBeInTheDocument()
    expect(assistantApi.chat).toHaveBeenCalledTimes(2)
    expect(assistantApi.chat).toHaveBeenLastCalledWith(expect.objectContaining({ message: 'And what is due tomorrow' }))
    expect(starts.length).toBe(3)
    expect(screen.getByRole('button', { name: 'Speak to the assistant' })).toBeInTheDocument()
  })

  it('approves a proposed change when the person says approve', async () => {
    fakeMicrophone(['Add a task to write the post', 'approve'])
    vi.mocked(assistantApi.chat).mockResolvedValue(reply({ pendingChanges: ['Create task "Write post" in Apollo'] }))
    vi.mocked(assistantApi.decide).mockResolvedValue(reply({ messages: ['Created it.'] }))
    renderPanel()
    await userEvent.click(screen.getByRole('button', { name: 'Open the assistant' }))
    await userEvent.click(await screen.findByRole('button', { name: 'Speak to the assistant' }))
    expect(await screen.findByText('Created it.')).toBeInTheDocument()
    expect(assistantApi.decide).toHaveBeenCalledWith('c1', true)
    await userEvent.click(screen.getByRole('button', { name: 'End voice chat' }))
  })

  it('ending the voice chat with the button turns the microphone off', async () => {
    const starts = fakeMicrophone([])
    renderPanel()
    await userEvent.click(screen.getByRole('button', { name: 'Open the assistant' }))
    await userEvent.click(await screen.findByRole('button', { name: 'Speak to the assistant' }))
    expect(screen.getByRole('status')).toHaveTextContent(/stop/)
    await userEvent.click(screen.getByRole('button', { name: 'End voice chat' }))
    const count = starts.length
    await new Promise((r) => setTimeout(r, 400))
    expect(starts.length).toBe(count)
    expect(screen.getByText('Voice chat ended.')).toBeInTheDocument()
  })

  it('hides the microphone where the browser cannot listen', async () => {
    renderPanel()
    await userEvent.click(screen.getByRole('button', { name: 'Open the assistant' }))
    expect(screen.queryByRole('button', { name: 'Speak to the assistant' })).not.toBeInTheDocument()
  })

  it('"Hand off a job" starts a message the person completes', async () => {
    renderPanel()
    await userEvent.click(screen.getByRole('button', { name: 'Open the assistant' }))
    await userEvent.click(await screen.findByRole('button', { name: /hand off a job/i }))
    expect((screen.getByLabelText('Message the assistant') as HTMLTextAreaElement).value).toMatch(/^Here is a job for you/)
  })

  it('opens from elsewhere in the app with a question, as Guide me does', async () => {
    const { openAssistant } = await import('./AssistantPanel')
    vi.mocked(assistantApi.chat).mockResolvedValue(reply({ messages: ['Start with your projects.'] }))
    renderPanel()
    await waitFor(() => expect(assistantApi.status).toHaveBeenCalled())
    const { act } = await import('@testing-library/react')
    act(() => openAssistant('Guide me'))
    expect(await screen.findByText('Start with your projects.')).toBeInTheDocument()
  })

  it('shows bold and lists from the assistant as formatting, never as markup', async () => {
    vi.mocked(assistantApi.chat).mockResolvedValue(reply({ messages: ['**Apollo** is on track:\n- 3 open\n- <b>1</b> blocked'] }))
    renderPanel()
    await openAndSend('How is Apollo?')
    expect(await screen.findByText('Apollo', { selector: 'strong' })).toBeInTheDocument()
    expect(screen.getByText('<b>1</b> blocked')).toBeInTheDocument()
  })
})
