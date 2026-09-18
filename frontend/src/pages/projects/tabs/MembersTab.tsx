import { useState } from 'react'
import { useProjectWorkspace } from '../../../context/ProjectWorkspaceContext'
import { useAsyncData } from '../../../hooks/useAsyncData'
import { membersApi } from '../../../api/members'
import { DataTable, type Column } from '../../../components/common/DataTable'
import { Modal } from '../../../components/common/Modal'
import { ConfirmDialog } from '../../../components/common/ConfirmDialog'
import { TextField, SelectField, FormActions } from '../../../components/forms/fields'
import { InlineError } from '../../../components/common/ErrorState'
import { toUserMessage } from '../../../api/errorMessage'
import { ASSIGNABLE_PROJECT_ROLES, type ProjectMember, type ProjectRole } from '../../../types/project'
import { Badge } from '../../../components/common/Badge'
import { formatDateTime } from '../../../utils/format'

export function MembersTab() {
  const { project, can } = useProjectWorkspace()
  const { data, loading, error, reload } = useAsyncData(() => membersApi.list(project.id), [project.id])
  const [addOpen, setAddOpen] = useState(false)
  const [editMember, setEditMember] = useState<ProjectMember | null>(null)
  const [removeMember, setRemoveMember] = useState<ProjectMember | null>(null)

  const columns: Column<ProjectMember>[] = [
    { key: 'userId', header: 'User', render: (m) => m.userId },
    { key: 'role', header: 'Role', render: (m) => <Badge tone={m.role === 'OWNER' ? 'primary' : 'neutral'}>{m.role}</Badge> },
    { key: 'joinedAt', header: 'Joined', render: (m) => formatDateTime(m.joinedAt) },
    {
      key: 'actions',
      header: '',
      render: (m) =>
        can('MANAGE_MEMBERS') && m.role !== 'OWNER' ? (
          <div className="page-actions">
            <button className="btn btn-sm" onClick={() => setEditMember(m)}>
              Change role
            </button>
            <button className="btn btn-sm btn-danger" onClick={() => setRemoveMember(m)}>
              Remove
            </button>
          </div>
        ) : null,
    },
  ]

  return (
    <div>
      <div className="toolbar">
        <div className="spacer" />
        {can('MANAGE_MEMBERS') && (
          <button className="btn btn-primary" onClick={() => setAddOpen(true)}>
            Add Member
          </button>
        )}
      </div>

      <DataTable
        items={data}
        loading={loading}
        error={error}
        onRetry={reload}
        columns={columns}
        getRowKey={(m) => m.id}
        emptyTitle="No team members yet"
        emptyDescription="Add people to the project so they can see and work on it."
      />

      {addOpen && (
        <AddMemberDialog projectId={project.id} onClose={() => setAddOpen(false)} onAdded={reload} />
      )}
      {editMember && (
        <ChangeRoleDialog
          projectId={project.id}
          member={editMember}
          onClose={() => setEditMember(null)}
          onChanged={reload}
        />
      )}
      {removeMember && (
        <ConfirmDialog
          title="Remove member"
          message={`Remove this member from the project?`}
          confirmLabel="Remove"
          onConfirm={async () => {
            await membersApi.remove(project.id, removeMember.id)
            reload()
          }}
          onClose={() => setRemoveMember(null)}
        />
      )}
    </div>
  )
}

function AddMemberDialog({
  projectId,
  onClose,
  onAdded,
}: {
  projectId: string
  onClose: () => void
  onAdded: () => void
}) {
  const [userId, setUserId] = useState('')
  const [role, setRole] = useState<ProjectRole>('MEMBER')
  const [error, setError] = useState<string | null>(null)
  const [submitting, setSubmitting] = useState(false)

  async function handleSubmit(event: React.FormEvent) {
    event.preventDefault()
    setSubmitting(true)
    setError(null)
    try {
      await membersApi.add(projectId, { userId, role })
      onAdded()
      onClose()
    } catch (err) {
      setError(toUserMessage(err))
    } finally {
      setSubmitting(false)
    }
  }

  return (
    <Modal title="Add Member" onClose={onClose}>
      <form onSubmit={handleSubmit} className="form-grid">
        {error && <InlineError message={error} />}
        <TextField id="member-userId" label="User ID (UUID)" value={userId} onChange={setUserId} required />
        <SelectField
          id="member-role"
          label="Role"
          value={role}
          onChange={setRole}
          required
          options={ASSIGNABLE_PROJECT_ROLES.map((r) => ({ value: r, label: r }))}
        />
        <FormActions>
          <button type="button" className="btn" onClick={onClose} disabled={submitting}>
            Cancel
          </button>
          <button type="submit" className="btn btn-primary" disabled={submitting || !userId.trim()}>
            {submitting ? 'Adding…' : 'Add Member'}
          </button>
        </FormActions>
      </form>
    </Modal>
  )
}

function ChangeRoleDialog({
  projectId,
  member,
  onClose,
  onChanged,
}: {
  projectId: string
  member: ProjectMember
  onClose: () => void
  onChanged: () => void
}) {
  const [role, setRole] = useState<ProjectRole>(member.role)
  const [error, setError] = useState<string | null>(null)
  const [submitting, setSubmitting] = useState(false)

  async function handleSubmit(event: React.FormEvent) {
    event.preventDefault()
    setSubmitting(true)
    setError(null)
    try {
      await membersApi.changeRole(projectId, member.id, { role })
      onChanged()
      onClose()
    } catch (err) {
      setError(toUserMessage(err))
    } finally {
      setSubmitting(false)
    }
  }

  return (
    <Modal title="Change Member Role" onClose={onClose}>
      <form onSubmit={handleSubmit} className="form-grid">
        {error && <InlineError message={error} />}
        <SelectField
          id="change-role"
          label="Role"
          value={role}
          onChange={setRole}
          required
          options={ASSIGNABLE_PROJECT_ROLES.map((r) => ({ value: r, label: r }))}
        />
        <FormActions>
          <button type="button" className="btn" onClick={onClose} disabled={submitting}>
            Cancel
          </button>
          <button type="submit" className="btn btn-primary" disabled={submitting}>
            {submitting ? 'Saving…' : 'Save'}
          </button>
        </FormActions>
      </form>
    </Modal>
  )
}
