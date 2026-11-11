import { createContext, useContext, useMemo, type ReactNode } from 'react'
import type { Account } from '../api/auth'
import { workspaceApi } from '../api/workspace'
import { useAsyncData } from '../hooks/useAsyncData'
import { useIdentity } from './IdentityContext'

interface PeopleContextValue {
  people: Account[]
  /** A person's name. Someone with no account, from before sign-in existed, shows a short id. */
  nameOf: (userId: string) => string
  isAdmin: boolean
  reload: () => void
}

const FALLBACK: PeopleContextValue = {
  people: [],
  nameOf: (userId) => userId.slice(0, 8),
  isAdmin: false,
  reload: () => undefined,
}

const PeopleContext = createContext<PeopleContextValue>(FALLBACK)

/** Loads the workspace's people once, so any screen can show a name instead of an id. */
export function PeopleProvider({ children }: { children: ReactNode }) {
  const { identity } = useIdentity()
  const { data, reload } = useAsyncData(() => workspaceApi.people(), [identity?.userId])

  const value = useMemo<PeopleContextValue>(() => {
    const people = data ?? []
    const byId = new Map(people.map((person) => [person.id, person]))
    return {
      people,
      nameOf: (userId) => byId.get(userId)?.name ?? FALLBACK.nameOf(userId),
      isAdmin: identity ? byId.get(identity.userId)?.workspaceRole === 'ADMIN' : false,
      reload,
    }
  }, [data, identity, reload])

  return <PeopleContext.Provider value={value}>{children}</PeopleContext.Provider>
}

export function usePeople(): PeopleContextValue {
  return useContext(PeopleContext)
}
