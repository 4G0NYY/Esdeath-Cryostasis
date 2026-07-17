"""Exercise PostgresNonceStore against an async sqlite engine.

Like test_repo_postgres, this runs the real store's SQL against aiosqlite so the code path is
covered without a Postgres. What matters for a multi-replica deploy is proven here: a nonce
issued through one store instance is consumable through another that shares the engine (the
stand-in for a second replica), single-use holds, and an expired nonce is rejected.
"""

from __future__ import annotations

import pytest_asyncio
from sqlalchemy.ext.asyncio import create_async_engine

from app.auth.session import PostgresNonceStore
from app.db.models import Base
from app.db.session import make_sessionmaker


@pytest_asyncio.fixture
async def sessionmaker():
    engine = create_async_engine("sqlite+aiosqlite://")
    async with engine.begin() as conn:
        await conn.run_sync(Base.metadata.create_all)
    yield make_sessionmaker(engine)
    await engine.dispose()


async def test_issue_then_consume_is_single_use(sessionmaker):
    store = PostgresNonceStore(sessionmaker)
    nonce = await store.issue()
    assert await store.consume(nonce) is True
    # A replay must fail: the row is gone.
    assert await store.consume(nonce) is False


async def test_nonce_crosses_replicas(sessionmaker):
    # Two store instances on one database stand in for two replicas behind a load balancer.
    replica_a = PostgresNonceStore(sessionmaker)
    replica_b = PostgresNonceStore(sessionmaker)
    nonce = await replica_a.issue()
    # Issued on A, proven on B: the whole reason the store is shared rather than in process.
    assert await replica_b.consume(nonce) is True


async def test_expired_nonce_is_rejected(sessionmaker):
    store = PostgresNonceStore(sessionmaker, ttl_seconds=-1)
    nonce = await store.issue()
    assert await store.consume(nonce) is False


async def test_unknown_nonce_is_rejected(sessionmaker):
    store = PostgresNonceStore(sessionmaker)
    assert await store.consume("never-issued") is False
