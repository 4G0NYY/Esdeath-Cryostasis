# Cryostasis backend

The cosmetics and presence backend for the Esdeath Cryostasis client. Python plus FastAPI,
deployed as a container. This replaces the earlier Java dev instance; both spoke the same
contract, and this one is now the only backend.

The wire contract is owned by `../docs/backend-api.md`; the design is
`../docs/backend-architecture.md`. This service implements both, with the deltas noted in
architecture section 9 (chat dropped, `has` is a catalogue check, a batch endpoint added,
session-proof auth added).

## Layout

```
app/
  main.py          app factory, /api mount, /health, lifespan wiring
  config.py        pydantic-settings, env driven (CRYOSTASIS_ prefix)
  api/
    deps.py        DI: repo, settings, current caller, rate limiter
    v1/            meta (version, capes), players, cosmetics, auth
  domain/          pure schemas and rules (no FastAPI, no SQLAlchemy)
  repo/            base Protocol, memory.py (dev/tests), postgres.py
  storage/         CDN URL building from object keys
  auth/            session proof (Mojang hasJoined) and JWT issue/verify
  db/              SQLAlchemy 2.0 async models, session, Alembic migrations
tests/             contract + auth + repo tests, all run with no database
```

The seam that matters is `repo/`: the API depends only on the `Repo` Protocol, so
`memory.py` and `postgres.py` are interchangeable. `memory.py` is what the whole test suite
runs against, which is how contract parity stays cheap to enforce.

## Run locally

Requires Python 3.11+. From this directory:

```
pip install -e ".[dev]"        # or: pip install fastapi "uvicorn[standard]" ... (see pyproject)
uvicorn app.main:app --reload  # in-memory backend, auth off, on http://localhost:8000
```

With no environment set it boots the in-memory repo with auth off, which is the mode the
client is smoke-tested against. Point the client at it with
`-Dcryostasis.api=http://localhost:8000/api`.

Quick check:

```
curl localhost:8000/api/version
curl localhost:8000/api/capes
curl -H 'Content-Type: application/json' -X POST \
     localhost:8000/api/players/<uuid>/cosmetics -d '{"cosmetic":"halo"}'
curl localhost:8000/api/players/<uuid>/cosmetics
```

Note the `Content-Type: application/json` header on writes: unlike the old Java instance,
which parsed any body as JSON, FastAPI expects the header on a JSON body.

## Configuration

All variables take the `CRYOSTASIS_` prefix (see `.env.example`). Only
`CRYOSTASIS_DATABASE_URL` is required in production.

| Variable | Default | Purpose |
|---|---|---|
| `CRYOSTASIS_DATABASE_URL` | empty | Blank selects the in-memory repo. A Postgres URL selects `postgres.py`. |
| `CRYOSTASIS_REQUIRE_AUTH` | `false` | Enforce the session-proof handshake on writes. |
| `CRYOSTASIS_JWT_SECRET` | dev default | Signs issued tokens. Must be changed when auth is on, or the app refuses to boot. |
| `CRYOSTASIS_PRESENCE_WINDOW_SECONDS` | `120` | A player is online while last seen inside this window. |
| `CRYOSTASIS_CDN_BASE_URL` | cdn.cryostasis.ramon.moe | Base for texture URLs built from object keys. |
| `CRYOSTASIS_RATE_LIMIT_PER_MINUTE` | `120` | Per authenticated UUID, not per IP. |
| `CRYOSTASIS_PORT` | `8080` | Listen port in the container. |

## Auth

Off by default so the client can be tested before its side of the handshake ships. When on:

1. `POST /api/auth/nonce` returns a `server_id`.
2. The client calls Mojang's `joinServer` with the session it already holds and that nonce.
3. `POST /api/auth/session` with `{uuid, username, server_id}`; this service verifies through
   Mojang's `hasJoined` and returns a short-lived bearer token.
4. Writes to `/api/players/{uuid}/...` must carry `Authorization: Bearer <token>`, and the
   token's subject must equal the path UUID. Reads stay open.

No OAuth and no Azure app: the Mojang session handshake every server already performs is
enough to prove UUID ownership. OAuth remains a Phase 5 concern for the alt-account manager
only.

## Database and migrations

Alembic owns the schema; there is no create-on-boot. The initial migration also seeds the
cosmetic catalogue and cape list, so a fresh database is immediately usable.

```
export CRYOSTASIS_DATABASE_URL=postgresql://user:pass@localhost:5432/cryostasis
alembic upgrade head
```

`asyncpg` is needed at runtime for Postgres and ships in the container image; it is not in
the dev extras because the test suite uses the in-memory and aiosqlite paths.

## Tests

```
pip install -e ".[dev]"
pytest
```

The suite needs no database. `test_contract.py` asserts the wire shapes against the docs,
`test_auth.py` covers the handshake and the ownership check (Mojang is patched, never
called), and `test_repo_postgres.py` runs the real `PostgresRepo` against async sqlite so
the Postgres code paths are exercised too.

## Docker

```
docker build -t cryostasis-backend .
docker run -p 8080:8080 -e CRYOSTASIS_DATABASE_URL=postgresql://... cryostasis-backend
```

The entrypoint runs `alembic upgrade head` and then uvicorn. `/health` is the platform
probe. For a single host with its own Postgres, `docker-compose.yml` wires the two together.

## Scaling: stateless and Swarm-ready

The service is stateless and safe to run as several replicas behind a load balancer. Every
request-scoped piece of state lives in shared storage, not in a replica:

- Player data (cosmetics, status, rank, server, cape) is read and written per request through
  the Postgres repo, with no in-process cache.
- Presence is derived from the `last_seen` column against a window, so any replica computes the
  same online list, and a crashed client falls offline on its own.
- ETags on the hot cosmetics path are a hash of the payload, so conditional requests work no
  matter which replica answers.
- Auth tokens are stateless JWTs; every replica validates them as long as they share
  `CRYOSTASIS_JWT_SECRET` (they do, from one secret).
- Login nonces are in the `auth_nonces` table, not in process, so `/auth/nonce` and
  `/auth/session` can land on different replicas.

Two deployment details make this hold on a real cluster:

- **Migrations are concurrency-safe.** Each replica runs `alembic upgrade head` on start; a
  transaction-level Postgres advisory lock (`app/db/migrations/env.py`) serializes them, so a
  fresh deploy or a rolling update does not race. Keep schema changes backward compatible
  (expand then contract) so old and new replicas can run together during a roll.
- **pgbouncer is handled.** The async engine disables asyncpg prepared-statement caching and
  uses unique statement names (`app/db/session.py`), so it is correct under any pgbouncer
  `pool_mode`, not only session pooling. Point `CRYOSTASIS_DATABASE_URL` at pgbouncer.

The one intentional per-replica component is the rate limiter (`app/api/deps.py`): it is
in-process, so the effective cap is `RATE_LIMIT_PER_MINUTE` times the replica count. That is an
accepted trade for abuse protection on a cosmetics backend; a strict cluster-wide cap would need
a shared store and a round-trip per mutating request.

`docker-stack.yml` is a ready Swarm stack: replicas, rolling updates, a healthcheck, and Docker
secrets for the database URL and JWT secret (read from `/run/secrets` via `secrets_dir`, so no
password sits in the stack file). It brings no database of its own; it points at your pgbouncer.

```
printf 'postgresql://cryostasis:<pw>@pgbouncer:6432/cryostasis' | docker secret create CRYOSTASIS_DATABASE_URL -
openssl rand -hex 32 | docker secret create CRYOSTASIS_JWT_SECRET -
docker stack deploy -c docker-stack.yml cryostasis
```
