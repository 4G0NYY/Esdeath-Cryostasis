"""Global chat: the polled log, the identity rules, and moderation.

The point of these is the delta against the original relay, which is why chat was dropped in
the first place (architecture 1): a sender is who Mojang says they are, a muted player is
silent, a staff rank can act, and nobody can flood the channel.
"""

from __future__ import annotations

import asyncio

from app.config import Settings

from .conftest import make_client
from .test_auth import OTHER, OWNER, auth_settings, no_mojang, obtain_token  # noqa: F401

STAFF_TOKEN = "admin-token-not-the-default-0123456789"


def chat_settings(**overrides) -> Settings:
    return Settings(database_url="", require_auth=False, admin_token=STAFF_TOKEN, **overrides)


async def post(client, message: str, **body):
    return await client.post("/api/chat", json={"message": message, **body})


async def test_post_then_read_backlog(client):
    r = await post(client, "hello swarm", username="Ray")
    assert r.status_code == 200
    stored = r.json()
    assert stored["message"] == "hello swarm"
    assert stored["username"] == "Ray"
    # Every message carries its sender's rank and colour, so the client renders a tag with no
    # second lookup.
    assert stored["rank"] == "Default" and stored["color"] == "#9AA7B8"

    body = (await client.get("/api/chat")).json()
    assert [m["message"] for m in body["messages"]] == ["hello swarm"]
    # The cursor is what the client sends back as `after`.
    assert body["cursor"] == stored["id"]


async def test_message_carries_the_senders_presence_state(client, uuid):
    # Posting is proof the client is alive, so a line is never stamped offline, but it is not
    # in-world activity, so a player whose character has been parked is stamped afk. That is the
    # whole information the tag carries: someone is watching the channel, not playing.
    async with make_client(chat_settings(afk_after_seconds=1)) as c:
        await c.post(f"/api/players/{uuid}/online", json={"active": True})
        assert (await post(c, "just landed", uuid=uuid)).json()["state"] == "online"

        await asyncio.sleep(1.1)
        assert (await post(c, "still here, not playing", uuid=uuid)).json()["state"] == "afk"


async def test_a_never_seen_sender_is_not_stamped_offline(client):
    # A dev instance and a client with the presence module off both post without ever beating.
    # The line still came from a live client, so stamping it offline would be a lie.
    assert (await post(client, "hello", username="Ray")).json()["state"] == "afk"


async def test_cursor_returns_only_new_messages(client):
    await post(client, "first")
    cursor = (await client.get("/api/chat")).json()["cursor"]

    await post(client, "second")
    body = (await client.get(f"/api/chat?after={cursor}")).json()
    assert [m["message"] for m in body["messages"]] == ["second"]

    # Polled again with the advanced cursor, nothing repeats.
    assert (await client.get(f"/api/chat?after={body['cursor']}")).json()["messages"] == []


async def test_empty_first_read_advances_to_the_live_end(client):
    # With no messages at all the cursor is 0, so the first poll does not re-request a backlog
    # it already knows is empty.
    assert (await client.get("/api/chat")).json() == {"messages": [], "cursor": 0}


async def test_message_is_cleaned_and_length_capped(client):
    assert (await post(client, "  hi there  ")).json()["message"] == "hi there"
    # Control characters would let a line forge the client's formatting or break its layout, so
    # a message made only of them is empty and refused.
    assert (await post(client, "\t\n ")).status_code == 422

    r = await post(client, "x" * 300)
    assert r.status_code == 400 and "longer than" in r.json()["detail"]


async def test_rate_limit_is_its_own_bucket():
    async with make_client(chat_settings(chat_rate_per_minute=3)) as limited:
        for _ in range(3):
            assert (await post(limited, "spam")).status_code == 200
        assert (await post(limited, "spam")).status_code == 429
        # The general limiter is untouched, so ordinary writes still go through.
        uuid = "11111111-2222-3333-4444-555555555555"
        assert (await limited.put(f"/api/players/{uuid}/status", json={"status": "ok"})).status_code == 204


async def test_identity_comes_from_the_token_not_the_body(no_mojang):
    settings = auth_settings()
    settings.admin_token = STAFF_TOKEN
    async with make_client(settings) as client:
        token = await obtain_token(client, OWNER)
        r = await client.post(
            "/api/chat",
            json={"message": "hi", "uuid": OTHER, "username": "Impostor"},
            headers={"Authorization": f"Bearer {token}"},
        )
        assert r.status_code == 200
        stored = r.json()
        # The body's uuid and username are ignored; the handshake recorded "Notch" for OWNER.
        assert stored["uuid"] == OWNER
        assert stored["username"] == "Notch"


async def test_posting_requires_a_token_when_auth_is_on(no_mojang):
    async with make_client(auth_settings()) as client:
        assert (await client.post("/api/chat", json={"message": "hi"})).status_code == 401
        # Reads stay open, as they do everywhere else in the service.
        assert (await client.get("/api/chat")).status_code == 200


async def test_mute_silences_a_player(client, uuid):
    await post(client, "before the mute", uuid=uuid, username="Ray")

    r = await client.post(
        "/api/chat/mutes",
        json={"player": uuid, "minutes": 30, "reason": "spam"},
        headers={"X-Admin-Token": STAFF_TOKEN},
    )
    assert r.status_code == 200 and r.json()["reason"] == "spam"

    blocked = await post(client, "after the mute", uuid=uuid)
    assert blocked.status_code == 403 and "muted until" in blocked.json()["detail"]

    listed = (await client.get("/api/chat/mutes", headers={"X-Admin-Token": STAFF_TOKEN})).json()
    assert [m["uuid"] for m in listed["mutes"]] == [uuid]

    assert (
        await client.delete(f"/api/chat/mutes/{uuid}", headers={"X-Admin-Token": STAFF_TOKEN})
    ).json() == {"ok": True}
    assert (await post(client, "unmuted", uuid=uuid)).status_code == 200


async def test_mute_by_username(no_mojang):
    settings = auth_settings()
    settings.admin_token = STAFF_TOKEN
    async with make_client(settings) as client:
        # The handshake is what records a username, which is what makes moderating by the name a
        # moderator reads off a chat line possible at all.
        await obtain_token(client, OWNER)
        r = await client.post(
            "/api/chat/mutes",
            json={"player": "notch", "minutes": 5},
            headers={"X-Admin-Token": STAFF_TOKEN},
        )
        assert r.status_code == 200 and r.json()["uuid"] == OWNER

        missing = await client.post(
            "/api/chat/mutes",
            json={"player": "nobody-by-that-name", "minutes": 5},
            headers={"X-Admin-Token": STAFF_TOKEN},
        )
        assert missing.status_code == 404


async def test_staff_rank_can_moderate_without_the_admin_token(no_mojang):
    settings = auth_settings()
    settings.admin_token = STAFF_TOKEN
    async with make_client(settings) as client:
        token = await obtain_token(client, OWNER)
        # A plain player's token is not enough.
        assert (
            await client.get("/api/chat/mutes", headers={"Authorization": f"Bearer {token}"})
        ).status_code == 403

        await client.put(
            f"/api/players/{OWNER}/rank",
            json={"rank": "Mod"},
            headers={"X-Admin-Token": STAFF_TOKEN},
        )
        assert (
            await client.get("/api/chat/mutes", headers={"Authorization": f"Bearer {token}"})
        ).status_code == 200


async def test_staff_deletes_a_message(client):
    posted = (await post(client, "regrettable")).json()
    r = await client.delete(f"/api/chat/{posted['id']}", headers={"X-Admin-Token": STAFF_TOKEN})
    assert r.status_code == 204
    assert (await client.get("/api/chat")).json()["messages"] == []
    # Already gone, so a repeat is a 404 rather than a silent success.
    assert (
        await client.delete(f"/api/chat/{posted['id']}", headers={"X-Admin-Token": STAFF_TOKEN})
    ).status_code == 404


async def test_long_poll_returns_when_a_message_lands(client):
    cursor = (await client.get("/api/chat")).json()["cursor"]

    async def send_shortly():
        await asyncio.sleep(0.1)
        await post(client, "landed mid-poll")

    # The reader is held open with nothing to return, and the write releases it.
    body, _ = await asyncio.gather(client.get(f"/api/chat?after={cursor}&wait=5"), send_shortly())
    assert [m["message"] for m in body.json()["messages"]] == ["landed mid-poll"]


async def test_long_poll_gives_up_at_the_deadline(client):
    cursor = (await client.get("/api/chat")).json()["cursor"]
    body = (await client.get(f"/api/chat?after={cursor}&wait=0.1")).json()
    # An empty result with an unchanged cursor, so the client simply polls again.
    assert body == {"messages": [], "cursor": cursor}
