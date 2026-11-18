import { useMemo, useState } from 'react'
import { Link } from 'react-router-dom'
import '../../styles/ops.css'
import '../../styles/ops-sections.css'
import { Icon } from '../../components/common/Icon'
import { openAssistant } from '../../components/assistant/AssistantPanel'
import { useWorkspaceSettings } from '../../context/WorkspaceSettingsContext'
import { useIdentity } from '../../context/IdentityContext'
import { useAllProjects } from '../../hooks/useWorkspaceData'

export type HelpdeskSection = 'raise' | 'tickets' | 'articles'

interface Answer {
  q: string
  a: React.ReactNode
  keywords: string
}

/** Help articles for the V3 workflow: invite, team, project, task, assign, due date, track, complete. */
export const ANSWERS: Answer[] = [
  {
    q: 'How do I invite members?',
    keywords: 'invite team member people join',
    a: <>Workspace admins open <Link to="/team">Team → Members</Link> and click <b>Invite Member</b>. The person gets an email with a link that works once and expires in 7 days. Waiting invitations are under <Link to="/team/invitations">Team → Invitations</Link>, where you can resend or revoke them.</>,
  },
  {
    q: 'How do I create a project?',
    keywords: 'new project create start owner customer',
    a: <>Open <Link to="/projects">Projects</Link> and click <b>Create Project</b>. Only the name and owner are needed; description, start and due dates, customer/contact and tags are optional. You are the owner unless you choose someone else.</>,
  },
  {
    q: 'How do I add people to a project?',
    keywords: 'project members access add remove',
    a: <>Open the project and go to its <b>Members</b> tab. Add people who are already in your workspace and choose what they can do on the project.</>,
  },
  {
    q: 'How do I add a task and assign it?',
    keywords: 'task add create assign assignee responsible person',
    a: <>Use <b>Add Task</b> on <Link to="/dashboard">Home</Link>, or open a project and add one on its Tasks tab. Only the title is required. Choose the assignee from the project&apos;s members; each task has one responsible person, and they are notified when it is assigned to them.</>,
  },
  {
    q: 'How do due dates and overdue work?',
    keywords: 'due date overdue today upcoming late',
    a: <>Give a task a due date when you create or edit it. A task is overdue once its due date has passed and it is not Done; it stays due until the end of that day. <Link to="/tasks">My Tasks</Link> shows Today, Upcoming, Overdue and Completed.</>,
  },
  {
    q: 'How do I track progress on the board?',
    keywords: 'board kanban list status drag progress',
    a: <>In a project, open <b>Tasks</b> and switch between <b>List</b> and <b>Board</b>. Drag a card to another column to change its status: To Do, In Progress, Blocked or Done. Project progress is worked out from completed tasks.</>,
  },
  {
    q: 'How do I archive a project?',
    keywords: 'archive archived complete close',
    a: <>The project owner can archive it from the project&apos;s Overview. Its tasks and history are kept, read only, under <Link to="/projects/archived">Projects → Archived Projects</Link>.</>,
  },
  {
    q: 'How do automation rules work?',
    keywords: 'automation rule notify trigger reminder',
    a: <>Open <Link to="/automations">Automation → Rules</Link> and click <b>New Rule</b>: choose the project, what starts it (for example, a task is completed) and who to notify. Every run is listed in <Link to="/automations/runs">Run History</Link>.</>,
  },
  {
    q: 'What can the AI Assistant do?',
    keywords: 'ai assistant chat voice help agent',
    a: <>It explains how to do things, summarises your projects and overdue work, drafts task descriptions and checklists, and can create or change tasks for you. It always shows the change first and saves nothing until you confirm.</>,
  },
  {
    q: 'I forgot my password',
    keywords: 'password reset forgot login sign in',
    a: <>Use <b>Forgot Password?</b> on the login page. Otherwise ask a workspace admin: they can create a reset link for you from <Link to="/team">Team → Members</Link>.</>,
  },
]

const INTRO: Record<HelpdeskSection, { title: string; text: string }> = {
  raise: { title: 'Raise Ticket', text: 'Tell us what went wrong and we will help.' },
  tickets: { title: 'My Tickets', text: 'Your support tickets, their status and replies.' },
  articles: { title: 'Help Articles', text: 'Short answers to the most common questions.' },
}

/** V3 Helpdesk: Raise Ticket, My Tickets and Help Articles. */
export function HelpdeskPage({ section }: { section: HelpdeskSection }) {
  const intro = INTRO[section]
  return (
    <div className="ops-page ops-section-page ops-help">
      <header className="ops-page-head">
        <div>
          <h1>{intro.title}</h1>
          <p>{intro.text}</p>
        </div>
        <div className="ops-head-actions">
          <button type="button" className="ops-ghost-btn" onClick={() => openAssistant()}>
            <Icon name="sparkles" size={20} /> Ask the AI Assistant
          </button>
        </div>
      </header>
      {section === 'raise' && <RaiseTicket />}
      {section === 'tickets' && <TicketsNotLinked />}
      {section === 'articles' && <Articles />}
    </div>
  )
}

/** The support address to write to, with product and workspace filled in. */
function useSupportMail() {
  const { settings } = useWorkspaceSettings()
  const support = settings?.supportEmail ?? 'support@workeasy360.com'
  const workspace = settings?.name ?? ''
  return { support, workspace }
}

export function supportMailto(support: string, fields: { subject: string; description: string; reference: string; workspace: string; email: string }): string {
  const header = [
    'Product: WorkEasy360 Operations',
    `Workspace: ${fields.workspace}`,
    ...(fields.email ? [`From: ${fields.email}`] : []),
    ...(fields.reference ? [`Reference: ${fields.reference}`] : []),
  ]
  const body = `${header.join('\n')}\n\n${fields.description}`
  return `mailto:${support}?subject=${encodeURIComponent(`[Operations] ${fields.subject}`)}&body=${encodeURIComponent(body)}`
}

/**
 * The ticket form. Tickets go to the shared WorkEasy360 Helpdesk, which this workspace is not linked
 * to yet, so nothing is submitted from here and no ticket number is shown; the form opens an email
 * to support with everything filled in instead.
 */
function RaiseTicket() {
  const { identity } = useIdentity()
  const { support, workspace } = useSupportMail()
  const projects = useAllProjects()
  const [subject, setSubject] = useState('')
  const [description, setDescription] = useState('')
  const [reference, setReference] = useState('')
  const [fileName, setFileName] = useState('')
  const href = supportMailto(support, { subject, description, reference, workspace, email: identity?.email ?? '' })
  const ready = subject.trim() !== '' && description.trim() !== ''

  return (
    <div className="ops-grid ops-grid-2 ops-help-grid">
      <section className="ops-card" aria-labelledby="ticket-form">
        <header className="ops-card-head">
          <h2 id="ticket-form">
            <Icon name="headset" size={22} /> New ticket
          </h2>
        </header>
        <form className="ops-form" onSubmit={(e) => e.preventDefault()}>
          <label className="ops-field">
            <span>Subject *</span>
            <input type="text" value={subject} onChange={(e) => setSubject(e.target.value)} maxLength={200} placeholder="e.g. I cannot assign a task" />
          </label>
          <label className="ops-field">
            <span>What happened? *</span>
            <textarea value={description} onChange={(e) => setDescription(e.target.value)} rows={5} placeholder="What you were doing, what you expected and what you saw." />
          </label>
          <div className="ops-form-row">
            <label className="ops-field">
              <span>Related project (optional)</span>
              <select value={reference} onChange={(e) => setReference(e.target.value)}>
                <option value="">None</option>
                {(projects.data?.content ?? []).map((p) => (
                  <option key={p.id} value={`Project "${p.name}"`}>
                    {p.name}
                  </option>
                ))}
              </select>
            </label>
            <label className="ops-field">
              <span>Screenshot (optional)</span>
              <input type="file" accept="image/*,.pdf" onChange={(e) => setFileName(e.target.files?.[0]?.name ?? '')} />
            </label>
          </div>
          <p className="ops-field-note">Only the project&apos;s name is mentioned; no project data is attached.</p>
          <div className="ops-form-actions">
            {ready ? (
              <a className="ops-primary" href={href}>
                <Icon name="mail" size={20} /> Email this to support
              </a>
            ) : (
              <button type="button" className="ops-primary disabled" disabled title="Add a subject and a description first">
                <Icon name="mail" size={20} /> Email this to support
              </button>
            )}
          </div>
          {fileName && <p className="ops-field-note">Attach {fileName} to the email before you send it.</p>}
        </form>
      </section>

      <section className="ops-card ops-notice-card" aria-labelledby="ticket-status">
        <span className="ops-soon-badge">Helpdesk not linked yet</span>
        <h2 id="ticket-status">How your request reaches us</h2>
        <p>
          Tickets are handled by the shared WorkEasy360 Helpdesk. This workspace is not linked to it yet, so tickets cannot be
          created here and there is no ticket number to show.
        </p>
        <p>
          Until it is linked, <b>Email this to support</b> opens an email to <b>{support}</b> with the product (Operations), your
          workspace and your message filled in. We usually reply within one working day.
        </p>
        <p className="ops-muted">Once linked, you will get a Ticket ID here, with its status, replies and notifications.</p>
      </section>
    </div>
  )
}

function TicketsNotLinked() {
  const { support } = useSupportMail()
  return (
    <section className="ops-card ops-soon-card" aria-labelledby="tickets-empty">
      <span className="ops-tile-icon tone-blue">
        <Icon name="inbox" size={34} />
      </span>
      <div>
        <span className="ops-soon-badge">Helpdesk not linked yet</span>
        <h2 id="tickets-empty">No tickets to show</h2>
        <p>
          Your tickets, with their Ticket ID, status and replies, appear here once this workspace is linked to the shared
          WorkEasy360 Helpdesk.
        </p>
        <p className="ops-muted">
          Until then, requests go by email to {support}, and replies come back to your inbox.{' '}
          <Link to="/helpdesk">Raise a ticket</Link>.
        </p>
      </div>
    </section>
  )
}

function Articles() {
  const { support } = useSupportMail()
  const [query, setQuery] = useState('')
  const [open, setOpen] = useState<number | null>(0)
  const visible = useMemo(() => {
    const q = query.trim().toLowerCase()
    return ANSWERS.map((a, i) => ({ ...a, i })).filter((a) => !q || `${a.q} ${a.keywords}`.toLowerCase().includes(q))
  }, [query])

  return (
    <section className="ops-card">
      <label className="ops-search ops-search-wide">
        <Icon name="search" size={22} />
        <input type="search" placeholder="Search help articles..." aria-label="Search help articles" value={query} onChange={(e) => setQuery(e.target.value)} />
      </label>
      {visible.length === 0 && <p className="ops-empty">No articles match. Ask the AI Assistant, or email {support}.</p>}
      <ul className="ops-faq">
        {visible.map((a) => (
          <li key={a.i} className={open === a.i ? 'open' : ''}>
            <button type="button" aria-expanded={open === a.i} onClick={() => setOpen(open === a.i ? null : a.i)}>
              {a.q}
              <Icon name="chevronDown" size={18} />
            </button>
            {open === a.i && <div className="ops-faq-answer">{a.a}</div>}
          </li>
        ))}
      </ul>
    </section>
  )
}
