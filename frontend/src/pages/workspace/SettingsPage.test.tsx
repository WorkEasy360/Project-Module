import { describe, expect, it, vi, beforeEach, afterEach } from 'vitest'
import { render, screen, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { MemoryRouter } from 'react-router-dom'
import { SettingsPage } from './SettingsPage'
import { IdentityProvider, useIdentity } from '../../context/IdentityContext'
import { SidebarProvider } from '../../context/SidebarContext'
import { getIdentity, setIdentity } from '../../context/identityStore'
import { getThemePreference, setThemePreference } from '../../context/themeStore'
import { getCollapsed, setCollapsed } from '../../context/sidebarStore'

const ME = '11111111-2222-4333-8444-555555555555'
const ORG = '99999999-2222-4333-8444-555555555555'

/** The identity dialog itself lives in the header; here we only observe that the page asked for it. */
function PromptProbe() {
  const { isPromptOpen } = useIdentity()
  return <output data-testid="prompt-open">{String(isPromptOpen)}</output>
}

function renderSettings() {
  return render(
    <MemoryRouter>
      <IdentityProvider>
        <SidebarProvider>
          <SettingsPage />
          <PromptProbe />
        </SidebarProvider>
      </IdentityProvider>
    </MemoryRouter>,
  )
}

describe('SettingsPage', () => {
  beforeEach(() => {
    setIdentity({ userId: ME, orgId: ORG })
    setThemePreference('system')
    setCollapsed(false)
  })
  afterEach(() => {
    setThemePreference('system')
    setCollapsed(false)
  })

  it('Identity shows the real ids with copy buttons and explains the headers; no invented profile fields', async () => {
    const writeText = vi.fn(() => Promise.resolve())
    Object.defineProperty(navigator, 'clipboard', { value: { writeText }, configurable: true })
    renderSettings()
    const section = screen.getByRole('region', { name: 'Identity' })
    expect(within(section).getByText(ME)).toBeInTheDocument()
    expect(within(section).getByText(ORG)).toBeInTheDocument()
    expect(section).toHaveTextContent('X-User-Id')
    expect(section).toHaveTextContent('X-Org-Id')
    expect(section).toHaveTextContent('stored only in this browser')
    await userEvent.click(within(section).getByRole('button', { name: 'Copy user ID' }))
    expect(writeText).toHaveBeenCalledWith(ME)
    expect(await within(section).findByText('Copied')).toBeInTheDocument()
    // No fake profile or notification preferences: the backend has no such fields.
    expect(screen.queryByLabelText(/email|name|notification/i)).not.toBeInTheDocument()
    expect(screen.queryByRole('checkbox', { name: /notification/i })).not.toBeInTheDocument()
  })

  it('Change identity opens the shared identity prompt; Sign out clears the stored identity', async () => {
    renderSettings()
    const section = screen.getByRole('region', { name: 'Identity' })
    expect(screen.getByTestId('prompt-open')).toHaveTextContent('false')
    await userEvent.click(within(section).getByRole('button', { name: 'Change identity' }))
    expect(screen.getByTestId('prompt-open')).toHaveTextContent('true')
    await userEvent.click(within(section).getByRole('button', { name: /Sign out/ }))
    expect(getIdentity()).toBeNull()
    expect(within(section).getAllByText('Not set')).toHaveLength(2)
    expect(within(section).getByRole('button', { name: 'Set identity' })).toBeInTheDocument()
  })

  it('Appearance is a System / Light / Dark radio group wired to the theme store', async () => {
    renderSettings()
    const section = screen.getByRole('region', { name: 'Appearance' })
    expect(within(section).getByRole('radio', { name: 'System' })).toBeChecked()
    await userEvent.click(within(section).getByRole('radio', { name: 'Dark' }))
    expect(getThemePreference()).toBe('dark')
    expect(document.documentElement.getAttribute('data-theme')).toBe('dark')
    expect(within(section).getByRole('radio', { name: 'Dark' })).toBeChecked()
    expect(section).toHaveTextContent('Currently showing the dark theme')
    await userEvent.click(within(section).getByRole('radio', { name: 'Light' }))
    expect(getThemePreference()).toBe('light')
    expect(document.documentElement.getAttribute('data-theme')).toBe('light')
  })

  it('Navigation toggles the persisted sidebar collapse', async () => {
    renderSettings()
    const box = within(screen.getByRole('region', { name: 'Navigation' })).getByRole('checkbox')
    expect(box).not.toBeChecked()
    await userEvent.click(box)
    expect(getCollapsed()).toBe(true)
    expect(box).toBeChecked()
    await userEvent.click(box)
    expect(getCollapsed()).toBe(false)
  })

  it('Features lists the registry read-only with release badges; About shows the API base URL', () => {
    renderSettings()
    const features = screen.getByRole('region', { name: 'Features' })
    expect(within(features).getByRole('link', { name: 'My Team' })).toHaveAttribute('href', '/team')
    expect(within(features).getByRole('link', { name: 'Reports' })).toHaveAttribute('href', '/reports')
    expect(within(features).getByRole('heading', { name: 'Workspace · Daily work' })).toBeInTheDocument()
    expect(within(features).getByRole('heading', { name: 'In each project · Project control' })).toBeInTheDocument()
    // Project-scoped features need a project id, so they are not links here.
    expect(within(features).queryByRole('link', { name: 'Activity' })).not.toBeInTheDocument()
    const activity = within(features).getByText('Activity').closest('li')!
    expect(activity).toHaveTextContent('Coming Soon')
    const ai = within(features).getByText('AI Suggestions').closest('li')!
    expect(ai).toHaveTextContent('Beta')
    expect(within(features).getAllByText('Available').length).toBeGreaterThan(10)

    const about = screen.getByRole('region', { name: 'About' })
    expect(about).toHaveTextContent('http://localhost:8080')
  })
})
