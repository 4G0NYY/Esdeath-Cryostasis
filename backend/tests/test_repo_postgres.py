"""Exercise the real PostgresRepo against an async sqlite engine.

This is not the production database, but it runs the same SQLAlchemy code paths (session
handling, the FK-backed active set, the last_seen presence query), so a regression in the
repo is caught here without needing a Postgres to be up. The contract-level parity with
MemoryRepo is what matters: both must answer identically.
"""

from __future__ import annotations

import pytest_asyncio
from sqlalchemy.ext.asyncio import create_async_engine

from app.db.models import Base
from app.db.session import make_sessionmaker
from app.repo.postgres import PostgresRepo

UUID = "aaaaaaaa-bbbb-cccc-dddd-eeeeeeeeeeee"


@pytest_asyncio.fixture
async def repo():
    engine = create_async_engine("sqlite+aiosqlite://")
    async with engine.begin() as conn:
        await conn.run_sync(Base.metadata.create_all)
    yield PostgresRepo(make_sessionmaker(engine))
    await engine.dispose()


async def test_get_creates_empty_player(repo):
    player = await repo.get_player(UUID)
    assert player.uuid == UUID
    assert player.cosmetics == set()
    assert player.cape == ""
    assert player.rank == "Default"


async def test_cosmetic_add_is_idempotent(repo):
    assert await repo.add_cosmetic(UUID, "halo") is True
    assert await repo.add_cosmetic(UUID, "halo") is False
    assert (await repo.get_player(UUID)).cosmetics == {"halo"}
    assert await repo.remove_cosmetic(UUID, "halo") is True
    assert await repo.remove_cosmetic(UUID, "halo") is False


async def test_presence_window(repo):
    assert await repo.online_players(120) == []
    await repo.touch(UUID)
    assert UUID in await repo.online_players(120)
    # A zero-width window means nothing is fresh enough, so the player reads offline.
    assert await repo.online_players(0) == []


async def test_server_marks_online(repo):
    await repo.set_server(UUID, "hypixel")
    assert UUID in await repo.players_on_server("HYPIXEL", 120)


async def test_batch(repo):
    await repo.add_cosmetic(UUID, "halo")
    other = "11111111-1111-1111-1111-111111111111"
    result = await repo.get_players([UUID, other])
    assert result[UUID].cosmetics == {"halo"}
    # A never-seen uuid comes back empty, not missing.
    assert result[other].cosmetics == set()


async def test_undashed_uuid_keys_same_player(repo):
    await repo.add_cosmetic("aaaaaaaabbbbccccddddeeeeeeeeeeee", "tophat")
    assert (await repo.get_player(UUID)).cosmetics == {"tophat"}
