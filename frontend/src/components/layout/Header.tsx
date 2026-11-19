import { useEffect, useRef, useState } from 'react'
import { Link, useMatch, useNavigate } from 'react-router-dom'
import { IdentityWidget } from '../common/IdentityWidget'
import { AccountMenu } from '../common/AccountMenu'
import { NotificationBell } from '../common/NotificationBell'
import { openAssistant } from '../assistant/AssistantPanel'
import { useIdentity } from '../../context/IdentityContext'
import { IconButton } from '../common/IconButton'
import { Icon } from '../common/Icon'
import { useSidebar } from '../../context/SidebarContext'
import { useWorkspaceSettings } from '../../context/WorkspaceSettingsContext'

/**
 * Top bar: the workspace picker on the left; search, the AI Assistant, notifications, help and the
 * signed-in person on the right. Search covers projects and tasks everywhere, or the open project;
 * Ctrl/Cmd+K opens it, and "/" inside a project jumps to that project's search.
 */
export function Header() {
  const { identity } = useIdentity()
  const workeasy360 = identity?.provider === 'workeasy360'
  const { toggle, isMobile } = useSidebar()
  const navigate = useNavigate()
  const projectMatch = useMatch('/projects/:projectId/*')
  const projectId = projectMatch?.params.projectId !== 'archived' ? projectMatch?.params.projectId : undefined
  const [searchOpen, setSearchOpen] = useState(false)
  const [query, setQuery] = useState('')
  const searchRef = useRef<HTMLInputElement>(null)

  useEffect(() => {
    function onKeyDown(event: KeyboardEvent) {
      const target = event.target as HTMLElement | null
      const typing = target && ['INPUT', 'TEXTAREA', 'SELECT'].includes(target.tagName)
      if ((event.ctrlKey || event.metaKey) && event.key.toLowerCase() === 'k') {
        event.preventDefault()
        setSearchOpen(true)
        return
      }
      if (event.key === 'Escape') setSearchOpen(false)
      if (projectId && event.key === '/' && !typing && !event.metaKey && !event.ctrlKey) {
        event.preventDefault()
        navigate(`/projects/${projectId}/search`)
      }
    }
    document.addEventListener('keydown', onKeyDown)
    return () => document.removeEventListener('keydown', onKeyDown)
  }, [projectId, navigate])

  useEffect(() => {
    if (searchOpen) searchRef.current?.focus()
  }, [searchOpen])

  function submitSearch(event: React.FormEvent) {
    event.preventDefault()
    const q = query.trim()
    setSearchOpen(false)
    setQuery('')
    navigate(projectId ? `/projects/${projectId}/search${q ? `?q=${encodeURIComponent(q)}` : ''}` : `/search${q ? `?q=${encodeURIComponent(q)}` : ''}`)
  }

  return (
    <header className="app-header">
      {isMobile && <IconButton icon="menu" label="Open navigation" onClick={toggle} />}
      <WorkspacePicker />

      <div className="app-header-spacer" />

      <div className="header-search-wrap">
        <IconButton icon="search" label="Search" size={22} onClick={() => setSearchOpen((v) => !v)} active={searchOpen} />
        {searchOpen && (
          <form className="header-search-pop" role="search" onSubmit={submitSearch}>
            <Icon name="search" size={20} />
            <input
              ref={searchRef}
              type="search"
              value={query}
              onChange={(e) => setQuery(e.target.value)}
              placeholder={projectId ? 'Search this project…' : 'Search projects and tasks…'}
              aria-label={projectId ? 'Search this project' : 'Search projects and tasks'}
            />
            <kbd aria-hidden="true">Enter</kbd>
          </form>
        )}
      </div>
      <button type="button" className="ai-assistant-btn" onClick={() => openAssistant()}>
        <Icon name="sparkles" size={20} />
        <span>AI Assistant</span>
      </button>
      <NotificationBell />
      <Link to="/helpdesk" className="icon-btn header-help" aria-label="Help and support" title="Help and support">
        <Icon name="help" size={22} />
      </Link>
      <span className="header-divider" aria-hidden="true" />
      {/* A Workeasy360 sign-in has its own menu: two ways to sign out, and no password of ours. */}
      {workeasy360 ? <AccountMenu /> : <IdentityWidget />}
    </header>
  )
}

/** The workspace (company) the person is working in. One workspace per account, so it lists just that one. */
function WorkspacePicker() {
  const { settings } = useWorkspaceSettings()
  const [open, setOpen] = useState(false)
  const ref = useRef<HTMLDivElement>(null)

  useEffect(() => {
    if (!open) return
    function onClick(event: MouseEvent) {
      if (ref.current && !ref.current.contains(event.target as Node)) setOpen(false)
    }
    document.addEventListener('mousedown', onClick)
    return () => document.removeEventListener('mousedown', onClick)
  }, [open])

  const name = settings?.name ?? '…'
  return (
    <div className="workspace-picker" ref={ref}>
      <button type="button" className="workspace-picker-btn" onClick={() => setOpen((v) => !v)} aria-expanded={open} aria-haspopup="menu">
        <Icon name="building" size={22} />
        <span className="workspace-picker-name">{name}</span>
        <Icon name="chevronDown" size={18} />
      </button>
      {open && (
        <div className="workspace-picker-menu" role="menu">
          <div className="workspace-picker-current" role="menuitem">
            <Icon name="building" size={18} />
            <span>{name}</span>
            <Icon name="check" size={16} />
          </div>
          <Link to="/settings" role="menuitem" className="workspace-picker-link" onClick={() => setOpen(false)}>
            <Icon name="settings" size={16} /> {settings?.canEdit ? 'Workspace settings' : 'Settings'}
          </Link>
          <Link to="/team" role="menuitem" className="workspace-picker-link" onClick={() => setOpen(false)}>
            <Icon name="team" size={16} /> People in this workspace
          </Link>
        </div>
      )}
    </div>
  )
}
