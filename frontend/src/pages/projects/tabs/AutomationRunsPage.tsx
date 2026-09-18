import { Link, useParams } from 'react-router-dom'
import { useAsyncData } from '../../../hooks/useAsyncData'
import { automationsApi } from '../../../api/automations'
import { DataTable, type Column } from '../../../components/common/DataTable'
import { RunStatusBadge } from '../../../components/common/Badge'
import { formatDateTime } from '../../../utils/format'
import type { AutomationRun } from '../../../types/automation'

export function AutomationRunsPage() {
  const { automationId } = useParams<{ automationId: string }>()
  const { data, loading, error, reload } = useAsyncData(() => automationsApi.runs(automationId!), [automationId])

  const columns: Column<AutomationRun>[] = [
    { key: 'executedAt', header: 'Executed', render: (r) => formatDateTime(r.executedAt) },
    { key: 'trigger', header: 'Trigger event', render: (r) => r.triggerEvent },
    { key: 'status', header: 'Status', render: (r) => <RunStatusBadge status={r.status} /> },
    { key: 'error', header: 'Error', render: (r) => r.errorMessage ?? '—' },
  ]

  return (
    <div>
      <p>
        <Link to="../../automations" relative="path">
          ← Back to automations
        </Link>
      </p>
      <div className="page-header">
        <div className="page-header-title">
          <h1>Run history</h1>
          <p>SUCCEEDED runs sent the configured notification/chat message. FAILED runs did not — the triggering change still succeeded regardless.</p>
        </div>
      </div>
      <DataTable
        items={data}
        loading={loading}
        error={error}
        onRetry={reload}
        columns={columns}
        getRowKey={(r) => r.id}
        emptyTitle="No runs recorded yet."
      />
    </div>
  )
}
