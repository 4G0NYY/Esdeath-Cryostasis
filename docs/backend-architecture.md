# Backend Architecture: Python plus FastAPI (Phase 3 target)

This document is the design for the production backend. It builds on `docs/backend-api.md`,
which records the recovered wire protocol and the REST redesign; that document owns the
contract, this one owns the implementation shape.

This design is now implemented in `backend/`. The earlier Java dev instance (JDK HTTP server
plus Gson) has been removed, so the FastAPI service is the only backend. The client points
at it with `-Dcryostasis.api`; with no environment set it boots the in-memory backend with
auth off, which is the mode the client is smoke-tested against.

## 1. Decisions this design rests on

- **Cosmetics are free for every linked account.** There is no ownership, no grants, no
  purchases, and no entitlement reconciliation. This removes an entire table and the whole
  payments surface.
- **Global chat is back, with the two things the original lacked.** The first version of this
  design dropped it: the original relay (port 1879) had no auth and no moderation, and porting
  that as-is would have shipped an unauthenticated broadcast channel. Both objections are
  answered by work that has since landed rather than by dropping the feature. The session proof
  (section 4) means a sender is an account Mojang authenticated, so there is no anonymous
  posting and no posting as someone else; ranks (section 4a) give a staff tier the mute and
  delete calls to act with. Section 12 has the delivery design.
- **Hosting is undecided.** The service targets plain Docker plus `DATABASE_URL`, so it runs
  anywhere and the choice can be made at deploy time.

## 2. Why "free for everyone" simplifies more than it looks

In the dev instance, `Store.Player.cosmetics` serves two purposes at once: it is what
`GET /players/{uuid}/cosmetics` returns (the renderer reads it as the active set) and it is
what `addMeACosmetic` grants (ownership). That ambiguity only exists because ownership exists.

With cosmetics free, the two collapse into one: the set **is** the active selection. There is
no owned-versus-worn distinction to keep in sync, `hasThePlayerTheCosmetic` degrades to a
catalogue-membership check, and `POST`/`DELETE /players/{uuid}/cosmetics` cleanly mean "put
this on" and "take this off", which is what the client already does with them.

## 3. Service shape

A modular monolith in one container. The traffic is a client mod's cosmetics lookups, the
domains are small and coupled, and hosting is containerized. Splitting this into services
would buy nothing and cost a deploy story.

```
app/
  main.py          app factory, middleware, router mounting
  config.py        pydantic-settings, env driven
  api/
    deps.py        DI: db session, current_player, rate limiter
    v1/            version, players, cosmetics, capes, presence, ranks, chat
  domain/          pure logic: schemas and rules, no FastAPI and no DB imports
  repo/            base Protocol, postgres.py, memory.py
  storage/         object storage keys and CDN URL building
  auth/            session proof, token issue and verify
  db/              SQLAlchemy 2.0 async models, Alembic migrations
```

`repo/` exists because `Store.java` already proved the value of one seam that owns storage.
`repo/memory.py` is not only a dev convenience: it lets the whole test suite run with no
database, which is how contract parity with `docs/backend-api.md` stays cheap to enforce.

`domain/` importing neither FastAPI nor SQLAlchemy is the rule that keeps the seam honest. If
a rule needs a request or a session to express itself, it belongs in `api/` or `repo/`.

## 4. Identity: session proof, not OAuth

The roadmap lists "Implement Microsoft OAuth account linking" and blocks it on registering an
Azure app. That item conflates two separate problems:

1. **Proving to this backend that a caller owns a UUID.** This does not need OAuth. Use the
   Mojang session handshake that every Minecraft server already performs: the client requests
   a nonce, calls `joinServer` with the session it already holds, and this backend verifies
   through Mojang's `hasJoined`. The backend then issues its own short-lived JWT. No Azure
   app, no second login, and no credentials ever reach this service.
2. **Logging into Minecraft as an alt account** (the Phase 5 account manager). This genuinely
   needs the Microsoft OAuth device-code flow.

Only the second needs OAuth, so Phase 3 auth is unblocked today and OAuth stays a Phase 5
concern.

Auth still matters despite cosmetics being free: without it anyone could set anyone else's
status string or appearance, which is an impersonation vector. It is now the only
security-relevant piece of the service, which is another reason to keep it this simple.

## 4a. Ranks: granted, not chosen

`players.rank` has existed since the first migration and until now had no write path: it was read
by `GET /players/{uuid}/rank` and never set by anything. It is now a small registry in
`domain/models.py` (Default, Premium, Epic, Chef, Mod, Admin), each entry carrying a display
colour and a `staff` flag.

Two decisions shape it:

- **The registry lives in the service, not the client.** The colour a client tags a chat line
  with comes from `GET /ranks` and from the `color` stamped onto each message, so adding a rank
  is a backend change and no client can drift from the palette.
- **A rank is set by an admin token, never by the player.** `PUT /players/{uuid}/rank` takes the
  `X-Admin-Token` header rather than going through `require_caller`. It is the only write in the
  service a player cannot make to their own row, which is exactly the point: everything else on
  a player record is a preference, and a rank is a grant. An unset `CRYOSTASIS_ADMIN_TOKEN`
  disables the route outright rather than falling back to a weaker check, so a deployment that
  forgot to configure one cannot have its ranks rewritten.

Ranks still gate no cosmetics. Cosmetics are free for every linked account (section 1), and that
has not changed; what a rank buys is a coloured tag and, for the two staff tiers, the moderation
calls on the chat routes.

## 5. Data model

Postgres, keyed by Minecraft UUID.

| Table | Columns | Notes |
|---|---|---|
| `players` | `uuid` PK, `username`, `rank`, `status`, `last_seen`, `cape`, `updated_at` | `rank` is a display tag plus a staff flag, since it no longer gates cape rarity. `username` is written by the session proof, never by a request body |
| `cosmetics` | `slug` PK, `kind`, `rarity`, `texture_key`, `model` | The catalogue. Seeded from a file and effectively read-only at runtime |
| `player_cosmetics` | `player_uuid` FK, `cosmetic_slug` FK, PK on both | The active set, not an ownership record |
| `chat_messages` | `id` PK (bigserial), `player_uuid`, `username`, `rank`, `color`, `message`, `created_at` | The global channel. `id` is the poll cursor; sender fields are a snapshot taken at post time |
| `chat_mutes` | `player_uuid` PK, `username`, `until`, `reason`, `by_uuid` | An expiry, not a flag, so a mute lapses without a sweeper |

Cape stays its own column rather than a row in `player_cosmetics`, for two reasons: the
contract already splits them (`PUT /players/{uuid}/cape`), and capes are a texture layer while
the rest are ModelPart meshes, so they are genuinely a different render pipeline.

Visual conflicts (a top hat and a bandana at once) are left to the client. The old client
modeled cosmetics as a set and did the same.

## 6. Presence without Redis

Presence is a `last_seen` timestamp on `players`; online is derived as
`last_seen > now() - interval`. The old `addMe` and `ImOnServer` had no way to notice a
crashed client, so "online" drifted permanently. A heartbeat with a derived window makes
offline the default and needs no sweeper job.

This is deliberately **not** Redis. A presence window is one indexed query, and the response
cache it would also serve is an ETag the client already honours. Chat's return did put the one
genuine pub/sub case back on the table, and section 12 takes the polled-log route instead for the
same reason: at this traffic Redis is a container to run, back up and debug for a latency
improvement nothing here needs. The swap stays contained in `repo/`.

## 7. Textures and object storage

S3-compatible object storage, with Cloudflare R2 as the default recommendation: egress is
free and the CDN is built in.

The database stores only the object key. The API returns a CDN URL in its JSON so the client
downloads bytes directly and this service never proxies them. Keys are content-addressed (the
texture hash in the filename), which makes them immutable, cacheable forever, and removes
cache invalidation as a problem entirely: a changed texture is a new key.

## 8. Caching and rate limiting

`GET /players/{uuid}/cosmetics` is the hot path. `CosmeticService` calls it once per visible
player and already keeps an async TTL cache.

- Serve `ETag` and `Cache-Control` so the client's existing cache cooperates rather than
  duplicating this one.
- Add a **batch endpoint** taking a list of UUIDs. On a full server this turns one request per
  visible player into one request per refresh, which is the difference that matters at 100
  players. `docs/backend-api.md` already suggests the per-player batch; this is the next step
  and the client's cache should be reworked to use it.
- Rate limit keyed by the authenticated UUID, not by IP. Players behind one NAT would
  otherwise share a bucket.

## 9. Contract delta

Against `docs/backend-api.md`:

- `hasThePlayerTheCosmetic` (`GET /players/{uuid}/cosmetics/{cosmetic}`) becomes a catalogue
  check and always returns true for a real cosmetic. Kept only for contract compatibility.
- Chat endpoints are ported, but not in the shape that document guessed: `GET /chat` is a
  cursor-and-long-poll read returning a list, not a single latest message with client-side
  dedup (section 12).
- `GET /players/{uuid}/rank` gains `color` and `staff` alongside the unchanged `rank`, so the
  addition is backward compatible for anything parsing the recovered shape.
- New: a batch active-cosmetics endpoint (section 8).
- New: `POST /auth/session` and the nonce exchange (section 4).
- New: `GET /ranks` and the admin-only `PUT /players/{uuid}/rank` (section 4a).
- New: the chat moderation routes, which the original protocol had no equivalent of at all.

**Versioning snag:** the client hardcodes `/api` (`CosmeticService`, via the
`cryostasis.api` system property). Introducing `/api/v1` breaks the shipped client unless
`/api` stays a permanent alias. Decided: routes are mounted at `/api` with no version
segment in the URL. The version seam lives in the code (`app/api/v1/`), so a future v2 would
add a second package and mount it while `/api` stays pinned to whichever version the live
client needs. This keeps the shipped client working with no alias to maintain.

## 10. Deployment and testing

- One Dockerfile, uvicorn behind the host's TLS terminator. Config is 12-factor, so
  `DATABASE_URL` is the only required variable. A `/health` endpoint for the platform's probe.
- Alembic owns the schema. No create-on-boot.
- pytest plus httpx `AsyncClient` against `repo/memory.py`, so the suite needs no database and
  runs in CI in seconds. The contract tests assert against `docs/backend-api.md` directly, so
  the two cannot drift silently.

## 11. Deferred

- Microsoft OAuth device-code flow (Phase 5, for the alt account manager only).
- Redis. It would buy a push channel for chat and a cluster-wide rate limit; neither is worth a
  stateful dependency at this traffic, and the polled log (section 12) was designed so that
  swapping to a push transport later changes only the read endpoint.
- Chat history beyond a day, and any search over it. The channel is live, not an archive.
- A cosmetics admin surface. With cosmetics free and the catalogue seeded from a file, there
  is nothing per-player to administer yet. Ranks needed one and got the narrowest possible
  version: a single token-guarded route (section 4a).

## 12. Global chat: a polled log, not a socket

Chat is the only feature in this service where clients need to learn about something they did not
ask for. Every other endpoint answers a question the caller already had.

**Why not a socket.** The obvious shape is a WebSocket per client with the server pushing. It
does not survive this deployment. The service runs as several stateless replicas behind Swarm's
routing mesh, so a message posted to one replica has to reach readers connected to the other two,
and a fan-out needs something to fan out through:

- **Postgres `LISTEN/NOTIFY`** is the one option that adds no new infrastructure, and it does not
  work here. `DATABASE_URL` points at pgbouncer in transaction pooling mode, where the server
  connection behind a client connection changes between transactions; a `LISTEN` registered on
  one is simply gone. Session pooling would fix it and give up the connection multiplexing that
  is the reason pgbouncer is there.
- **Redis pub/sub** works and is what most designs reach for. It is also exactly the dependency
  section 6 declined to take on for presence: a container to run, back up and debug, for a
  cosmetics backend serving a client mod.

**What it does instead.** `chat_messages.id` is a bigserial, so "what is new for me" becomes a
per-client question any replica can answer from the database alone: `WHERE id > :cursor`, one
indexed range scan. There is no shared state between replicas beyond the table they all already
share, so chat inherits the statelessness the rest of the service has rather than eroding it.

The cost of polling, a request per client per interval, is paid down by holding the read open
(`wait`, capped at 25s): a reader with nothing to receive costs one aggregate query per second
against `MAX(id)` rather than a full round trip, and one HTTP request per wait window. On an idle
channel with 50 clients that is roughly two requests a second across the cluster, which is less
traffic than the cosmetics hot path already carries.

The client half mirrors this: `ChatService` polls off-thread and hands finished lines to the
client thread through a queue, so nothing in the delivery path can block a frame.

**What this forecloses, and what it does not.** Latency is bounded by the poll interval, so a
message lands within about a second rather than instantly. That is well inside what a chat line
needs. Should it stop being enough, the swap is contained: the store, the moderation, and the
message shape are unchanged by transport, and only `GET /chat` would be replaced.

**Moderation is part of the design, not an afterthought**, because its absence is why the first
version of this document dropped chat rather than port it:

- Posting requires the session proof, so a sender is an authenticated account. The username shown
  is the one Mojang confirmed during the handshake and stored on the player row; the request body
  cannot supply it.
- Messages are capped at 256 characters and stripped of non-printable characters, so a line
  cannot forge the client's formatting or break its layout.
- Each sender has a chat-specific rate bucket, separate from the general one, because a chat line
  is the one write a player repeats deliberately.
- Staff ranks mute (an expiry, so it lapses on its own) and delete. A delete is a hard delete
  rather than a tombstone: ids are the poll cursor, and a row that lingered to be skipped would
  need a second column and a filter on every read.
- The log is swept to a day on write, the same opportunistic sweep the auth nonces use. Nothing
  needs a scheduled job.
