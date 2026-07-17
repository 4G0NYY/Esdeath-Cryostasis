"""Async engine and session factory.

One engine per process, created from DATABASE_URL. The URL is normalized to an async driver
so an operator can paste a plain `postgres://` or `postgresql://` string (what most platforms
hand out) and still get asyncpg.
"""

from __future__ import annotations

from uuid import uuid4

from sqlalchemy.ext.asyncio import AsyncEngine, AsyncSession, async_sessionmaker, create_async_engine
from sqlalchemy.pool import NullPool

from app.db.dsn import async_engine_args, parse_multihost

# The pgbouncer-safe connect args, shared by the single- and multi-host asyncpg engines.
# asyncpg prepares every statement server-side and caches them per connection; under a
# transaction- or statement-pooling pgbouncer the server connection behind a client connection
# changes between transactions, so a cached prepared statement is gone the next time it is used
# ("prepared statement __asyncpg_... does not exist"). Turning both statement caches off and
# giving each prepared statement a unique name makes it correct under every pool_mode, at a small
# per-query cost. NullPool lets pgbouncer own the pooling instead of double-pooling beneath it.
def _asyncpg_connect_args() -> dict:
    return {
        "statement_cache_size": 0,
        "prepared_statement_cache_size": 0,
        "prepared_statement_name_func": lambda: f"__cryostasis_{uuid4().hex}__",
    }


def _normalize(url: str) -> str:
    if url.startswith("postgres://"):
        url = "postgresql://" + url[len("postgres://") :]
    if url.startswith("postgresql://"):
        url = "postgresql+asyncpg://" + url[len("postgresql://") :]
    if url.startswith("sqlite://") and "+aiosqlite" not in url:
        url = "sqlite+aiosqlite://" + url[len("sqlite://") :]
    return url


def make_engine(database_url: str) -> AsyncEngine:
    # Multi-host HA DSN (Patroni + pgbouncer client-side failover): SQLAlchemy cannot parse the
    # comma authority, so hand it a hostless URL and give asyncpg the host list plus
    # target_session_attrs through connect_args (app/db/dsn.py). Still Postgres, so it keeps the
    # same pgbouncer-safe statement-cache settings as the single-host path below.
    multihost = parse_multihost(database_url)
    if multihost is not None:
        url, extra = async_engine_args(multihost)
        return create_async_engine(
            url, poolclass=NullPool, connect_args={**_asyncpg_connect_args(), **extra}
        )

    url = _normalize(database_url)
    if url.startswith("postgresql+asyncpg"):
        return create_async_engine(
            url, poolclass=NullPool, connect_args=_asyncpg_connect_args()
        )
    # Non-Postgres (the aiosqlite the repo tests run against): the pgbouncer args do not apply,
    # and aiosqlite would reject them, so keep a plain engine.
    return create_async_engine(url, pool_pre_ping=True)


def make_sessionmaker(engine: AsyncEngine) -> async_sessionmaker[AsyncSession]:
    return async_sessionmaker(engine, expire_on_commit=False)
