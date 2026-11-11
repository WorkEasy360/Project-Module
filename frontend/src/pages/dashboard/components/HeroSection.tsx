import { Link } from 'react-router-dom'
import { Icon } from '../../../components/common/Icon'
import { TaskStatusBadge } from '../../../components/common/Badge'
import { openAssistant } from '../../../components/assistant/AssistantPanel'
import { formatDate } from '../../../utils/format'
import type { NextStep } from '../dashboardUtils'

/** Welcome, and the single task most worth doing next, with the reason it was chosen. */
export function HeroSection({ greeting, name, nextStep }: { greeting: string; name: string | null; nextStep?: NextStep | null }) {
  return (
    <section className="hb-hero" aria-labelledby="hb-hero-title">
      <div className="hb-hero-text">
        <h1 id="hb-hero-title">
          {greeting}
          {name ? `, ${name}` : ''}!{' '}
          <span aria-hidden="true">👋</span>
        </h1>
        <p>Here&apos;s what&apos;s happening across your projects today.</p>
      </div>
      {nextStep ? (
        <div className="hb-next">
          <span className="hb-next-icon" aria-hidden="true">
            <Icon name="tasks" size={22} />
          </span>
          <div className="hb-next-main">
            <span className="hb-next-kicker">Your next step</span>
            <strong className="hb-next-title">{nextStep.task.name}</strong>
            <span className="hb-next-meta">
              <TaskStatusBadge status={nextStep.task.status} />
              <span>{nextStep.projectName}</span>
              {nextStep.task.dueDate && (
                <span>
                  <Icon name="calendar" size={13} /> {formatDate(nextStep.task.dueDate)}
                </span>
              )}
            </span>
            <span className="hb-next-why">
              <strong>Why:</strong> {nextStep.reason}
            </span>
          </div>
          <Link className="hb-next-go" to={`/projects/${nextStep.task.projectId}/tasks/${nextStep.task.id}`}>
            <Icon name="arrowRight" size={16} /> Open task
          </Link>
        </div>
      ) : (
        <div className="hb-next">
          <span className="hb-next-icon" aria-hidden="true">
            <Icon name="check" size={22} />
          </span>
          <div className="hb-next-main">
            <span className="hb-next-kicker">Your next step</span>
            <strong className="hb-next-title">You're all caught up</strong>
            <span className="hb-next-why">Nothing is open right now. Plan something new, or ask the assistant.</span>
          </div>
          <button type="button" className="hb-next-go" onClick={() => openAssistant()}>
            <Icon name="ai" size={16} /> Ask the assistant
          </button>
        </div>
      )}
    </section>
  )
}
