"""Async engine and session factory.

One engine per process, created from DATABASE_URL. The URL is normalized to an async driver
so an operator can paste a plain `postgres://` or `postgresql://` string (what most platforms
hand out) and still get asyncpg.
"""

from __future__ import annotations

from uuid import uuid4

from sqlalchemy.ext.asyncio import AsyncEngine, AsyncSession, async_sessionmaker, create_async_engine
from sqlalchemy.pool import NullPool


def _normalize(url: str) -> str:
    if url.startswith("postgres://"):
        url = "postgresql://" + url[len("postgres://") :]
    if url.startswith("postgresql://"):
        url = "postgresql+asyncpg://" + url[len("postgresql://") :]
    if url.startswith("sqlite://") and "+aiosqlite" not in url:
        url = "sqlite+aiosqlite://" + url[len("sqlite://") :]
    return url


def make_engine(database_url: str) -> AsyncEngine:
    url = _normalize(database_url)
    if url.startswith("postgresql+asyncpg"):
        # pgbouncer-safe configuration. asyncpg prepares every statement server-side and caches
        # the prepared statements per connection. Under a transaction- or statement-pooling
        # pgbouncer the server connection behind a client connection changes between
        # transactions, so a cached prepared statement is gone the next time it is used, which
        # surfaces as "prepared statement __asyncpg_... does not exist". Turning both the asyncpg
        # and SQLAlchemy statement caches off and giving each prepared statement a unique name
        # makes it correct under every pgbouncer pool_mode, at a small per-query cost. NullPool
        # lets pgbouncer own the connection pooling instead of double-pooling beneath it.
        return create_async_engine(
            url,
            poolclass=NullPool,
            connect_args={
                "statement_cache_size": 0,
                "prepared_statement_cache_size": 0,
                "prepared_statement_name_func": lambda: f"__cryostasis_{uuid4().hex}__",
            },
        )
    # Non-Postgres (the aiosqlite the repo tests run against): the pgbouncer args do not apply,
    # and aiosqlite would reject them, so keep a plain engine.
    return create_async_engine(url, pool_pre_ping=True)


def make_sessionmaker(engine: AsyncEngine) -> async_sessionmaker[AsyncSession]:
    return async_sessionmaker(engine, expire_on_commit=False)
