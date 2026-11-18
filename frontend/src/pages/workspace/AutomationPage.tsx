import { useMemo, useState } from 'react'
import { Link } from 'react-router-dom'
import '../../styles/ops.css'
import '../../styles/ops-sections.css'
import { automationsApi } from '../../api/automations'
import { toUserMessage } from '../../api/errorMessage'
import { useAllProjects, usePerProject } from '../../hooks/useWorkspaceData'
import { useAsyncData } from '../../hooks/useAsyncData'
import { usePeople } from '../../context/PeopleContext'
import { ConfirmDialog } from '../../components/common/ConfirmDialog'
import { ErrorState, InlineError } from '../../components/common/ErrorState'
import { Icon, type IconName } from '../../components/common/Icon'
import { formatDateTime } from '../../utils/format'
import { triggerLabel, type AutomationRun, type ProjectAutomation } from '../../types/automation'
import { CreateRuleDialog, EditRuleDialog } from './automation/RuleDialogs'

export type AutomationView = 'rules' | 'runs'

interface RuleRow extends ProjectAutomation {
  projectName: string
}

/** V3 Automation: guided Rules, and the Run History of every rule with a readable result. */
export function AutomationPage({ view }: { view: AutomationView }) {
  const projects = useAllProjects()
  const projectList = useMemo(() => (projects.data?.content ?? null)?.filter((p) => !p.archived) ?? null, [projects.data])
  const rules = usePerProject(projectList, (id) => automationsApi.list(id))
  const [createOpen, setCreateOpen] = useState(false)

  const rows = useMemo<RuleRow[]>(() => {
    const names = new Map((projectList ?? []).map((p) => [p.id, p.name]))
    return Object.entries(rules.byProject)
      .flatMap(([projectId, list]) => (list ?? []).filter((r) => !r.archived).map((r) => ({ ...r, projectName: names.get(projectId) ?? 'Project' })))
      .sort((a, b) => a.name.localeCompare(b.name))
  }, [rules.byProject, projectList])

  const loading = projects.loading || ((projectList?.length ?? 0) > 0 && rules.loading)

  return (
    <div className="ops-page ops-section-page">
      <header className="ops-page-head">
        <div>
          <h1>{view === 'rules' ? 'Automation Rules' : 'Run History'}</h1>
          <p>
            {view === 'rules'
              ? 'Simple rules that notify the right person when work changes.'
              : 'Every time a rule ran, whether it worked, and why not when it did not.'}
          </p>
        </div>
        {view === 'rules' && (
          <div className="ops-head-actions">
            <button type="button" className="ops-primary" onClick={() => setCreateOpen(true)} disabled={!projectList || projectList.length === 0}>
              <Icon name="plus" size={22} /> New Rule
            </button>
          </div>
        )}
      </header>

      {projects.error ? (
        <ErrorState message={projects.error} onRetry={projects.reload} />
      ) : view === 'rules' ? (
        <RulesView rows={rows} loading={loading} hasProjects={(projectList?.length ?? 0) > 0} onChanged={rules.reload} />
      ) : (
        <RunsView rows={rows} loading={loading} />
      )}

      {createOpen && projectList && (
        <CreateRuleDialog projects={projectList} onClose={() => setCreateOpen(false)} onCreated={rules.reload} />
      )}
    </div>
  )
}

function Tile({ icon, tone, label, value }: { icon: IconName; tone: string; label: string; value: number }) {
  return (
    <div className="ops-tile">
      <span className={`ops-tile-icon tone-${tone}`}>
        <Icon name={icon} size={30} />
      </span>
      <span className="ops-tile-text">
        <span className="ops-tile-label">{label}</span>
        <strong className="ops-tile-value">{value}</strong>
      </span>
    </div>
  )
}

function RulesView({ rows, loading, hasProjects, onChanged }: { rows: RuleRow[]; loading: boolean; hasProjects: boolean; onChanged: () => void }) {
  const { nameOf } = usePeople()
  const [editing, setEditing] = useState<RuleRow | null>(null)
  const [deleting, setDeleting] = useState<RuleRow | null>(null)
  const [error, setError] = useState<string | null>(null)
  const on = rows.filter((r) => r.enabled).length

  async function toggle(rule: RuleRow) {
    setError(null)
    try {
      await automationsApi.update(rule.id, { enabled: !rule.enabled, version: rule.version })
      onChanged()
    } catch (err) {
      setError(toUserMessage(err))
    }
  }

  return (
    <>
      <section className="ops-stats ops-stats-3" aria-label="Rule counts">
        <Tile icon="bolt" tone="green" label="Rules" value={rows.length} />
        <Tile icon="play" tone="blue" label="On" value={on} />
        <Tile icon="pause" tone="orange" label="Off" value={rows.length - on} />
      </section>

      <p className="ops-notice">
        <Icon name="bell" size={18} /> Rules record every run in Run History. Delivering the notification to the person&apos;s bell
        arrives with the notifications update.
      </p>

      {error && <InlineError message={error} />}

      <section className="ops-card ops-table-card">
        <div className="ops-table-scroll">
          <table className="ops-table">
            <thead>
              <tr>
                <th>Rule</th>
                <th>Project</th>
                <th>When</th>
                <th>Then</th>
                <th>Status</th>
                <th className="ops-th-actions">Actions</th>
              </tr>
            </thead>
            <tbody>
              {loading && rows.length === 0 && (
                <tr>
                  <td colSpan={6} className="ops-empty">Loading rules…</td>
                </tr>
              )}
              {!loading && rows.length === 0 && (
                <tr>
                  <td colSpan={6} className="ops-empty">
                    {hasProjects ? 'No rules yet. Click New Rule to create the first one.' : 'Create a project first; rules watch a project.'}
                  </td>
                </tr>
              )}
              {rows.map((r) => (
                <tr key={r.id}>
                  <td>
                    <strong>{r.name}</strong>
                  </td>
                  <td>
                    <Link to={`/projects/${r.projectId}/overview`}>{r.projectName}</Link>
                  </td>
                  <td>{triggerLabel(r.triggerEvent)}</td>
                  <td>Notify {r.actionRecipientId ? nameOf(r.actionRecipientId) : 'someone'}</td>
                  <td>
                    <button type="button" className={`ops-switch${r.enabled ? ' on' : ''}`} role="switch" aria-checked={r.enabled} aria-label={`${r.name} is ${r.enabled ? 'on' : 'off'}`} onClick={() => toggle(r)}>
                      <span />
                      {r.enabled ? 'On' : 'Off'}
                    </button>
                  </td>
                  <td className="ops-actions">
                    <button type="button" className="ops-link-btn" onClick={() => setEditing(r)}>
                      Edit
                    </button>
                    <button type="button" className="ops-link-btn danger" onClick={() => setDeleting(r)}>
                      Delete
                    </button>
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
        <footer className="ops-table-foot">
          <span>
            {rows.length} {rows.length === 1 ? 'rule' : 'rules'} across your projects. See what they did in{' '}
            <Link to="/automations/runs">Run History</Link>.
          </span>
        </footer>
      </section>

      {editing && <EditRuleDialog rule={editing} onClose={() => setEditing(null)} onSaved={onChanged} />}
      {deleting && (
        <ConfirmDialog
          title="Delete rule"
          message={`Delete the rule "${deleting.name}"? It stops running straight away.`}
          confirmLabel="Delete"
          onConfirm={async () => {
            await automationsApi.archive(deleting.id)
            onChanged()
          }}
          onClose={() => setDeleting(null)}
        />
      )}
    </>
  )
}

interface RunRow extends AutomationRun {
  ruleName: string
  projectName: string
}

function RunsView({ rows, loading }: { rows: RuleRow[]; loading: boolean }) {
  const ruleKey = rows.map((r) => r.id).join(',')
  // One request per rule; a rule whose history cannot load is skipped rather than blanking the page.
  const runs = useAsyncData(
    () =>
      Promise.allSettled(rows.map((r) => automationsApi.runs(r.id))).then((results) =>
        results.flatMap((res, i) =>
          res.status === 'fulfilled' ? res.value.map((run) => ({ ...run, ruleName: rows[i].name, projectName: rows[i].projectName })) : [],
        ),
      ),
    // eslint-disable-next-line react-hooks/exhaustive-deps
    [ruleKey],
  )
  const [result, setResult] = useState('')

  const all = useMemo<RunRow[]>(
    () => [...(runs.data ?? [])].sort((a, b) => b.executedAt.localeCompare(a.executedAt)),
    [runs.data],
  )

  const shown = all.filter((r) => !result || r.status === result).slice(0, 100)
  const failed = all.filter((r) => r.status === 'FAILED').length
  const busy = loading || runs.loading

  return (
    <>
      <section className="ops-stats ops-stats-3" aria-label="Run counts">
        <Tile icon="playCircle" tone="blue" label="Runs" value={all.length} />
        <Tile icon="checkCircle" tone="green" label="Succeeded" value={all.length - failed} />
        <Tile icon="warning" tone="red" label="Failed" value={failed} />
      </section>

      <div className="ops-filters ops-filters-1" role="search" aria-label="Filter runs">
        <label className="ops-select">
          <select aria-label="Result" value={result} onChange={(e) => setResult(e.target.value)}>
            <option value="">All Results</option>
            <option value="SUCCEEDED">Succeeded</option>
            <option value="FAILED">Failed</option>
          </select>
          <Icon name="chevronDown" size={18} className="ops-select-chevron" />
        </label>
      </div>

      <section className="ops-card ops-table-card">
        <div className="ops-table-scroll">
          <table className="ops-table">
            <thead>
              <tr>
                <th>When</th>
                <th>Rule</th>
                <th>Project</th>
                <th>Started by</th>
                <th>Result</th>
                <th>Details</th>
              </tr>
            </thead>
            <tbody>
              {busy && all.length === 0 && (
                <tr>
                  <td colSpan={6} className="ops-empty">Loading run history…</td>
                </tr>
              )}
              {!busy && shown.length === 0 && (
                <tr>
                  <td colSpan={6} className="ops-empty">{all.length === 0 ? 'No rule has run yet.' : 'No runs match this filter.'}</td>
                </tr>
              )}
              {shown.map((r) => (
                <tr key={r.id}>
                  <td>{formatDateTime(r.executedAt)}</td>
                  <td>
                    <strong>{r.ruleName}</strong>
                  </td>
                  <td>{r.projectName}</td>
                  <td>{triggerLabel(r.triggerEvent)}</td>
                  <td>
                    <span className={`ops-status ${r.status === 'SUCCEEDED' ? 'status-active' : 'status-failed'}`}>
                      {r.status === 'SUCCEEDED' ? 'Succeeded' : 'Failed'}
                    </span>
                  </td>
                  <td className="ops-cell-wrap">{r.status === 'FAILED' ? r.errorMessage ?? 'The rule could not finish.' : 'The rule ran.'}</td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
        <footer className="ops-table-foot">
          <span>{all.length > 100 ? `Showing the latest 100 of ${all.length} runs.` : `${all.length} ${all.length === 1 ? 'run' : 'runs'}, newest first.`}</span>
        </footer>
      </section>
    </>
  )
}
