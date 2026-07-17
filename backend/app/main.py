"""App factory, middleware, router mounting.

Routers are mounted at /api with no version segment, matching the shipped client, which
hardcodes /api via the cryostasis.api system property (CosmeticService). The api/v1 package
name keeps a version seam in the code without putting one in the URL; a future v2 would
mount a second package and /api would stay pinned to whichever version the live client
needs.
"""

from __future__ import annotations

import contextlib
from collections.abc import AsyncIterator

from fastapi import FastAPI

from app.api.deps import RateLimiter
from app.api.v1 import auth, cosmetics, meta, players
from app.auth.session import NonceStore
from app.config import Settings, get_settings


def _build_repo(settings: Settings):
    """Select the storage backend. Empty DATABASE_URL selects the in-memory repo, which is
    both the zero-config dev default and what the test suite runs against."""
    if not settings.database_url:
        from app.repo.memory import MemoryRepo

        return MemoryRepo()

    from app.db.session import make_engine, make_sessionmaker
    from app.repo.postgres import PostgresRepo

    engine = make_engine(settings.database_url)
    return PostgresRepo(make_sessionmaker(engine))


@contextlib.asynccontextmanager
async def _lifespan(app: FastAPI) -> AsyncIterator[None]:
    settings: Settings = app.state.settings
    app.state.repo = _build_repo(settings)
    app.state.nonces = NonceStore(ttl_seconds=settings.presence_window_seconds)
    app.state.rate_limiter = RateLimiter(settings.rate_limit_per_minute)
    try:
        yield
    finally:
        await app.state.repo.close()


def create_app(settings: Settings | None = None) -> FastAPI:
    settings = settings or get_settings()

    if settings.require_auth and settings.jwt_secret == "dev-insecure-secret":
        # Fail fast rather than sign real tokens with a public default.
        raise RuntimeError("CRYOSTASIS_JWT_SECRET must be set when auth is enforced")

    app = FastAPI(title="Cryostasis backend", version=settings.version, lifespan=_lifespan)
    app.state.settings = settings

    for router in (meta.router, players.router, cosmetics.router, auth.router):
        app.include_router(router, prefix="/api")

    @app.get("/health")
    async def health() -> dict:
        return {"status": "ok"}

    return app


app = create_app()
