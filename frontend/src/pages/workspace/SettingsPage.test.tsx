import { describe, expect, it, vi, beforeEach, afterEach } from 'vitest'
import { render, screen, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { MemoryRouter } from 'react-router-dom'
import { SettingsPage } from './SettingsPage'
import { IdentityProvider } from '../../context/IdentityContext'
import { SidebarProvider } from '../../context/SidebarContext'
import { getIdentity, setIdentity } from '../../context/identityStore'
import { getThemePreference, setThemePreference } from '../../context/themeStore'
import { getCollapsed, setCollapsed } from '../../context/sidebarStore'

const ME = '11111111-2222-4333-8444-555555555555'
const ORG = '99999999-2222-4333-8444-555555555555'

vi.mock('../../api/auth', () => ({ authApi: { logout: vi.fn(() => Promise.resolve()) } }))

function renderSettings() {
  return render(
    <MemoryRouter>
      <IdentityProvider>
        <SidebarProvider>
          <SettingsPage />
        </SidebarProvider>
      </IdentityProvider>
    </MemoryRouter>,
  )
}

describe('SettingsPage', () => {
  beforeEach(() => {
    setIdentity({ userId: ME, orgId: ORG, token: 'tok', name: 'Ada', email: 'ada@example.com' })
    setThemePreference('system')
    setCollapsed(false)
  })
  afterEach(() => {
    setThemePreference('system')
    setCollapsed(false)
  })

  it('Account shows the signed-in name and email, not internal ids', () => {
    renderSettings()
    const section = screen.getByRole('region', { name: 'Account' })
    expect(within(section).getByText('Ada')).toBeInTheDocument()
    expect(within(section).getByText('ada@example.com')).toBeInTheDocument()
    expect(within(section).queryByText(ME)).not.toBeInTheDocument()
    expect(screen.queryByRole('checkbox', { name: /notification/i })).not.toBeInTheDocument()
  })

  it('Sign out ends the session, and the ids can no longer be edited by hand', async () => {
    renderSettings()
    const section = screen.getByRole('region', { name: 'Account' })
    expect(within(section).queryByRole('button', { name: /change identity/i })).not.toBeInTheDocument()
    await userEvent.click(within(section).getByRole('button', { name: /Sign out/ }))
    expect(getIdentity()).toBeNull()
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
    expect(activity).toHaveTextContent('Available')
    const ai = within(features).getByText('AI Suggestions').closest('li')!
    expect(ai).toHaveTextContent('Beta')
    expect(within(features).getAllByText('Available').length).toBeGreaterThan(10)

    const about = screen.getByRole('region', { name: 'About' })
    expect(about).toHaveTextContent('http://localhost:8080')
  })
})
