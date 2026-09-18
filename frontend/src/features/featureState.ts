import type { FeatureDefinition } from './registry'
import type { ProjectPermission } from '../utils/permissions'

/**
 * What the current user can do with a feature right now.
 *
 * AVAILABLE           – usable now.
 * BETA                – usable, but the backend marks it partial (AI provider may be unconfigured).
 * REQUIRES_PERMISSION – the page can be opened read-only, but the user's project role can't use
 *                       its primary actions. There is no backend "request access" API yet, so the
 *                       UI only explains who can grant it.
 * COMING_SOON         – no backend API exists yet.
 */
export type FeatureAccess = 'AVAILABLE' | 'BETA' | 'REQUIRES_PERMISSION' | 'COMING_SOON'

export interface FeatureAccessContext {
  /** Whether the user's project role is known (null outside a project or when not a member). */
  can: ((permission: ProjectPermission) => boolean) | null
}

export function resolveFeatureAccess(feature: FeatureDefinition, ctx: FeatureAccessContext): FeatureAccess {
  if (feature.releaseState === 'COMING_SOON') return 'COMING_SOON'
  if (feature.requiredPermission && ctx.can && !ctx.can(feature.requiredPermission)) {
    return 'REQUIRES_PERMISSION'
  }
  if (feature.releaseState === 'BETA') return 'BETA'
  return 'AVAILABLE'
}

export const ACCESS_LABELS: Record<FeatureAccess, string> = {
  AVAILABLE: 'Available',
  BETA: 'Beta',
  REQUIRES_PERMISSION: 'Requires permission',
  COMING_SOON: 'Coming soon',
}
