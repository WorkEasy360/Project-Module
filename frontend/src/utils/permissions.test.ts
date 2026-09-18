import { describe, expect, it } from 'vitest'
import { hasPermission, permissionsForRole } from './permissions'

describe('permissionsForRole', () => {
  it('grants a VIEWER only VIEW_PROJECT', () => {
    expect(hasPermission('VIEWER', 'VIEW_PROJECT')).toBe(true)
    expect(hasPermission('VIEWER', 'EDIT_PROJECT')).toBe(false)
    expect(hasPermission('VIEWER', 'MANAGE_MEMBERS')).toBe(false)
    expect(hasPermission('VIEWER', 'ARCHIVE_PROJECT')).toBe(false)
  })

  it('grants a MEMBER only VIEW_PROJECT', () => {
    expect(hasPermission('MEMBER', 'VIEW_PROJECT')).toBe(true)
    expect(hasPermission('MEMBER', 'EDIT_PROJECT')).toBe(false)
  })

  it('grants a MANAGER VIEW/EDIT/MANAGE_MEMBERS but not ARCHIVE_PROJECT', () => {
    expect(hasPermission('MANAGER', 'VIEW_PROJECT')).toBe(true)
    expect(hasPermission('MANAGER', 'EDIT_PROJECT')).toBe(true)
    expect(hasPermission('MANAGER', 'MANAGE_MEMBERS')).toBe(true)
    expect(hasPermission('MANAGER', 'ARCHIVE_PROJECT')).toBe(false)
  })

  it('grants an OWNER every permission', () => {
    expect(permissionsForRole('OWNER').size).toBe(4)
    expect(hasPermission('OWNER', 'ARCHIVE_PROJECT')).toBe(true)
  })

  it('grants no permissions when no role is known', () => {
    expect(permissionsForRole(null).size).toBe(0)
    expect(hasPermission(null, 'VIEW_PROJECT')).toBe(false)
  })
})
