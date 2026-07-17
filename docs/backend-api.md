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
| addMe | `POST /players/{uuid}/online` | - | `204` |
| ImOnServer | `PUT /players/{uuid}/server` | `{ "server": "..." }` | `204` |
| setMyPlayerStatus | `PUT /players/{uuid}/status` | `{ "status": "..." }` | `204` |
| getTheStatusOfThePlayer | `GET /players/{uuid}/status` | - | `{ "status": "..." }` |
| getRankofPlayer | `GET /players/{uuid}/rank` | - | `{ "rank": "..." }` |
| getPlayersOnServer | `GET /servers/{server}/players` | - | `{ "players": [...] }` |
| getOnlinePlayingPlayers | `GET /players/online` | - | `{ "players": [...], "count": N }` |
| addMeACosmetic | `POST /players/{uuid}/cosmetics` | `{ "cosmetic": "..." }` | `{ "ok": true }` |
| removeMeACosmetic | `DELETE /players/{uuid}/cosmetics/{cosmetic}` | - | `{ "ok": true }` |
| setMyCape | `PUT /players/{uuid}/cape` | `{ "cape": "..." }` | `204` |
| hasThePlayerTheCosmetic | `GET /players/{uuid}/cosmetics/{cosmetic}` | - | `{ "has": true }` |
| getMSG | `GET /chat` | - | `{ "message": "...", "from": "..." }` |
| sendMSG | `POST /chat` | `{ "message": "..." }` | `204` |

The most important addition over the original is authentication: the new endpoints must
verify that the caller owns the UUID it acts on (via the OAuth-issued token), since the old
protocol trusted the client entirely. Rate limiting, caching, and a texture CDN are Phase 3
work items. The batch endpoint `GET /players/{uuid}/cosmetics` (return the full active set
in one call) should be added so the cosmetic renderer fetches once per visible player
rather than per cosmetic.
