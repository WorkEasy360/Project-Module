import { useProjectWorkspace } from '../../../context/ProjectWorkspaceContext'
import { useAsyncData } from '../../../hooks/useAsyncData'
import { delayDetectionApi } from '../../../api/delayDetection'
import { DataTable, type Column } from '../../../components/common/DataTable'
import { Badge } from '../../../components/common/Badge'
import { formatDate } from '../../../utils/format'
import type { DelayedItem } from '../../../types/work'

export function DelayedTab() {
  const { project } = useProjectWorkspace()
  const { data, loading, error, reload } = useAsyncData(() => delayDetectionApi.get(project.id), [project.id])

  const columns: Column<DelayedItem>[] = [
    { key: 'type', header: 'Type', render: (i) => <Badge tone="info">{i.entityType}</Badge> },
    { key: 'name', header: 'Name', render: (i) => i.name },
    { key: 'dueDate', header: 'Due date', render: (i) => formatDate(i.dueDate) },
    { key: 'daysOverdue', header: 'Days overdue', render: (i) => <Badge tone="danger">{i.daysOverdue}</Badge> },
  ]

  return (
    <div>
      <p className="text-muted">
        Deterministic delay detection — items whose due date has passed without completion. This
        is not an AI prediction; see the AI tab for AI-generated recommendations.
      </p>
      <DataTable
        items={data?.items ?? null}
        loading={loading}
        error={error}
        onRetry={reload}
        columns={columns}
        getRowKey={(i) => `${i.entityType}-${i.id}`}
        emptyTitle="Nothing is delayed."
      />
    </div>
  )
}
