import { useCallback, useEffect, useRef, useState } from 'react'
import { useLocation, useNavigate } from 'react-router-dom'
import { notificationsApi, type AppNotification } from '../../api/collaboration'
import { Icon } from './Icon'
import { formatDateTime } from '../../utils/format'

const POLL_MS = 60_000

/** The header bell: an unread count, kept fresh, opening the latest notifications. */
export function NotificationBell() {
  const navigate = useNavigate()
  const location = useLocation()
  const [unread, setUnread] = useState(0)
  const [open, setOpen] = useState(false)
  const [items, setItems] = useState<AppNotification[] | null>(null)
  const panelRef = useRef<HTMLDivElement>(null)

  const refreshCount = useCallback(() => {
    notificationsApi.unreadCount().then((result) => setUnread(result.count)).catch(() => undefined)
  }, [])

  useEffect(() => {
    refreshCount()
    const timer = window.setInterval(refreshCount, POLL_MS)
    return () => window.clearInterval(timer)
  }, [refreshCount])

  // Moving to another page closes the panel.
  useEffect(() => setOpen(false), [location.pathname])

  useEffect(() => {
    if (!open) return
    function onPointerDown(event: MouseEvent) {
      if (panelRef.current && !panelRef.current.contains(event.target as Node)) setOpen(false)
    }
    document.addEventListener('mousedown', onPointerDown)
    return () => document.removeEventListener('mousedown', onPointerDown)
  }, [open])

  function toggle() {
    if (!open) {
      setItems(null)
      notificationsApi.list().then((page) => setItems(page.content)).catch(() => setItems([]))
      refreshCount()
    }
    setOpen(!open)
  }

  async function openItem(item: AppNotification) {
    setOpen(false)
    if (!item.read) {
      await notificationsApi.markRead(item.id).catch(() => undefined)
      refreshCount()
    }
    if (item.projectId && item.taskId) navigate(`/projects/${item.projectId}/tasks/${item.taskId}`)
    else if (item.projectId) navigate(`/projects/${item.projectId}/comments`)
  }

  async function markAll() {
    await notificationsApi.markAllRead().catch(() => undefined)
    setUnread(0)
    setItems((current) => current?.map((item) => ({ ...item, read: true })) ?? null)
  }

  return (
    <div className="notif" ref={panelRef}>
      <button
        type="button"
        className="icon-btn notif-trigger"
        aria-label={unread > 0 ? `Notifications, ${unread} unread` : 'Notifications'}
        aria-expanded={open}
        onClick={toggle}
      >
        <Icon name="bell" />
        {unread > 0 && <span className="notif-count">{unread > 99 ? '99+' : unread}</span>}
      </button>
      {open && (
        <div className="notif-panel" role="dialog" aria-label="Notifications">
          <div className="notif-head">
            <strong>Notifications</strong>
            {unread > 0 && (
              <button type="button" className="btn-link" onClick={markAll}>
                Mark all as read
              </button>
            )}
          </div>
          {items === null && <p className="notif-empty">Loading…</p>}
          {items?.length === 0 && <p className="notif-empty">You're all caught up.</p>}
          {items && items.length > 0 && (
            <ul className="notif-list">
              {items.map((item) => (
                <li key={item.id}>
                  <button type="button" className={`notif-item${item.read ? '' : ' unread'}`} onClick={() => openItem(item)}>
                    <span className="notif-title">{item.title}</span>
                    <span className="notif-time">{formatDateTime(item.createdAt)}</span>
                  </button>
                </li>
              ))}
            </ul>
          )}
        </div>
      )}
    </div>
  )
}
