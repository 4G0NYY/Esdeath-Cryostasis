"""Presence: the derived Online / AFK / Offline state and the two roster endpoints.

The states are derived from two timestamps rather than stored (domain/models.py), so the tests
that matter are the ones that pin down which timestamp decides what. A short AFK window is
configured per test rather than sleeping through the real 300 seconds.
"""

from __future__ import annotations

import asyncio

import pytest_asyncio
from httpx import AsyncClient

from app.config import Settings
from app.domain.models import Player, now

from .conftest import make_client

OTHER = "22222222-3333-4444-5555-666666666666"


@pytest_asyncio.fixture
async def quick(request) -> AsyncClient:
    """A client whose AFK threshold is a fraction of a second, so idleness is testable."""
    async with make_client(
        Settings(database_url="", require_auth=False, afk_after_seconds=1)
    ) as c:
        yield c


async def test_state_starts_offline(client, uuid):
    body = (await client.get(f"/api/players/{uuid}/status")).json()
    assert body["state"] == "offline"
    assert body["last_seen"] is None and body["last_active"] is None


async def test_heartbeat_without_activity_reads_afk(client, uuid):
    # A client that beats but reports nothing is connected and idle, which is the whole reason
    # the flag exists: without it a heartbeat alone could not tell the two apart.
    assert (await client.post(f"/api/players/{uuid}/online", json={"active": False})).status_code == 204
    assert (await client.get(f"/api/players/{uuid}/status")).json()["state"] == "afk"


async def test_bodyless_heartbeat_still_registers(client, uuid):
    # The recovered addMe carried no body. Such a caller stays AFK, which is the honest answer
    # for a client that has no way to report activity, but it must still count as online.
    assert (await client.post(f"/api/players/{uuid}/online")).status_code == 204
    body = (await client.get(f"/api/players/{uuid}/status")).json()
    assert body["state"] == "afk" and body["last_seen"] is not None


async def test_active_heartbeat_reads_online(client, uuid):
    await client.post(f"/api/players/{uuid}/online", json={"active": True})
    assert (await client.get(f"/api/players/{uuid}/status")).json()["state"] == "online"


async def test_activity_lapses_into_afk(quick, uuid):
    await quick.post(f"/api/players/{uuid}/online", json={"active": True})
    assert (await quick.get(f"/api/players/{uuid}/status")).json()["state"] == "online"

    await asyncio.sleep(1.1)
    # A beat with no activity keeps them connected; only the activity timestamp has gone stale.
    await quick.post(f"/api/players/{uuid}/online", json={"active": False})
    assert (await quick.get(f"/api/players/{uuid}/status")).json()["state"] == "afk"


async def test_offline_beats_afk(uuid):
    # Offline dominates: a client that stopped beating is gone whatever it was last doing.
    async with make_client(
        Settings(database_url="", require_auth=False, presence_window_seconds=1)
    ) as c:
        await c.post(f"/api/players/{uuid}/online", json={"active": True})
        await asyncio.sleep(1.1)
        assert (await c.get(f"/api/players/{uuid}/status")).json()["state"] == "offline"


async def test_heartbeat_carries_the_server(client, uuid):
    await client.post(f"/api/players/{uuid}/online", json={"active": True, "server": "hypixel"})
    assert uuid in (await client.get("/api/servers/hypixel/players")).json()["players"]
    assert (await client.get(f"/api/players/{uuid}/status")).json()["server"] == "hypixel"


async def test_roster_lists_the_connected_with_their_rank(client, uuid):
    await client.put(f"/api/players/{uuid}/status", json={"status": "at the anvil"})
    await client.post(f"/api/players/{uuid}/online", json={"active": True})
    await client.post(f"/api/players/{OTHER}/online", json={"active": False})

    body = (await client.get("/api/players/presence")).json()
    assert body["count"] == 2
    # AFK is a sub-state of connected, so an idle player is on the roster rather than missing
    # from it. Counting them separately is what lets a client show "3 online, 1 away".
    assert body["online"] == 1 and body["afk"] == 1

    by_uuid = {p["uuid"]: p for p in body["players"]}
    assert by_uuid[uuid]["state"] == "online"
    assert by_uuid[uuid]["status"] == "at the anvil"
    assert by_uuid[uuid]["rank"] == "Default" and by_uuid[uuid]["color"] == "#9AA7B8"
    assert by_uuid[OTHER]["state"] == "afk"


async def test_roster_excludes_the_never_seen(client, uuid):
    # Reading a player's record creates it, so a roster that listed every row would fill up with
    # players nobody has heard from since the last restart.
    await client.get(f"/api/players/{uuid}/status")
    assert (await client.get("/api/players/presence")).json() == {
        "players": [],
        "count": 0,
        "online": 0,
        "afk": 0,
    }


async def test_presence_batch_answers_for_named_players(client, uuid):
    await client.post(f"/api/players/{uuid}/online", json={"active": True})
    body = (await client.post("/api/players/presence/batch", json={"uuids": [uuid, OTHER]})).json()

    # Unlike the roster, the batch answers about players it was asked about even when they are
    # offline: the caller can see them in world and needs a row for each.
    assert body["players"][uuid]["state"] == "online"
    assert body["players"][OTHER]["state"] == "offline"


def test_never_active_player_is_afk_not_online():
    # A record with a fresh heartbeat and no activity has nothing saying the player was ever at
    # the keyboard, so it must not read as online.
    player = Player(uuid=OTHER, last_seen=now())
    assert player.presence_state(window_seconds=120, afk_after_seconds=300) == "afk"
