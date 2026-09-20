import { describe, expect, it } from 'vitest'
import appSource from '../App.tsx?raw'
import { FEATURES, advancedFeatures, coreFeatures, featureHref, groupByCategory } from './registry'
import { resolveFeatureAccess } from './featureState'

const registeredRoutes = new Set(
  [...appSource.matchAll(/<Route path="([^"]+)"/g)].map((m) => m[1]),
)

describe('feature registry', () => {
  it('has unique ids', () => {
    const ids = FEATURES.map((f) => f.id)
    expect(new Set(ids).size).toBe(ids.length)
  })

  it('only references routes that actually exist in App.tsx (no invented routes)', () => {
    for (const feature of FEATURES) {
      const segment = feature.route.replace(/^\//, '')
      expect(registeredRoutes.has(segment), `route "${feature.route}" for ${feature.id}`).toBe(true)
    }
  })

  it('keeps the primary navigation bounded: the 9-item workspace sidebar of the reference design, at most 5 core project tabs', () => {
    // Workspace: Home, Projects, My Tasks, Calendar, My Team, Reports, Templates, Automations, Settings.
    expect(coreFeatures('workspace').map((f) => f.name)).toEqual([
      'Home', 'Projects', 'My Tasks', 'Calendar', 'My Team', 'Reports', 'Templates', 'Automations', 'Settings',
    ])
    expect(coreFeatures('project').length).toBeLessThanOrEqual(5)
  })

  it('exposes every backend-backed capability somewhere (core or advanced)', () => {
    const all = [...coreFeatures('workspace'), ...coreFeatures('project'), ...advancedFeatures('workspace'), ...advancedFeatures('project')]
    expect(all.length).toBe(FEATURES.length)
    for (const id of ['tasks', 'kanban', 'gantt', 'risks', 'automations', 'ai', 'templates', 'custom-fields', 'search']) {
      expect(all.some((f) => f.id === id), id).toBe(true)
    }
  })

  it('groups advanced project features into the documented categories, in order', () => {
    const groups = groupByCategory(advancedFeatures('project'))
    expect(groups.map(([c]) => c)).toEqual([
      'Advanced planning',
      'Project control',
      'Productivity',
      'Automation & intelligence',
    ])
  })

  it('builds absolute hrefs for project features', () => {
    const gantt = FEATURES.find((f) => f.id === 'gantt')!
    expect(featureHref(gantt, 'p1')).toBe('/projects/p1/gantt')
    const home = FEATURES.find((f) => f.id === 'home')!
    expect(featureHref(home)).toBe('/dashboard')
  })
})

describe('resolveFeatureAccess', () => {
  const automations = FEATURES.find((f) => f.id === 'automations')!
  const activity = FEATURES.find((f) => f.id === 'activity')!
  const ai = FEATURES.find((f) => f.id === 'ai')!

  it('is REQUIRES_PERMISSION when the role lacks the needed permission', () => {
    expect(resolveFeatureAccess(automations, { can: () => false })).toBe('REQUIRES_PERMISSION')
  })

  it('is AVAILABLE when the role has the permission', () => {
    expect(resolveFeatureAccess(automations, { can: () => true })).toBe('AVAILABLE')
  })

  it('is COMING_SOON for features without a backend API, regardless of role', () => {
    expect(resolveFeatureAccess(activity, { can: () => true })).toBe('COMING_SOON')
  })

  it('reports BETA for AI (provider may be unconfigured) once permission is satisfied', () => {
    expect(resolveFeatureAccess(ai, { can: () => true })).toBe('BETA')
  })

  it('does not guess permission when the role is unknown', () => {
    expect(resolveFeatureAccess(automations, { can: null })).toBe('AVAILABLE')
  })
})
