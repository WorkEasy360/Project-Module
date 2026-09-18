const STORAGE_KEY = 'projectmodule.sidebarCollapsed'

function readStored(): boolean {
  try {
    return window.localStorage.getItem(STORAGE_KEY) === '1'
  } catch {
    return false
  }
}

let collapsed = readStored()
const listeners = new Set<() => void>()

export function getCollapsed(): boolean {
  return collapsed
}

export function setCollapsed(next: boolean): void {
  collapsed = next
  try {
    window.localStorage.setItem(STORAGE_KEY, next ? '1' : '0')
  } catch {
    // localStorage may be unavailable (private browsing) — collapse still works in-memory.
  }
  for (const listener of listeners) listener()
}

export function subscribeCollapsed(listener: () => void): () => void {
  listeners.add(listener)
  return () => listeners.delete(listener)
}
