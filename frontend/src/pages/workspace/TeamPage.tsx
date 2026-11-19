import { useMemo, useState } from 'react'
import { Link, useLocation } from 'react-router-dom'
import '../../styles/ops.css'
import '../../styles/ops-sections.css'
import { passwordResetLink, workspaceApi, type Invitation } from '../../api/workspace'
import { toUserMessage } from '../../api/errorMessage'
import { useIdentity } from '../../context/IdentityContext'
import { usePeople } from '../../context/PeopleContext'
import { useAsyncData } from '../../hooks/useAsyncData'
import { Avatar } from '../../components/common/Avatar'
import { ConfirmDialog } from '../../components/common/ConfirmDialog'
import { InlineError } from '../../components/common/ErrorState'
import { Icon, type IconName } from '../../components/common/Icon'
import { Modal } from '../../components/common/Modal'
import { FormActions } from '../../components/forms/fields'
import { formatDate } from '../../utils/format'
import { CopyableLink, InviteDialog } from './team/InviteDialog'

export type TeamSection = 'members' | 'teams' | 'invitations'

const INTRO: Record<TeamSection, { title: string; text: string }> = {
  members: { title: 'Members', text: 'Everyone in your workspace, their role and whether they have joined.' },
  teams: { title: 'Teams', text: 'Group members into teams such as Sales, Support or Development.' },
  invitations: { title: 'Invitations', text: 'Invitations waiting to be accepted. Send one again or withdraw it.' },
}

/** V3 Team: Members, Teams and Invitations, one page per sidebar link. */
export function TeamPage({ section }: { section: TeamSection }) {
  const { isAdmin } = usePeople()
  const location = useLocation()
  // Home's "Invite Member" opens Team with the invite form already open.
  const [inviteOpen, setInviteOpen] = useState(() => Boolean((location.state as { invite?: boolean } | null)?.invite))
  const [refreshKey, setRefreshKey] = useState(0)
  const intro = INTRO[section]

  return (
    <div className="ops-page ops-section-page">
      <header className="ops-page-head">
        <div>
          <h1>{intro.title}</h1>
          <p>{intro.text}</p>
        </div>
        {isAdmin && section !== 'teams' && (
          <div className="ops-head-actions">
            <button type="button" className="ops-primary" onClick={() => setInviteOpen(true)}>
              <Icon name="userPlus" size={22} /> Invite Member
            </button>
          </div>
        )}
      </header>

      {section === 'members' && <MembersView refreshKey={refreshKey} />}
      {section === 'teams' && <TeamsComingSoon />}
      {section === 'invitations' && <InvitationsView refreshKey={refreshKey} onChanged={() => setRefreshKey((k) => k + 1)} />}

      {inviteOpen && isAdmin && (
        <InviteDialog onClose={() => setInviteOpen(false)} onInvited={() => setRefreshKey((k) => k + 1)} />
      )}
    </div>
  )
}

function Tile({ icon, tone, label, value, to }: { icon: IconName; tone: string; label: string; value: number | string; to?: string }) {
  const body = (
    <>
      <span className={`ops-tile-icon tone-${tone}`}>
        <Icon name={icon} size={30} />
      </span>
      <span className="ops-tile-text">
        <span className="ops-tile-label">{label}</span>
        <strong className="ops-tile-value">{value}</strong>
      </span>
    </>
  )
  return to ? (
    <Link to={to} className="ops-tile">
      {body}
    </Link>
  ) : (
    <div className="ops-tile">{body}</div>
  )
}

interface MemberRow {
  key: string
  id: string | null
  name: string
  email: string
  role: 'Admin' | 'Member'
  status: 'Active' | 'Invited'
  expiresAt?: string
}

/** Members: joined people and, for admins, the people invited but not joined yet. */
function MembersView({ refreshKey }: { refreshKey: number }) {
  const { identity } = useIdentity()
  // A Workeasy360 sign-in has no email workspace to administer here: its people are managed in Workeasy360.
  const workeasy360 = identity?.provider === 'workeasy360'
  const { people, isAdmin } = usePeople()
  const invitations = useAsyncData(() => (isAdmin ? workspaceApi.invitations() : Promise.resolve([] as Invitation[])), [isAdmin, refreshKey])
  const [query, setQuery] = useState('')
  const [role, setRole] = useState('')
  const [status, setStatus] = useState('')
  const [resetLink, setResetLink] = useState<{ email: string; link: string } | null>(null)
  const [error, setError] = useState<string | null>(null)

  const rows = useMemo<MemberRow[]>(() => {
    const joined: MemberRow[] = people.map((p) => ({
      key: p.id,
      id: p.id,
      name: p.name,
      email: p.email,
      role: p.workspaceRole === 'ADMIN' ? 'Admin' : 'Member',
      status: 'Active',
    }))
    const invited: MemberRow[] = (invitations.data ?? []).map((i) => ({
      key: `invite-${i.id}`,
      id: null,
      name: i.email,
      email: i.email,
      role: 'Member',
      status: 'Invited',
      expiresAt: i.expiresAt,
    }))
    return [...joined, ...invited]
  }, [people, invitations.data])

  const shown = rows.filter((r) => {
    const q = query.trim().toLowerCase()
    if (q && !`${r.name} ${r.email}`.toLowerCase().includes(q)) return false
    if (role && r.role !== role) return false
    if (status && r.status !== status) return false
    return true
  })
  const admins = rows.filter((r) => r.role === 'Admin' && r.status === 'Active').length
  const invitedCount = rows.filter((r) => r.status === 'Invited').length

  async function createResetLink(accountId: string, email: string) {
    setError(null)
    try {
      const { token } = await workspaceApi.resetLink(accountId)
      setResetLink({ email, link: passwordResetLink(token) })
    } catch (err) {
      setError(toUserMessage(err))
    }
  }

  return (
    <>
      <section className="ops-stats ops-stats-3" aria-label="Member counts">
        <Tile icon="team" tone="green" label="Members" value={people.length} />
        <Tile icon="crown" tone="purple" label="Admins" value={admins} />
        <Tile icon="mail" tone="orange" label="Invited, not joined" value={isAdmin ? invitedCount : '—'} to={isAdmin ? '/team/invitations' : undefined} />
      </section>

      <div className="ops-filters ops-filters-3" role="search" aria-label="Find members">
        <label className="ops-search">
          <Icon name="search" size={22} />
          <input type="search" placeholder="Search members by name or email..." aria-label="Search members by name or email" value={query} onChange={(e) => setQuery(e.target.value)} />
        </label>
        <Select label="Role" value={role} onChange={setRole} options={[{ value: '', label: 'All Roles' }, { value: 'Admin', label: 'Admin' }, { value: 'Member', label: 'Member' }]} />
        <Select label="Status" value={status} onChange={setStatus} options={[{ value: '', label: 'All Status' }, { value: 'Active', label: 'Active' }, { value: 'Invited', label: 'Invited' }]} />
      </div>

      {error && <InlineError message={error} />}

      <section className="ops-card ops-table-card">
        <div className="ops-table-scroll">
          <table className="ops-table">
            <thead>
              <tr>
                <th>Member</th>
                <th>Email</th>
                <th>Role</th>
                <th>Operations Access</th>
                <th>Status</th>
                <th>Teams</th>
                {isAdmin && <th className="ops-th-actions">Actions</th>}
              </tr>
            </thead>
            <tbody>
              {shown.length === 0 && (
                <tr>
                  <td colSpan={isAdmin ? 7 : 6} className="ops-empty">
                    {rows.length === 0
                      ? workeasy360
                        ? 'You signed in with Workeasy360: the people in your organization are managed in Workeasy360. Add a teammate to a project from its Members tab.'
                        : 'No members to show yet.'
                      : 'No members match these filters.'}
                  </td>
                </tr>
              )}
              {shown.map((r) => (
                <tr key={r.key}>
                  <td>
                    <span className="ops-owner">
                      {r.id ? (
                        <Avatar userId={r.id} name={r.name} />
                      ) : (
                        <span className="ops-avatar-empty" aria-hidden="true">
                          <Icon name="mail" size={16} />
                        </span>
                      )}
                      <strong>
                        {r.status === 'Invited' ? 'Invited member' : r.name}
                        {r.id && r.id === identity?.userId ? ' (you)' : ''}
                      </strong>
                    </span>
                  </td>
                  <td>{r.email}</td>
                  <td>
                    <span className={`ops-role role-${r.role.toLowerCase()}`}>{r.role}</span>
                  </td>
                  <td>{r.status === 'Active' ? 'Yes' : 'After joining'}</td>
                  <td>
                    <span className={`ops-status ${r.status === 'Active' ? 'status-active' : 'status-on_hold'}`}>{r.status}</span>
                    {r.expiresAt && <span className="ops-cell-note">until {formatDate(r.expiresAt)}</span>}
                  </td>
                  <td className="ops-muted">—</td>
                  {isAdmin && (
                    <td className="ops-actions">
                      {r.id && r.id !== identity?.userId && (
                        <button type="button" className="ops-link-btn" onClick={() => createResetLink(r.id as string, r.email)}>
                          Reset password
                        </button>
                      )}
                      {r.status === 'Invited' && (
                        <Link to="/team/invitations" className="ops-link-btn">
                          Manage
                        </Link>
                      )}
                    </td>
                  )}
                </tr>
              ))}
            </tbody>
          </table>
        </div>
        <footer className="ops-table-foot">
          <span>
            Showing {shown.length} of {rows.length} {rows.length === 1 ? 'member' : 'members'}. Add members to a project from its Members tab.
          </span>
        </footer>
      </section>

      {resetLink && (
        <Modal title="Password reset link" onClose={() => setResetLink(null)}>
          <p className="text-muted">Send this to {resetLink.email}. It works once and expires in 2 hours.</p>
          <CopyableLink link={resetLink.link} label="Password reset link" />
          <FormActions>
            <button type="button" className="btn btn-primary" onClick={() => setResetLink(null)}>
              Done
            </button>
          </FormActions>
        </Modal>
      )}
    </>
  )
}

/** Teams are the next build stage; this says so plainly instead of showing anything that looks working. */
function TeamsComingSoon() {
  return (
    <section className="ops-card ops-soon-card" aria-labelledby="teams-soon">
      <span className="ops-tile-icon tone-purple">
        <Icon name="team" size={34} />
      </span>
      <div>
        <span className="ops-soon-badge">Coming in the next update</span>
        <h2 id="teams-soon">Teams are not available yet</h2>
        <p>When Teams arrive you will be able to:</p>
        <ul className="ops-soon-list">
          <li>Create a named team, such as Sales, Support or Development.</li>
          <li>Choose a team manager and add existing members; one person can be in several teams.</li>
          <li>Add a whole team to a project and see exactly who that gives access to.</li>
          <li>Keep each task with one named person responsible, even when a team is on the project.</li>
        </ul>
        <p className="ops-muted">
          Until then, add people to each project one by one from the project&apos;s Members tab.
        </p>
      </div>
    </section>
  )
}

/** Invitations that are still waiting (admins only): send again or withdraw. */
function InvitationsView({ refreshKey, onChanged }: { refreshKey: number; onChanged: () => void }) {
  const { isAdmin } = usePeople()
  const invitations = useAsyncData(() => (isAdmin ? workspaceApi.invitations() : Promise.resolve([] as Invitation[])), [isAdmin, refreshKey])
  const [resend, setResend] = useState<Invitation | null>(null)
  const [withdraw, setWithdraw] = useState<Invitation | null>(null)

  if (!isAdmin) {
    return (
      <section className="ops-card">
        <p className="ops-empty">Only workspace admins can see and manage invitations.</p>
      </section>
    )
  }

  const list = invitations.data ?? []
  return (
    <>
      <section className="ops-card ops-table-card">
        <div className="ops-table-scroll">
          <table className="ops-table">
            <thead>
              <tr>
                <th>Email</th>
                <th>Status</th>
                <th>Link expires</th>
                <th className="ops-th-actions">Actions</th>
              </tr>
            </thead>
            <tbody>
              {invitations.loading && list.length === 0 && (
                <tr>
                  <td colSpan={4} className="ops-empty">Loading invitations…</td>
                </tr>
              )}
              {invitations.error && (
                <tr>
                  <td colSpan={4} className="ops-empty">{invitations.error}</td>
                </tr>
              )}
              {!invitations.loading && !invitations.error && list.length === 0 && (
                <tr>
                  <td colSpan={4} className="ops-empty">No invitations are waiting. Everyone you invited has joined, or the links have expired.</td>
                </tr>
              )}
              {list.map((i) => (
                <tr key={i.id}>
                  <td>
                    <strong>{i.email}</strong>
                  </td>
                  <td>
                    <span className="ops-status status-on_hold">Pending</span>
                  </td>
                  <td>{formatDate(i.expiresAt)}</td>
                  <td className="ops-actions">
                    <button type="button" className="ops-link-btn" onClick={() => setResend(i)}>
                      Resend
                    </button>
                    <button type="button" className="ops-link-btn danger" onClick={() => setWithdraw(i)}>
                      Revoke
                    </button>
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
        <footer className="ops-table-foot">
          <span>
            Accepted invitations show on Members as Active. Expired links are not listed; invite the person again.
          </span>
        </footer>
      </section>

      {resend && (
        <InviteDialog initialEmail={resend.email} onClose={() => setResend(null)} onInvited={onChanged} />
      )}
      {withdraw && (
        <ConfirmDialog
          title="Revoke invitation"
          message={`Revoke the invitation for ${withdraw.email}? The link stops working straight away.`}
          confirmLabel="Revoke"
          onConfirm={async () => {
            await workspaceApi.revokeInvitation(withdraw.id)
            onChanged()
          }}
          onClose={() => setWithdraw(null)}
        />
      )}
    </>
  )
}

function Select({ label, value, onChange, options }: { label: string; value: string; onChange: (v: string) => void; options: { value: string; label: string }[] }) {
  return (
    <label className="ops-select">
      <select aria-label={label} value={value} onChange={(e) => onChange(e.target.value)}>
        {options.map((o) => (
          <option key={o.value} value={o.value}>
            {o.label}
          </option>
        ))}
      </select>
      <Icon name="chevronDown" size={18} className="ops-select-chevron" />
    </label>
  )
}
