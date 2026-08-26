import { useEffect, useState } from 'react'
import { fetchRoster, fetchVersion, type PresenceState, type Roster } from '../api'

/** Matches the backend's presence window, so the page is never more stale than the data is. */
const REFRESH_MS = 30_000

const STATE_LABEL: Record<PresenceState, string> = {
  online: 'Online',
  afk: 'Away',
  offline: 'Offline',
}

/**
 * Who is on the client right now, read from the backend that served this page.
 *
 * It exists to be evidence rather than decoration: the same presence the in-game roster shows,
 * from the same endpoint, so the page proves the service behind it is up instead of asserting it.
 * An unreachable backend says so plainly; it never shows a zero that could be mistaken for a
 * quiet evening.
 */
export function LivePanel() {
  const [version, setVersion] = useState<string | null>(null)
  const [roster, setRoster] = useState<Roster | null>(null)
  const [reachable, setReachable] = useState<boolean | null>(null)

  useEffect(() => {
    const controller = new AbortController()

    async function load() {
      const [v, r] = await Promise.all([
        fetchVersion(controller.signal),
        fetchRoster(controller.signal),
      ])
      if (controller.signal.aborted) {
        return
      }
      setVersion(v?.version ?? null)
      setRoster(r)
      // The version endpoint is the health signal: presence can legitimately be empty, so an
      // empty roster must not read as an outage.
      setReachable(v !== null)
    }

    load()
    const timer = window.setInterval(load, REFRESH_MS)
    return () => {
      controller.abort()
      window.clearInterval(timer)
    }
  }, [])

  const named = (roster?.players ?? []).filter((p) => p.username.length > 0)

  return (
    <section id="live">
      <div className="shell">
        <p className="eyebrow">Live</p>
        <h2>Who is on right now</h2>
        <p className="lede">
          Read from this backend as you loaded the page. Presence is derived from each client's
          heartbeat rather than stored, so a crashed client drops off on its own and a player who
          has stopped touching anything shows as away instead of vanishing.
        </p>

        {reachable === false ? (
          <div className="caveat" style={{ marginTop: 30 }}>
            <p>
              The backend did not answer, so there is nothing live to show. The rest of this page
              is static and unaffected.
            </p>
          </div>
        ) : (
          <>
            <div className="stats">
              <div className="stat online">
                <div className="value">{roster ? roster.online : '—'}</div>
                <div className="name">Playing</div>
              </div>
              <div className="stat afk">
                <div className="value">{roster ? roster.afk : '—'}</div>
                <div className="name">Away</div>
              </div>
              <div className="stat">
                <div className="value">{roster ? roster.count : '—'}</div>
                <div className="name">Clients connected</div>
              </div>
              <div className="stat">
                <div className="value mono" style={{ fontSize: '1.2rem', paddingTop: 8 }}>
                  {version ?? '—'}
                </div>
                <div className="name">Backend version</div>
              </div>
            </div>

            {named.length > 0 && (
              <div className="roster">
                {named.map((player) => (
                  <div className="roster-row" key={player.uuid}>
                    <span className={`dot ${player.state}`} />
                    <span className="who">{player.username}</span>
                    {player.rank !== 'Default' && (
                      <span className="rank" style={{ color: player.color }}>
                        {player.rank}
                      </span>
                    )}
                    <span className="status">
                      {player.status || STATE_LABEL[player.state]}
                      {player.server ? ` · ${player.server}` : ''}
                    </span>
                  </div>
                ))}
              </div>
            )}

            <p className="note">
              {named.length > 0
                ? 'Names come from the Mojang session handshake each client completes, never from what a client claims to be called.'
                : 'Nobody is connected at the moment. Names appear here once a client signs in.'}
            </p>
          </>
        )}
      </div>
    </section>
  )
}
