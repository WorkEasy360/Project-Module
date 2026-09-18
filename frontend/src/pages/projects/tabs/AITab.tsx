import { useState } from 'react'
import { useProjectWorkspace } from '../../../context/ProjectWorkspaceContext'
import { useAsyncData } from '../../../hooks/useAsyncData'
import { aiApi } from '../../../api/ai'
import { tasksApi } from '../../../api/tasks'
import { DataTable, type Column } from '../../../components/common/DataTable'
import { Modal } from '../../../components/common/Modal'
import { SelectField, TextareaField, FormActions } from '../../../components/forms/fields'
import { InlineError } from '../../../components/common/ErrorState'
import { toUserMessage } from '../../../api/errorMessage'
import { ApiError } from '../../../api/client'
import { RecommendationStatusBadge, Badge } from '../../../components/common/Badge'
import { formatDateTime } from '../../../utils/format'
import {
  RECOMMENDATION_TYPES,
  RECOMMENDATION_TYPES_REQUIRING_QUESTION,
  RECOMMENDATION_TYPES_REQUIRING_RESOURCE,
  RECOMMENDATION_TYPE_LABELS,
  type AIRecommendation,
  type RecommendationType,
} from '../../../types/ai'

export function AITab() {
  const { project, can } = useProjectWorkspace()
  const { data, loading, error, reload } = useAsyncData(() => aiApi.list(project.id), [project.id])
  const [requestOpen, setRequestOpen] = useState(false)
  const [viewing, setViewing] = useState<AIRecommendation | null>(null)

  const columns: Column<AIRecommendation>[] = [
    { key: 'type', header: 'Type', render: (r) => RECOMMENDATION_TYPE_LABELS[r.type] },
    { key: 'title', header: 'Title', render: (r) => r.title ?? '(pending)' },
    { key: 'status', header: 'Status', render: (r) => <RecommendationStatusBadge status={r.status} /> },
    { key: 'requested', header: 'Requested', render: (r) => formatDateTime(r.createdAt) },
    {
      key: 'actions',
      header: '',
      render: (r) => (
        <button className="btn btn-sm" onClick={() => setViewing(r)}>
          View
        </button>
      ),
    },
  ]

  return (
    <div>
      <p className="text-muted">
        AI recommendations are suggestions only — accepting one applies it through the same
        application services and authorization as a manual edit would. Rejecting or ignoring a
        recommendation changes nothing. If the AI provider is not configured, requesting a new
        recommendation will show a clear "unavailable" message rather than fabricated content.
      </p>
      <div className="toolbar">
        <div className="spacer" />
        {can('EDIT_PROJECT') && (
          <button className="btn btn-primary" onClick={() => setRequestOpen(true)}>
            Request Recommendation
          </button>
        )}
      </div>

      <DataTable
        items={data}
        loading={loading}
        error={error}
        onRetry={reload}
        columns={columns}
        getRowKey={(r) => r.id}
        emptyTitle="No suggestions yet"
        emptyDescription="Ask for a suggestion about a task, the project's risks, or the project as a whole."
      />

      {requestOpen && (
        <RequestRecommendationDialog
          projectId={project.id}
          onClose={() => setRequestOpen(false)}
          onRequested={() => {
            setRequestOpen(false)
            reload()
          }}
        />
      )}
      {viewing && (
        <RecommendationDetailDialog recommendation={viewing} onClose={() => setViewing(null)} onChanged={reload} />
      )}
    </div>
  )
}

export function RequestRecommendationDialog({
  projectId,
  onClose,
  onRequested,
  initialType = 'PROJECT_SUMMARY',
  initialResourceId = '',
}: {
  projectId: string
  onClose: () => void
  onRequested: () => void
  /** Contextual entry points (a task page, the overview) preselect what to ask about. */
  initialType?: RecommendationType
  initialResourceId?: string
}) {
  const [type, setType] = useState<RecommendationType>(initialType)
  const [resourceId, setResourceId] = useState(initialResourceId)
  const [question, setQuestion] = useState('')
  const [error, setError] = useState<string | null>(null)
  const [unavailable, setUnavailable] = useState(false)
  const [submitting, setSubmitting] = useState(false)

  const tasks = useAsyncData(
    () => (RECOMMENDATION_TYPES_REQUIRING_RESOURCE.includes(type) ? tasksApi.list(projectId, { archived: false }) : Promise.resolve([])),
    [projectId, type],
  )

  const needsResource = RECOMMENDATION_TYPES_REQUIRING_RESOURCE.includes(type)
  const needsQuestion = RECOMMENDATION_TYPES_REQUIRING_QUESTION.includes(type)

  async function handleSubmit(event: React.FormEvent) {
    event.preventDefault()
    setSubmitting(true)
    setError(null)
    setUnavailable(false)
    try {
      await aiApi.request(projectId, {
        type,
        resourceId: needsResource ? resourceId || null : null,
        question: needsQuestion ? question || null : null,
      })
      onRequested()
    } catch (err) {
      if (err instanceof ApiError && err.isIntegrationUnavailable) {
        setUnavailable(true)
      }
      setError(toUserMessage(err))
    } finally {
      setSubmitting(false)
    }
  }

  return (
    <Modal title="Request AI Recommendation" onClose={onClose}>
      <form onSubmit={handleSubmit} className="form-grid">
        {unavailable && (
          <div className="banner banner-warning">
            AI service is currently unavailable. No recommendation was generated or faked — try
            again once an AI provider is configured on the backend.
          </div>
        )}
        {error && !unavailable && <InlineError message={error} />}
        <SelectField
          id="ai-type"
          label="Recommendation type"
          value={type}
          onChange={setType}
          required
          options={RECOMMENDATION_TYPES.map((t) => ({ value: t, label: RECOMMENDATION_TYPE_LABELS[t] }))}
        />
        {needsResource && (
          <SelectField
            id="ai-resource"
            label="Task"
            value={resourceId}
            onChange={setResourceId}
            required
            allowEmpty
            emptyLabel={tasks.loading ? 'Loading tasks…' : 'Select a task'}
            options={(tasks.data ?? []).map((t) => ({ value: t.id, label: t.name }))}
          />
        )}
        {needsQuestion && (
          <TextareaField id="ai-question" label="Question" value={question} onChange={setQuestion} required />
        )}
        <FormActions>
          <button type="button" className="btn" onClick={onClose} disabled={submitting}>
            Cancel
          </button>
          <button
            type="submit"
            className="btn btn-primary"
            disabled={submitting || (needsResource && !resourceId) || (needsQuestion && !question.trim())}
          >
            {submitting ? 'Requesting…' : 'Request'}
          </button>
        </FormActions>
      </form>
    </Modal>
  )
}

function RecommendationDetailDialog({
  recommendation,
  onClose,
  onChanged,
}: {
  recommendation: AIRecommendation
  onClose: () => void
  onChanged: () => void
}) {
  const [error, setError] = useState<string | null>(null)
  const [submitting, setSubmitting] = useState(false)

  async function handleAccept() {
    setSubmitting(true)
    setError(null)
    try {
      await aiApi.accept(recommendation.id)
      onChanged()
      onClose()
    } catch (err) {
      setError(toUserMessage(err))
    } finally {
      setSubmitting(false)
    }
  }

  async function handleReject() {
    setSubmitting(true)
    setError(null)
    try {
      await aiApi.reject(recommendation.id)
      onChanged()
      onClose()
    } catch (err) {
      setError(toUserMessage(err))
    } finally {
      setSubmitting(false)
    }
  }

  return (
    <Modal title={RECOMMENDATION_TYPE_LABELS[recommendation.type]} onClose={onClose}>
      {error && <InlineError message={error} />}
      <div className="form-grid">
        <div>
          <Badge tone="neutral">{recommendation.status}</Badge> Requested {formatDateTime(recommendation.createdAt)}
        </div>
        {recommendation.question && (
          <div>
            <strong>Question:</strong> {recommendation.question}
          </div>
        )}
        {recommendation.title && (
          <div>
            <strong>Title:</strong> {recommendation.title}
          </div>
        )}
        {recommendation.rationale && (
          <div>
            <strong>Rationale:</strong>
            <p>{recommendation.rationale}</p>
          </div>
        )}
        {recommendation.payload && (
          <div>
            <strong>Details:</strong>
            <pre style={{ whiteSpace: 'pre-wrap', background: 'var(--color-bg)', padding: 10, borderRadius: 6 }}>
              {recommendation.payload}
            </pre>
          </div>
        )}
        {recommendation.status === 'PENDING' && (
          <FormActions>
            <button className="btn btn-danger" onClick={handleReject} disabled={submitting}>
              Reject
            </button>
            <button className="btn btn-primary" onClick={handleAccept} disabled={submitting}>
              Accept
            </button>
          </FormActions>
        )}
      </div>
    </Modal>
  )
}
