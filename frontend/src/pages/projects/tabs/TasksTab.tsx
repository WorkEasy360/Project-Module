import { useMemo, useState } from 'react'
import { Link } from 'react-router-dom'
import { useProjectWorkspace } from '../../../context/ProjectWorkspaceContext'
import { useAsyncData } from '../../../hooks/useAsyncData'
import { tasksApi } from '../../../api/tasks'
import { DataTable, type Column } from '../../../components/common/DataTable'
import { CreateTaskDialog } from '../CreateTaskDialog'
import { ConfirmDialog } from '../../../components/common/ConfirmDialog'
import { TextField, SelectField } from '../../../components/forms/fields'
import { TaskStatusBadge, ArchivedBadge } from '../../../components/common/Badge'
import { TaskStatusControl } from '../../../components/task/TaskStatusControl'
import { formatDate } from '../../../utils/format'
import { TASK_SORT_FIELDS, TASK_STATUSES, type Task, type TaskListQuery, type TaskSortField } from '../../../types/work'
import type { SortDirection } from '../../../types/common'

export function TasksTab() {
  const { project, can } = useProjectWorkspace()
  const [statusFilter, setStatusFilter] = useState('')
  const [assigneeFilter, setAssigneeFilter] = useState('')
  const [showArchived, setShowArchived] = useState(false)
  const [sortBy, setSortBy] = useState<TaskSortField>('CREATED_AT')
  const [sortDir, setSortDir] = useState<SortDirection>('DESC')
  const [createOpen, setCreateOpen] = useState(false)
  const [selected, setSelected] = useState<Set<string>>(new Set())
  const [bulkResult, setBulkResult] = useState<string | null>(null)
  const [bulkArchiveOpen, setBulkArchiveOpen] = useState(false)

  const query: TaskListQuery = useMemo(
    () => ({
      status: (statusFilter || undefined) as Task['status'] | undefined,
      assigneeId: assigneeFilter || undefined,
      archived: showArchived,
      sortBy,
      sortDir,
    }),
    [statusFilter, assigneeFilter, showArchived, sortBy, sortDir],
  )

  const { data, loading, error, reload } = useAsyncData(
    () => tasksApi.list(project.id, query),
    [project.id, statusFilter, assigneeFilter, showArchived, sortBy, sortDir],
  )

  function toggleSelected(id: string) {
    setSelected((prev) => {
      const next = new Set(prev)
      if (next.has(id)) next.delete(id)
      else next.add(id)
      return next
    })
  }

  async function handleBulkArchive() {
    const response = await tasksApi.bulkArchive(project.id, { ids: Array.from(selected) })
    const failed = response.results.filter((r) => !r.succeeded)
    setBulkResult(
      failed.length === 0
        ? `Archived ${response.results.length} task(s) successfully.`
        : `Archived ${response.results.length - failed.length} of ${response.results.length}. Failed: ${failed
            .map((f) => f.error)
            .join('; ')}`,
    )
    setSelected(new Set())
    reload()
  }

  const columns: Column<Task>[] = [
    {
      key: 'select',
      header: '',
      render: (t) => (
        <input
          type="checkbox"
          checked={selected.has(t.id)}
          onChange={() => toggleSelected(t.id)}
          aria-label={`Select ${t.name}`}
        />
      ),
    },
    {
      key: 'name',
      header: 'Name',
      render: (t) => (
        <Link to={`../tasks/${t.id}`} relative="path">
          {t.name}
        </Link>
      ),
    },
    {
      key: 'status',
      header: 'Status',
      render: (t) => (
        <span className="status-cell">
          <TaskStatusBadge status={t.status} />
          {can('EDIT_PROJECT') && !t.archived && <TaskStatusControl task={t} onChanged={reload} compact />}
        </span>
      ),
    },
    { key: 'dueDate', header: 'Due', render: (t) => formatDate(t.dueDate) },
    { key: 'assignee', header: 'Assignee', render: (t) => t.assigneeId ?? '—' },
    { key: 'archived', header: '', render: (t) => (t.archived ? <ArchivedBadge /> : null) },
  ]

  return (
    <div>
      <div className="filter-bar">
        <SelectField
          id="task-status-filter"
          label="Filter by status"
          value={statusFilter}
          onChange={setStatusFilter}
          allowEmpty
          emptyLabel="All statuses"
          options={TASK_STATUSES.map((s) => ({ value: s, label: s }))}
        />
        <TextField
          id="task-assignee-filter"
          label="Assignee ID"
          value={assigneeFilter}
          onChange={setAssigneeFilter}
        />
        <SelectField
          id="task-sortby"
          label="Sort by"
          value={sortBy}
          onChange={setSortBy}
          options={TASK_SORT_FIELDS.map((s) => ({ value: s, label: s }))}
        />
        <SelectField
          id="task-sortdir"
          label="Direction"
          value={sortDir}
          onChange={setSortDir}
          options={[
            { value: 'ASC', label: 'Ascending' },
            { value: 'DESC', label: 'Descending' },
          ]}
        />
        <label className="checkbox-row">
          <input type="checkbox" checked={showArchived} onChange={(e) => setShowArchived(e.target.checked)} />
          Show archived
        </label>
        <div className="spacer" />
        {can('EDIT_PROJECT') && selected.size > 0 && (
          <button className="btn btn-sm btn-danger" onClick={() => setBulkArchiveOpen(true)}>
            Archive {selected.size} selected
          </button>
        )}
        {can('EDIT_PROJECT') && (
          <button className="btn btn-primary" onClick={() => setCreateOpen(true)}>
            New Task
          </button>
        )}
      </div>

      {bulkResult && <div className="banner banner-info">{bulkResult}</div>}

      <DataTable
        items={data}
        loading={loading}
        error={error}
        onRetry={reload}
        columns={columns}
        getRowKey={(t) => t.id}
        emptyTitle="No tasks yet"
        emptyDescription={
          statusFilter || assigneeFilter || showArchived
            ? 'Nothing matches these filters. Try clearing them.'
            : 'Create your first task to start organizing project work.'
        }
        emptyAction={
          can('EDIT_PROJECT') && !statusFilter && !assigneeFilter ? (
            <button className="btn btn-primary" onClick={() => setCreateOpen(true)}>
              Create Task
            </button>
          ) : undefined
        }
      />

      {createOpen && (
        <CreateTaskDialog
          projectId={project.id}
          onClose={() => setCreateOpen(false)}
          onCreated={() => {
            setCreateOpen(false)
            reload()
          }}
        />
      )}

      {bulkArchiveOpen && (
        <ConfirmDialog
          title="Archive tasks"
          message={`Archive ${selected.size} selected task(s)?`}
          confirmLabel="Archive"
          onConfirm={handleBulkArchive}
          onClose={() => setBulkArchiveOpen(false)}
        />
      )}
    </div>
  )
}
