import { createContext, useCallback, useContext, useEffect, useMemo, useState, type ReactNode } from 'react'
import { workspaceApi, type WorkspaceSettings } from '../api/workspace'

interface WorkspaceSettingsValue {
  settings: WorkspaceSettings | null
  rename: (name: string) => Promise<void>
}

const FALLBACK: WorkspaceSettings = {
  name: 'My Workspace',
  canEdit: false,
  plan: { name: 'Advanced', validUntil: null, manageUrl: 'https://workeasy360.com' },
  supportEmail: 'support@workeasy360.com',
}

const Ctx = createContext<WorkspaceSettingsValue>({ settings: null, rename: async () => {} })

/** The workspace's name, plan and support contact, loaded once for the app shell. */
export function WorkspaceSettingsProvider({ children }: { children: ReactNode }) {
  const [settings, setSettings] = useState<WorkspaceSettings | null>(null)

  useEffect(() => {
    let alive = true
    workspaceApi
      .settings()
      .then((s) => alive && setSettings(s))
      .catch(() => alive && setSettings(FALLBACK))
    return () => {
      alive = false
    }
  }, [])

  const rename = useCallback(async (name: string) => {
    setSettings(await workspaceApi.rename(name))
  }, [])

  const value = useMemo(() => ({ settings, rename }), [settings, rename])
  return <Ctx.Provider value={value}>{children}</Ctx.Provider>
}

export function useWorkspaceSettings(): WorkspaceSettingsValue {
  return useContext(Ctx)
}
