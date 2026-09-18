import { useState } from 'react'
import { Link } from 'react-router-dom'
import { useOptionalProjectWorkspace } from '../../context/ProjectWorkspaceContext'
import { advancedFeatures, featureHref, groupByCategory, type FeatureDefinition } from '../../features/registry'
import { ACCESS_LABELS, resolveFeatureAccess, type FeatureAccess } from '../../features/featureState'
import { Icon } from '../../components/common/Icon'
import { Badge, type BadgeTone } from '../../components/common/Badge'
import { PageHeader } from '../../components/common/PageHeader'
import { Modal } from '../../components/common/Modal'

const ACCESS_TONE: Record<FeatureAccess, BadgeTone> = {
  AVAILABLE: 'success',
  BETA: 'info',
  REQUIRES_PERMISSION: 'warning',
  COMING_SOON: 'neutral',
}

/**
 * The "More" hub: every ADVANCED feature from the registry, grouped by category, each with an
 * honest access state. Rendered both at /more (workspace features) and inside a project at
 * /projects/:id/more (project features).
 */
export function AdvancedFeaturesPage() {
  const workspace = useOptionalProjectWorkspace()
  const [requesting, setRequesting] = useState<FeatureDefinition | null>(null)

  const scope = workspace ? 'project' : 'workspace'
  const groups = groupByCategory(advancedFeatures(scope))
  const can = workspace && workspace.currentMember ? workspace.can : null

  return (
    <div>
      <PageHeader
        title="More"
        description={
          workspace
            ? 'Advanced tools for this project. Some may require additional permissions.'
            : 'Workspace-wide tools. Open a project to see its advanced features.'
        }
      />

      {groups.map(([category, features]) => (
        <section className="feature-group" key={category} aria-labelledby={`group-${category}`}>
          <h2 id={`group-${category}`} className="feature-group-title">
            {category}
          </h2>
          <div className="feature-grid">
            {features.map((feature) => {
              const access = resolveFeatureAccess(feature, { can })
              return (
                <FeatureCard
                  key={feature.id}
                  feature={feature}
                  access={access}
                  href={featureHref(feature, workspace?.project.id)}
                  onRequestAccess={() => setRequesting(feature)}
                />
              )
            })}
          </div>
        </section>
      ))}

      {requesting && <RequestAccessDialog feature={requesting} onClose={() => setRequesting(null)} />}
    </div>
  )
}

function FeatureCard({
  feature,
  access,
  href,
  onRequestAccess,
}: {
  feature: FeatureDefinition
  access: FeatureAccess
  href: string
  onRequestAccess: () => void
}) {
  const openable = access !== 'COMING_SOON'
  return (
    <article className={`feature-card feature-${access.toLowerCase()}`}>
      <div className="feature-card-icon">
        <Icon name={feature.icon} size={20} />
      </div>
      <div className="feature-card-body">
        <div className="feature-card-title-row">
          <h3>{feature.name}</h3>
          <Badge tone={ACCESS_TONE[access]}>{ACCESS_LABELS[access]}</Badge>
        </div>
        {feature.technicalName && feature.technicalName !== feature.name && (
          <div className="feature-card-technical">{feature.technicalName}</div>
        )}
        <p>{feature.description}</p>
        <div className="feature-card-actions">
          {openable && (
            <Link className="btn btn-sm" to={href}>
              Open
            </Link>
          )}
          {access === 'REQUIRES_PERMISSION' && (
            <button type="button" className="btn btn-sm btn-ghost" onClick={onRequestAccess}>
              Request access
            </button>
          )}
          {access === 'COMING_SOON' && (
            <span className="text-faint">Not available in this version yet.</span>
          )}
        </div>
      </div>
    </article>
  )
}

function RequestAccessDialog({ feature, onClose }: { feature: FeatureDefinition; onClose: () => void }) {
  return (
    <Modal title={`Access to ${feature.name}`} onClose={onClose}>
      <p>
        You can open <strong>{feature.name}</strong> to view it, but your role on this project
        can't use its editing actions. Your project owner or a manager needs to change your role
        under <strong>Team</strong>.
      </p>
      <p className="text-muted">
        There is no in-app access-request workflow yet — this backend has no feature-request
        API, so nothing is sent automatically. Contact your project owner directly.
      </p>
      <div className="form-actions">
        <button type="button" className="btn btn-primary" onClick={onClose}>
          Got it
        </button>
      </div>
    </Modal>
  )
}
