# Backend Protocol: Recovered Shape and Modern Redesign

The original EsdeathClient backend is gone (`cwbwtraining.de` no longer serves it), so
Phase 3 is a fresh design. This document records the recovered wire protocol so the new
service preserves the same client-visible behavior, then proposes the REST API the rebuild
will target.

## 1. Recovered transport

Raw TCP sockets, not HTTP. Every call opened a fresh `java.net.Socket`, wrapped it in
`DataOutputStream` / `DataInputStream`, did a single `writeUTF(command)` and optionally one
`readUTF()`, then closed the socket. One socket per request, no persistent connection, no
framing beyond Java modified-UTF length prefixes.

- Host: `cwbwtraining.de` (the only hostname constant in the client).
- Data port: `1333` (version, cosmetics, ranks, status, presence).
- Chat port: `1879` (chat relay only).
- Wire content was plaintext. The Blowfish and DES routines in the client only
  deobfuscated string constants at load time; they were never applied to socket payloads.
- No authentication or handshake. Identity was carried inline in each command as the
  player UUID or name. Startup did a reachability plus version check (`getVersion` compared
  against the local `3.8.6`) and announced presence with `addMe` / `ImOnServer`.

Three socket primitives were used: a request/response `get` (write then read, on port
1333), a fire-and-forget `info` (write only), and a `chat` (write then read, on port 1879).
Calls were gated on the `Connector` module being enabled and the client being connected.

## 2. Recovered command vocabulary

Each command is a single UTF string with space- or colon-separated arguments.

| Wire command | Direction | Purpose |
|---|---|---|
| `getVersion` | read | Server version, compared to the local client version |
| `getAllCapes` | read | List of all cape names (each roughly `name-rarity.png`) |
| `addMe <name>` | write only | Register the player as online |
| `ImOnServer <server> <uuid>` | write only | Register which server the player is on |
| `setMyPlayerStatus <uuid> <status>` | write only | Set the player's status string |
| `getTheStatusOfThePlayer <uuid>` | read | Fetch a player's status (async, cached) |
| `getRankofPlayer <uuid>` | read | Player rank (Premium, Epic, Chef, and so on) |
| `getPlayersOnServer <server>` | read | Comma-separated player list on a server |
| `getOnlinePlayingPlayers` | read | Comma-separated list of all online Esdeath users |
| `addMeACosmetic <uuid> <cosmetic>` | read | Grant a cosmetic to the player, returns boolean |
| `removeMeACosmetic <uuid> <cosmetic>` | read | Revoke a cosmetic, returns boolean |
| `setMyCape <uuid>:<cape>` | write only | Set the player's active cape |
| `hasThePlayerTheCosmetic <uuid>:<cosmetic>` | read | Whether a player owns a cosmetic (async, cached) |
| `getMSG` | read (port 1879) | Latest global chat line, deduped against the last seen |
| `sendMSG <name> <message>` | read (port 1879) | Send a global chat message |

Certainty notes: opcodes such as `getAllCapes`, `getMSG`, `sendMSG`, `addMe`, `ImOnServer`,
`addMeACosmetic`, `getRankofPlayer`, `getTheStatusOfThePlayer`, `hasThePlayerTheCosmetic`
decrypted cleanly. `removeMeACosmetic` and the cape-set opcode were recovered from a noisy
second cipher layer, so their exact spelling is about 90 percent certain while their
semantics are certain. The new API does not depend on the old spelling.

## 3. Data the backend must model

From the command set, the backend tracks per player (keyed by Minecraft UUID):
- Owned cosmetics (a set), and the currently active cape.
- A rank or permission tier that gates cape rarity and status editing.
- A free-text status string.
- Presence: whether online, and which game server they are on.
- A global chat channel (broadcast, with basic dedup).

Cosmetic ownership is server-authoritative; the client caches per `uuid:cosmetic` and
refreshes asynchronously.

## 4. Proposed modern REST redesign (Phase 3 target)

Base URL `https://cryostasis.ramon.moe/api`. Identity by UUID in the path. A bearer or session token
issued after Microsoft OAuth linking replaces the original no-auth model. Texture bytes for
capes and cosmetics move behind object storage plus a CDN.

| Old command | Method and path | Request body | Response |
|---|---|---|---|
| getVersion | `GET /version` | - | `{ "version": "..." }` |
| getAllCapes | `GET /capes` | - | `{ "capes": [{ "name": "...", "rarity": "..." }] }` |
| addMe | `POST /players/{uuid}/online` | `{ "active": bool, "server": "..." }` (optional) | `204` |
| ImOnServer | `PUT /players/{uuid}/server` | `{ "server": "..." }` | `204` |
| setMyPlayerStatus | `PUT /players/{uuid}/status` | `{ "status": "..." }` | `204` |
| getTheStatusOfThePlayer | `GET /players/{uuid}/status` | - | `{ "status": "...", "state": "...", ... }` |
| getRankofPlayer | `GET /players/{uuid}/rank` | - | `{ "rank": "..." }` |
| getPlayersOnServer | `GET /servers/{server}/players` | - | `{ "players": [...] }` |
| getOnlinePlayingPlayers | `GET /players/online` | - | `{ "players": [...], "count": N }` |
| addMeACosmetic | `POST /players/{uuid}/cosmetics` | `{ "cosmetic": "..." }` | `{ "ok": true }` |
| removeMeACosmetic | `DELETE /players/{uuid}/cosmetics/{cosmetic}` | - | `{ "ok": true }` |
| setMyCape | `PUT /players/{uuid}/cape` | `{ "cape": "..." }` | `204` |
| hasThePlayerTheCosmetic | `GET /players/{uuid}/cosmetics/{cosmetic}` | - | `{ "has": true }` |
| getMSG | `GET /chat` | - | `{ "messages": [...], "cursor": N }` |
| sendMSG | `POST /chat` | `{ "message": "..." }` | the stored message |

The most important addition over the original is authentication: the new endpoints must
verify that the caller owns the UUID it acts on (via the OAuth-issued token), since the old
protocol trusted the client entirely. Rate limiting, caching, and a texture CDN are Phase 3
work items. The batch endpoint `GET /players/{uuid}/cosmetics` (return the full active set
in one call) should be added so the cosmetic renderer fetches once per visible player
rather than per cosmetic.

## 5. Endpoints with no recovered ancestor

These have no row above because the original protocol had nothing like them. They are recorded
here because this document owns the contract, and the shipped client parses them.

| Method and path | Auth | Request body | Response |
|---|---|---|---|
| `GET /players/presence` | none | - | `{ "players": [...], "count": N, "online": N, "afk": N }` |
| `POST /players/presence/batch` | none | `{ "uuids": [...] }` | `{ "players": { "<uuid>": {...} } }` |
| `POST /auth/nonce` | none | - | `{ "server_id": "..." }` |
| `POST /auth/session` | none | `{ "uuid": "...", "username": "...", "server_id": "..." }` | `{ "token": "...", "token_type": "Bearer", "expires_in": N }` |
| `POST /players/cosmetics/batch` | none | `{ "uuids": [...] }` | `{ "players": { "<uuid>": { "cosmetics": [...], "cape": "..." } } }` |
| `GET /ranks` | none | - | `{ "ranks": [{ "name": "...", "color": "#RRGGBB", "staff": bool }] }` |
| `PUT /players/{uuid}/rank` | admin token | `{ "rank": "..." }` | `204` |
| `DELETE /chat/{id}` | staff | - | `204` |
| `GET /chat/mutes` | staff | - | `{ "mutes": [...] }` |
| `POST /chat/mutes` | staff | `{ "player": "...", "minutes": N, "reason": "..." }` | the stored mute |
| `DELETE /chat/mutes/{player}` | staff | - | `{ "ok": bool }` |

`GET /players/{uuid}/rank` gained two fields over its recovered shape: it now answers
`{ "rank": "...", "color": "#RRGGBB", "staff": bool }`. `rank` is unchanged, so a caller written
against the recovered protocol still reads correctly; `color` exists so the client tags a chat
line from the same palette the API stamps onto messages rather than keeping its own copy.

**Auth column.** "admin token" means the `X-Admin-Token` header matching
`CRYOSTASIS_ADMIN_TOKEN`; an unset token disables those routes outright. "staff" means either
that header or a bearer token whose player holds a rank marked `staff` in the registry.

## 6. Presence: online, away, offline

Presence is derived from two timestamps on the player row rather than stored as a state, for the
reason section 6 of `docs/backend-architecture.md` gives: nothing clears a stored state when a
client crashes.

- `last_seen` is written by every heartbeat. Inside `CRYOSTASIS_PRESENCE_WINDOW_SECONDS`
  (120 by default) the client counts as connected.
- `last_active` is written only when a heartbeat reports `active: true`. Past
  `CRYOSTASIS_AFK_AFTER_SECONDS` (300 by default) the player counts as away.

So `offline` when the window has lapsed, `afk` when it has not but activity has, `online`
otherwise. A record that has never reported activity reads as `afk` rather than `online`: there
is nothing saying the player was ever at the keyboard.

`POST /players/{uuid}/online` takes an optional body:

```json
{ "active": true, "server": "hypixel.net" }
```

Both fields are optional and the body itself may be omitted, which is what the recovered `addMe`
did. Such a caller stays `afk` forever, which is the honest answer for a client with no way to
report otherwise. `server` is honoured exactly as `PUT /players/{uuid}/server` would, so a client
that has just joined a server needs one call rather than two.

Two endpoints read presence back. `GET /players/presence` is the roster: everyone inside the
window, away players included, since away is a sub-state of connected and dropping them would
make the roster empty whenever everyone is standing still. Each entry is:

```json
{ "uuid": "...", "username": "Ray", "rank": "Chef", "color": "#E8B14C",
  "state": "afk", "status": "at the anvil", "server": "hypixel.net",
  "last_seen": "2026-08-26T06:34:26Z", "last_active": "2026-08-26T06:29:02Z" }
```

`POST /players/presence/batch` answers the same shape for a named list of UUIDs, offline ones
included, keyed by the UUID as sent. It exists because the surfaces that render other players
(name tags, chat) already know which players are in front of them and would otherwise pull the
whole roster to find three rows in it.

`GET /players/{uuid}/status` returns that same object. `status` is unchanged: it is still the
free text the player set, so a caller written against the recovered `getTheStatusOfThePlayer`
reads the same value out of the same key. Everything beside it is additive.

`status` and `state` are deliberately separate. One is a line a player wrote, the other is a fact
about their client that nobody can set.

## 7. Global chat

Delivery is a polled log rather than a stream, because the service runs as several stateless
replicas with nothing to broadcast through: Postgres `LISTEN/NOTIFY` does not survive
pgbouncer's transaction pooling, and Redis is a dependency the design deliberately avoids.

Every message carries a monotonic `id`, and a reader asks for what comes after the highest id it
holds:

```
GET /chat?after=<id>&limit=<n>&wait=<seconds>
```

- **No `after`**: the newest `limit` messages, oldest first. This is the backlog a client shows
  when it starts reading, and the `cursor` in the reply is where it continues from.
- **With `after`**: everything newer, oldest first.
- **`wait`**: hold the request open until something arrives, up to the server's ceiling (25s).
  Only meaningful once the client holds a cursor. An idle reader therefore costs roughly one
  request per wait window rather than one per poll interval.

The reply is `{ "messages": [...], "cursor": N }`. `cursor` is what to send as `after` next time;
on an empty first read it is the live end of the log, so a client never re-requests a backlog it
already knows is empty. A message is:

```json
{ "id": 12, "uuid": "...", "username": "Ray", "rank": "Chef",
  "color": "#E8B14C", "message": "...", "state": "online",
  "at": "2026-08-25T20:48:33Z" }
```

`username`, `rank`, `color` and `state` are a snapshot taken when the line was posted, so
rendering it needs no further lookups and a later promotion does not rewrite history.

`state` is the sender's presence (section 6) as it stood at post time. Posting counts as a
heartbeat, since the line is proof the client is alive, so a message is never stamped `offline`;
it is not in-world activity, so it does not clear `afk`. A line stamped `afk` therefore means the
sender's character had been parked while they typed, which on a channel spanning many game
servers is the difference between someone playing and someone watching the chat from a menu.

`POST /chat` takes `{ "message": "..." }` and returns the stored message. With auth on, the
sender is taken entirely from the bearer token and the username the session proof recorded, never
from the body, so a caller cannot post as someone else. The body's optional `uuid` and `username`
are honoured only with auth off, where a dev instance has no identity to take. Refusals are:
`400` too long, `403` muted, `422` empty after cleaning, `429` sending too fast.

Moderation is what the original relay lacked, and why the first design dropped chat rather than
port it: messages are length-capped and stripped of control characters, every sender has their
own rate bucket, staff ranks can mute and delete, and the log is swept daily rather than kept.

## 8. Presets

A preset is a named snapshot of which modules a player has on and how each is set. The backend
stores it without reading it: `modules` is the client's own shape, keyed by lower-cased module
name, so a module added to the client needs no backend change.

| Method and path | Auth | Request body | Response |
|---|---|---|---|
| `GET /players/{uuid}/presets` | owner | - | `{ "presets": [{ "name": "...", "modules": {...}, "updated_at": "..." }] }` |
| `PUT /players/{uuid}/presets/{name}` | owner | `{ "modules": { "<module>": { "enabled": bool, "settings": {...} } } }` | `204` |
| `DELETE /players/{uuid}/presets/{name}` | owner | - | `{ "ok": bool }` |

"Owner" is the `require_caller` check every other player write uses. Unlike cosmetics, reads take
it too: a cosmetic is on show to everyone the player meets, but a preset is how they have set up
their client, and nobody else needs it.

`PUT` creates or replaces. The list comes back ordered by name, ignoring case.

| Rule | Limit | Refusal |
|---|---|---|
| Name | 1 to 24 letters, digits, spaces, `-` or `_`, after trimming | `400` |
| Reserved name | `QoL` in any case, the client's built-in preset | `400` |
| Presets per player | 32. Overwriting one is always allowed | `409` |
| `modules` size | 32 KiB serialized | `422` |

Names are limited to that set because the name is a path segment, and an encoded slash is decoded
before routing, so a name holding one could be saved but never deleted.
