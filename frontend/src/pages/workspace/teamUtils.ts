import type { BadgeTone } from '../../components/common/Badge'
import type { ProjectMember, ProjectRole, ProjectSummary } from '../../types/project'

/** One membership row: a member record together with the project it belongs to. */
export interface Membership {
  project: ProjectSummary
  member: ProjectMember
}

/** A person is just a user id — this backend has no user directory (no names, no emails). */
export interface Person {
  userId: string
  memberships: Membership[]
  isMe: boolean
}

export interface TeamFilters {
  query: string
  role: ProjectRole | ''
}

export const ROLE_TONE: Record<ProjectRole, BadgeTone> = {
  OWNER: 'primary',
  MANAGER: 'info',
  MEMBER: 'neutral',
  VIEWER: 'neutral',
}

/** First 8 characters of a user id — the only "name" this backend can give a person. */
export function shortId(id: string): string {
  return id.slice(0, 8)
}

export function avatarText(id: string): string {
  return id.slice(0, 2).toUpperCase()
}

/** Flattens the per-project member lists (skipping projects that failed or are still loading). */
export function collectMemberships(
  projects: ProjectSummary[],
  byProject: Record<string, ProjectMember[] | null>,
): Membership[] {
  const out: Membership[] = []
  for (const project of projects) {
    for (const member of byProject[project.id] ?? []) out.push({ project, member })
  }
  return out
}

export function filterMemberships(items: Membership[], filters: TeamFilters): Membership[] {
  const q = filters.query.trim().toLowerCase()
  return items.filter(
    ({ project, member }) =>
      (filters.role === '' || member.role === filters.role) &&
      (q === '' || member.userId.toLowerCase().includes(q) || project.name.toLowerCase().includes(q)),
  )
}

/** Groups memberships by user id: the current user first, then by project count, then by id. */
export function groupPeople(items: Membership[], meId: string | undefined): Person[] {
  const byUser = new Map<string, Membership[]>()
  for (const m of items) {
    const list = byUser.get(m.member.userId)
    if (list) list.push(m)
    else byUser.set(m.member.userId, [m])
  }
  return [...byUser.entries()]
    .map(([userId, memberships]) => ({
      userId,
      memberships: [...memberships].sort((a, b) => a.project.name.localeCompare(b.project.name)),
      isMe: meId !== undefined && userId === meId,
    }))
    .sort((a, b) => {
      if (a.isMe !== b.isMe) return a.isMe ? -1 : 1
      if (a.memberships.length !== b.memberships.length) return b.memberships.length - a.memberships.length
      return a.userId.localeCompare(b.userId)
    })
}

export interface TeamSummary {
  people: number
  projects: number
  owners: number
  managers: number
}

/** Distinct people / projects with a loaded member list / distinct owners and managers. */
export function teamSummary(items: Membership[]): TeamSummary {
  const people = new Set<string>()
  const projects = new Set<string>()
  const owners = new Set<string>()
  const managers = new Set<string>()
  for (const { project, member } of items) {
    people.add(member.userId)
    projects.add(project.id)
    if (member.role === 'OWNER') owners.add(member.userId)
    if (member.role === 'MANAGER') managers.add(member.userId)
  }
  return { people: people.size, projects: projects.size, owners: owners.size, managers: managers.size }
}
