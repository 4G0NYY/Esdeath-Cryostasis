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
- **Global chat is dropped.** The original relay (port 1879) had no auth and no moderation.
  The endpoints are not ported. Phase 2's Chat and CleanChat modules stay unchecked.
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
    v1/            version, players, cosmetics, capes, presence
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

## 5. Data model

Postgres, keyed by Minecraft UUID.

| Table | Columns | Notes |
|---|---|---|
| `players` | `uuid` PK, `rank`, `status`, `last_seen`, `cape`, `updated_at` | `rank` is now a display tag only, since it no longer gates cape rarity |
| `cosmetics` | `slug` PK, `kind`, `rarity`, `texture_key`, `model` | The catalogue. Seeded from a file and effectively read-only at runtime |
| `player_cosmetics` | `player_uuid` FK, `cosmetic_slug` FK, PK on both | The active set, not an ownership record |

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

This is deliberately **not** Redis. With chat dropped, pub/sub fan-out is gone, and the
remaining needs (a presence window and a small response cache) are one indexed query and an
in-process TTL cache. Redis earns its place when there is more than one replica; until then it
is a container to run, back up, and debug for no gain. The swap is contained in `repo/`.

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

- Chat endpoints (`GET /chat`, `POST /chat`) are not ported.
- `hasThePlayerTheCosmetic` (`GET /players/{uuid}/cosmetics/{cosmetic}`) becomes a catalogue
  check and always returns true for a real cosmetic. Kept only for contract compatibility.
- New: a batch active-cosmetics endpoint (section 8).
- New: `POST /auth/session` and the nonce exchange (section 4).

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
- Redis, until a second replica exists.
- Chat, unless it comes back with moderation tooling.
- A cosmetics admin surface. With cosmetics free and the catalogue seeded from a file, there
  is nothing per-player to administer yet.
