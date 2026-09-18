import { EmptyState } from '../../../components/common/EmptyState'

export function ActivityTab() {
  return (
    <EmptyState
      title="Activity feed is not available"
      description="The backend records project activity internally (ProjectActivity) for audit purposes, but does not currently expose a REST endpoint for it. This screen will populate automatically once that API exists — nothing is simulated here."
    />
  )
}
