import { afterEach, describe, expect, it } from 'vitest'
import { render, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { IdentityProvider } from '../../context/IdentityContext'
import { IdentityWidget } from './IdentityWidget'
import { setIdentity } from '../../context/identityStore'

describe('IdentityWidget', () => {
  afterEach(() => {
    setIdentity(null)
  })

  it('prompts to set identity when none is configured', () => {
    render(
      <IdentityProvider>
        <IdentityWidget />
      </IdentityProvider>,
    )
    expect(screen.getByRole('button', { name: /set identity/i })).toBeInTheDocument()
  })

  it('rejects non-UUID input and does not save it', async () => {
    render(
      <IdentityProvider>
        <IdentityWidget />
      </IdentityProvider>,
    )
    await userEvent.click(screen.getByRole('button', { name: /set identity/i }))
    await userEvent.type(screen.getByLabelText(/user id/i), 'not-a-uuid')
    await userEvent.type(screen.getByLabelText(/organization id/i), 'also-not-a-uuid')
    await userEvent.click(screen.getByRole('button', { name: /^save$/i }))

    expect(await screen.findByText(/must be valid UUIDs/i)).toBeInTheDocument()
    // Dialog stays open and the trigger still shows "Set identity" — nothing was saved.
    expect(screen.getByRole('button', { name: /set identity/i })).toBeInTheDocument()
  })

  it('accepts valid UUIDs and shows the shortened identity afterward', async () => {
    render(
      <IdentityProvider>
        <IdentityWidget />
      </IdentityProvider>,
    )
    await userEvent.click(screen.getByRole('button', { name: /set identity/i }))
    await userEvent.type(screen.getByLabelText(/user id/i), '11111111-1111-1111-1111-111111111111')
    await userEvent.type(screen.getByLabelText(/organization id/i), '22222222-2222-2222-2222-222222222222')
    await userEvent.click(screen.getByRole('button', { name: /^save$/i }))

    expect(await screen.findByText(/11111111/)).toBeInTheDocument()
  })
})
