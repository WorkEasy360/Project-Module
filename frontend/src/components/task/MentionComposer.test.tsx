import { describe, expect, it, vi } from 'vitest'
import { render, screen, waitFor } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { MentionComposer } from './MentionComposer'

const PEOPLE = [
  { value: 'u-1', label: 'Asha Rao' },
  { value: 'u-2', label: 'Ben Lee' },
]

function setup() {
  const onSubmit = vi.fn(() => Promise.resolve())
  render(<MentionComposer id="c" label="Comment" people={PEOPLE} onSubmit={onSubmit} />)
  return { onSubmit, box: screen.getByLabelText('Comment') as HTMLTextAreaElement }
}

describe('MentionComposer', () => {
  it('offers matching people after @ and tells whoever was chosen', async () => {
    const { onSubmit, box } = setup()
    await userEvent.type(box, 'Hi @as')
    expect(screen.queryByRole('button', { name: 'Ben Lee' })).not.toBeInTheDocument()
    await userEvent.click(screen.getByRole('button', { name: 'Asha Rao' }))
    expect(box.value).toBe('Hi @Asha Rao ')
    await userEvent.click(screen.getByRole('button', { name: 'Comment' }))
    expect(onSubmit).toHaveBeenCalledWith('Hi @Asha Rao ', ['u-1'])
    await waitFor(() => expect(box.value).toBe(''))
  })

  it('does not tell someone whose name was removed before posting', async () => {
    const { onSubmit, box } = setup()
    await userEvent.type(box, '@as')
    await userEvent.click(screen.getByRole('button', { name: 'Asha Rao' }))
    await userEvent.clear(box)
    await userEvent.type(box, 'Never mind')
    await userEvent.click(screen.getByRole('button', { name: 'Comment' }))
    expect(onSubmit).toHaveBeenCalledWith('Never mind', [])
  })

  it('keeps the text and shows the error when posting fails', async () => {
    const onSubmit = vi.fn(() => Promise.reject(new Error('nope')))
    render(<MentionComposer id="c" label="Comment" people={PEOPLE} onSubmit={onSubmit} />)
    const box = screen.getByLabelText('Comment') as HTMLTextAreaElement
    await userEvent.type(box, 'Hello')
    await userEvent.click(screen.getByRole('button', { name: 'Comment' }))
    expect(await screen.findByText(/something went wrong/i)).toBeInTheDocument()
    expect(box.value).toBe('Hello')
  })
})
