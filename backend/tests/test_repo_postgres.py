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


async def test_activity_is_only_recorded_when_reported(repo):
    await repo.touch(UUID)
    assert (await repo.get_player(UUID)).last_active is None
    await repo.touch(UUID, active=True)
    assert (await repo.get_player(UUID)).last_active is not None


async def test_presence_returns_whole_records(repo):
    # The roster needs names and both timestamps, which is why this returns players rather than
    # the UUIDs online_players answers with.
    assert await repo.presence(120) == []
    await repo.set_username(UUID, "Ray")
    await repo.touch(UUID, active=True)

    roster = await repo.presence(120)
    assert [p.uuid for p in roster] == [UUID]
    assert roster[0].username == "Ray"
    assert roster[0].presence_state(120, 300) == "online"
    assert await repo.presence(0) == []


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


async def test_rank_and_username_roundtrip(repo):
    await repo.set_rank(UUID, "Chef")
    await repo.set_username(UUID, "Ray")
    player = await repo.get_player(UUID)
    assert player.rank == "Chef" and player.username == "Ray"

    # Moderation resolves a name to a player, case-insensitively, since a moderator types the
    # name as it appears in chat.
    found = await repo.find_by_username("ray")
    assert found is not None and found.uuid == UUID
    assert await repo.find_by_username("someone-else") is None


async def test_chat_log_is_an_ordered_cursor(repo):
    from datetime import timedelta

    from app.domain.models import ChatMessage, now

    def line(text: str) -> ChatMessage:
        return ChatMessage(
            id=0, uuid=UUID, username="Ray", rank="Default", color="#9AA7B8", message=text, at=now()
        )

    first = await repo.post_chat(line("one"))
    second = await repo.post_chat(line("two"))
    # The id is assigned by the database and must advance, since it is the poll cursor.
    assert second.id > first.id
    assert await repo.latest_chat_id() == second.id

    assert [m.message for m in await repo.chat_since(first.id, 10)] == ["two"]
    # No cursor: the tail of the log, still oldest first.
    assert [m.message for m in await repo.chat_since(None, 10)] == ["one", "two"]

    assert await repo.delete_chat(first.id) is True
    assert await repo.delete_chat(first.id) is False

    await repo.prune_chat(now() + timedelta(hours=1))
    assert await repo.chat_since(None, 10) == []


async def test_mute_lapses_without_a_sweeper(repo):
    from datetime import timedelta

    from app.domain.models import Mute, now

    await repo.set_mute(Mute(uuid=UUID, username="Ray", until=now() + timedelta(minutes=5)))
    stored = await repo.get_mute(UUID)
    assert stored is not None and stored.is_active()
    assert [m.uuid for m in await repo.active_mutes()] == [UUID]

    # An expired row stays behind but reads as inactive, which is why no sweeper is needed.
    await repo.set_mute(Mute(uuid=UUID, username="Ray", until=now() - timedelta(minutes=1)))
    lapsed = await repo.get_mute(UUID)
    assert lapsed is not None and not lapsed.is_active()
    assert await repo.active_mutes() == []

    assert await repo.clear_mute(UUID) is True
    assert await repo.get_mute(UUID) is None


async def test_presets_roundtrip(repo):
    from app.domain.models import now
    from app.domain.presets import ModuleState, Preset

    pvp = {"killaura": ModuleState(enabled=True, settings={"Reach": 3.5})}
    await repo.put_preset(UUID, Preset(name="PvP", modules=pvp, updated_at=now()))
    await repo.put_preset(UUID, Preset(name="anarchy", modules={}, updated_at=now()))

    presets = await repo.list_presets(UUID)
    assert [p.name for p in presets] == ["anarchy", "PvP"]
    assert presets[1].modules == pvp

    # Putting an existing name replaces it rather than adding a second row.
    await repo.put_preset(UUID, Preset(name="PvP", modules={}, updated_at=now()))
    assert [p.modules for p in await repo.list_presets(UUID) if p.name == "PvP"] == [{}]

    assert await repo.delete_preset(UUID, "PvP") is True
    assert await repo.delete_preset(UUID, "PvP") is False
    assert [p.name for p in await repo.list_presets(UUID)] == ["anarchy"]
