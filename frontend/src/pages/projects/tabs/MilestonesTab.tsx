import { useState } from 'react'
import { useProjectWorkspace } from '../../../context/ProjectWorkspaceContext'
import { useAsyncData } from '../../../hooks/useAsyncData'
import { milestonesApi } from '../../../api/milestones'
import { phasesApi } from '../../../api/phases'
import { DataTable, type Column } from '../../../components/common/DataTable'
import { ConfirmDialog } from '../../../components/common/ConfirmDialog'
import { formatDate } from '../../../utils/format'
import { MilestoneStatusBadge } from '../../../components/common/Badge'
import { MilestoneFormDialog } from '../CreateMilestoneDialog'
import type { Milestone } from '../../../types/work'

export function MilestonesTab() {
  const { project, can } = useProjectWorkspace()
  const { data, loading, error, reload } = useAsyncData(() => milestonesApi.list(project.id), [project.id])
  const phases = useAsyncData(() => phasesApi.list(project.id), [project.id])
  const [createOpen, setCreateOpen] = useState(false)
  const [editMilestone, setEditMilestone] = useState<Milestone | null>(null)
  const [archiveMilestone, setArchiveMilestone] = useState<Milestone | null>(null)

  const phaseName = (id: string | null) => phases.data?.find((p) => p.id === id)?.name ?? '—'

  const columns: Column<Milestone>[] = [
    { key: 'name', header: 'Name', render: (m) => m.name },
    { key: 'phase', header: 'Phase', render: (m) => phaseName(m.phaseId) },
    { key: 'dueDate', header: 'Due', render: (m) => formatDate(m.dueDate) },
    { key: 'status', header: 'Status', render: (m) => <MilestoneStatusBadge status={m.status} /> },
    {
      key: 'actions',
      header: '',
      render: (m) =>
        can('EDIT_PROJECT') ? (
          <div className="page-actions">
            <button className="btn btn-sm" onClick={() => setEditMilestone(m)}>
              Edit
            </button>
            <button className="btn btn-sm btn-danger" onClick={() => setArchiveMilestone(m)}>
              Archive
            </button>
          </div>
        ) : null,
    },
  ]

  return (
    <div>
      <div className="toolbar">
        <div className="spacer" />
        {can('EDIT_PROJECT') && (
          <button className="btn btn-primary" onClick={() => setCreateOpen(true)}>
            New Milestone
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
        emptyTitle="No milestones yet."
      />

      {createOpen && (
        <MilestoneFormDialog
          title="New Milestone"
          initial={null}
          phaseOptions={phases.data ?? []}
          onClose={() => setCreateOpen(false)}
          onSubmit={async (values) => {
            await milestonesApi.create(project.id, values)
            reload()
          }}
        />
      )}
      {editMilestone && (
        <MilestoneFormDialog
          title="Edit Milestone"
          initial={editMilestone}
          phaseOptions={phases.data ?? []}
          onClose={() => setEditMilestone(null)}
          onSubmit={async (values) => {
            await milestonesApi.update(editMilestone.id, { ...values, version: editMilestone.version })
            reload()
          }}
        />
      )}
      {archiveMilestone && (
        <ConfirmDialog
          title="Archive milestone"
          message={`Archive "${archiveMilestone.name}"?`}
          confirmLabel="Archive"
          onConfirm={async () => {
            await milestonesApi.archive(archiveMilestone.id)
            reload()
          }}
          onClose={() => setArchiveMilestone(null)}
        />
      )}
    </div>
  )
}
