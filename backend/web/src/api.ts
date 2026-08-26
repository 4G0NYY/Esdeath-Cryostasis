/**
 * The two reads the live panel makes, against the same origin that served this page.
 *
 * Both are open endpoints (the backend authenticates writes, not reads), so nothing here holds a
 * token and nothing needs CORS. A failure is a returned null rather than a thrown error: the
 * panel degrades to "backend unreachable" and the rest of the page is unaffected, which is the
 * behaviour that matters when the site is served by the very service it is reporting on.
 */

export type PresenceState = 'online' | 'afk' | 'offline'

export interface PresencePlayer {
  uuid: string
  username: string
  rank: string
  color: string
  state: PresenceState
  status: string
  server: string
}

export interface Roster {
  players: PresencePlayer[]
  count: number
  online: number
  afk: number
}

async function read<T>(path: string, signal: AbortSignal): Promise<T | null> {
  try {
    const response = await fetch(path, { signal, headers: { Accept: 'application/json' } })
    return response.ok ? ((await response.json()) as T) : null
  } catch {
    return null
  }
}

export function fetchVersion(signal: AbortSignal): Promise<{ version: string } | null> {
  return read<{ version: string }>('/api/version', signal)
}

export function fetchRoster(signal: AbortSignal): Promise<Roster | null> {
  return read<Roster>('/api/players/presence', signal)
}
