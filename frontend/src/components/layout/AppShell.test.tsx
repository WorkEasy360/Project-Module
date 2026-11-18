import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { render, screen, fireEvent } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { MemoryRouter } from 'react-router-dom'
import { SidebarProvider } from '../../context/SidebarContext'
import { setCollapsed } from '../../context/sidebarStore'
import { SessionProvider } from '../../context/SessionContext'
import { IdentityProvider } from '../../context/IdentityContext'
import { setIdentity } from '../../context/identityStore'
import { resetSessionForTest, signInAs } from '../../test/session'
import { Sidebar } from './Sidebar'
import { Header } from './Header'

/** Stubs window.matchMedia so `useMediaQuery('(max-width: 768px)')` resolves as desired. */
function mockViewport(isMobile: boolean) {
  window.matchMedia = ((query: string) => ({
    matches: query.includes('768px') ? isMobile : false,
    media: query,
    onchange: null,
    addListener: () => {},
    removeListener: () => {},
    addEventListener: () => {},
    removeEventListener: () => {},
    dispatchEvent: () => false,
  })) as typeof window.matchMedia
}

function renderShell() {
  return render(
    <MemoryRouter initialEntries={['/dashboard']}>
      <SessionProvider autoLoad={false}>
        <IdentityProvider>
          <SidebarProvider>
            <Header />
            <Sidebar />
          </SidebarProvider>
        </IdentityProvider>
      </SessionProvider>
    </MemoryRouter>,
  )
}

describe('AppShell — hamburger / sidebar navigation', () => {
  beforeEach(() => {
    setCollapsed(false)
    signInAs('11111111-2222-4333-8444-555555555555', { name: 'Ada Lovelace' })
  })

  afterEach(() => {
    setCollapsed(false)
    setIdentity(null)
    resetSessionForTest()
    vi.restoreAllMocks()
  })

  it('the account control follows the sign-in: Workeasy360 offers its two sign-outs, an email account its own', async () => {
    mockViewport(false)
    const user = userEvent.setup()
    const first = renderShell()
    await user.click(screen.getByRole('button', { name: /ada lovelace/i }))
    expect(screen.getByRole('button', { name: 'Sign out of Easy Project' })).toBeInTheDocument()
    first.unmount()

    setIdentity({ userId: 'email-user', orgId: 'org-1', token: 'tok', name: 'Ben Email', email: 'ben@example.com' })
    renderShell()
    await user.click(screen.getByRole('button', { name: /ben email/i }))
    expect(screen.getByText('ben@example.com')).toBeInTheDocument()
    expect(screen.getByRole('button', { name: /^sign out$/i })).toBeInTheDocument()
    expect(screen.queryByRole('button', { name: 'Sign out of Easy Project' })).not.toBeInTheDocument()
  })

  it('desktop: clicking the hamburger collapses the sidebar and persists the preference', () => {
    mockViewport(false)
    const { container } = renderShell()

    const sidebar = container.querySelector('.sidebar')
    expect(sidebar).not.toHaveClass('collapsed')

    const hamburger = screen.getByRole('button', { name: /collapse sidebar/i })
    fireEvent.click(hamburger)

    expect(sidebar).toHaveClass('collapsed')
    expect(window.localStorage.getItem('projectmodule.sidebarCollapsed')).toBe('1')

    // Clicking again expands it back, and the label flips to match.
    const expandButton = screen.getByRole('button', { name: /expand sidebar/i })
    fireEvent.click(expandButton)
    expect(sidebar).not.toHaveClass('collapsed')
    expect(window.localStorage.getItem('projectmodule.sidebarCollapsed')).toBe('0')
  })

  it('mobile: clicking the hamburger opens the off-canvas drawer with a backdrop', () => {
    mockViewport(true)
    const { container } = renderShell()

    const sidebar = container.querySelector('.sidebar')
    expect(sidebar).not.toHaveClass('open')
    expect(container.querySelector('.sidebar-backdrop')).not.toBeInTheDocument()

    const hamburger = screen.getByRole('button', { name: /open navigation/i })
    fireEvent.click(hamburger)

    expect(sidebar).toHaveClass('open')
    expect(container.querySelector('.sidebar-backdrop')).toBeInTheDocument()
  })

  it('mobile: clicking the backdrop closes the drawer', () => {
    mockViewport(true)
    const { container } = renderShell()

    fireEvent.click(screen.getByRole('button', { name: /open navigation/i }))
    expect(container.querySelector('.sidebar')).toHaveClass('open')

    fireEvent.click(container.querySelector('.sidebar-backdrop')!)
    expect(container.querySelector('.sidebar')).not.toHaveClass('open')
    expect(container.querySelector('.sidebar-backdrop')).not.toBeInTheDocument()
  })

  it('mobile: pressing Escape closes the drawer', () => {
    mockViewport(true)
    const { container } = renderShell()

    fireEvent.click(screen.getByRole('button', { name: /open navigation/i }))
    expect(container.querySelector('.sidebar')).toHaveClass('open')

    fireEvent.keyDown(document, { key: 'Escape' })
    expect(container.querySelector('.sidebar')).not.toHaveClass('open')
  })

  it('mobile: clicking a navigation link closes the drawer', async () => {
    mockViewport(true)
    const { container } = renderShell()
    const user = userEvent.setup()

    fireEvent.click(screen.getByRole('button', { name: /open navigation/i }))
    expect(container.querySelector('.sidebar')).toHaveClass('open')

    await user.click(screen.getByRole('link', { name: /projects/i }))
    expect(container.querySelector('.sidebar')).not.toHaveClass('open')
  })

  it('highlights the active route in the sidebar', () => {
    mockViewport(false)
    renderShell()
    expect(screen.getByRole('link', { name: /^home$/i })).toHaveClass('active')
    expect(screen.getByRole('link', { name: /projects/i })).not.toHaveClass('active')
  })

  it('links Templates directly from the workspace sidebar with the correct active state', () => {
    mockViewport(false)
    render(
      <MemoryRouter initialEntries={['/templates']}>
        <IdentityProvider><SidebarProvider>
          <Sidebar />
        </SidebarProvider></IdentityProvider>
      </MemoryRouter>,
    )
    const templates = screen.getByRole('link', { name: /^templates$/i })
    expect(templates).toHaveAttribute('href', '/templates')
    expect(templates).toHaveClass('active')
    // No workspace-level "More" page exists any more.
    expect(screen.queryByRole('link', { name: /^more$/i })).not.toBeInTheDocument()
  })

  it('groups the workspace into short sections', () => {
    mockViewport(false)
    render(
      <MemoryRouter initialEntries={['/dashboard']}>
        <IdentityProvider><SidebarProvider>
          <Sidebar />
        </SidebarProvider></IdentityProvider>
      </MemoryRouter>,
    )
    expect(screen.getByText('Work')).toBeInTheDocument()
    expect(screen.getByRole('link', { name: 'Timesheet' })).toHaveAttribute('href', '/timesheet')
  })

  it('never repeats the project tabs in the sidebar', () => {
    mockViewport(false)
    render(
      <MemoryRouter initialEntries={['/dashboard']}>
        <IdentityProvider><SidebarProvider>
          <Sidebar />
        </SidebarProvider></IdentityProvider>
      </MemoryRouter>,
    )
    expect(screen.queryByRole('link', { name: /^tasks$/i })).not.toBeInTheDocument()
  })

  it('keeps project tabs off the sidebar even inside a project', () => {
    mockViewport(false)
    render(
      <MemoryRouter initialEntries={['/projects/proj-123/overview']}>
        <IdentityProvider><SidebarProvider>
          <Sidebar />
        </SidebarProvider></IdentityProvider>
      </MemoryRouter>,
    )
    expect(screen.queryByRole('link', { name: /^tasks$/i })).not.toBeInTheDocument()
    expect(screen.getByRole('link', { name: 'Projects' })).toBeInTheDocument()
  })
})
