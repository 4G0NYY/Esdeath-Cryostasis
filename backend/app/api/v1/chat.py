"""Global chat: one Cryostasis-wide channel that ignores which game server a player is on.

Delivery is a polled message log, not a socket. The service runs as several stateless replicas
behind a routing mesh, so a broadcast would need something for the replicas to broadcast
through: Postgres LISTEN/NOTIFY does not survive pgbouncer's transaction pooling, and Redis is
the dependency architecture 6 refused to take on. A monotonic id column instead makes "what is
new for me" a per-client question that any replica can answer from the database alone, and the
long poll below keeps an idle reader to roughly one request per wait window rather than one per
poll interval.

Moderation is what the original relay lacked and why architecture 1 dropped chat in the first
place: posting requires the session proof, staff ranks can mute and delete, messages are length
capped and stripped of control characters, and every sender has their own rate bucket.
"""

from __future__ import annotations

import asyncio
import time
from datetime import timedelta

from fastapi import APIRouter, Depends, HTTPException, Query, Response

from app.api.deps import (
    RateLimiter,
    get_chat_limiter,
    get_repo,
    require_authenticated,
    require_staff,
    settings_dep,
)
from app.config import Settings
from app.domain.models import (
    ChatBody,
    ChatMessage,
    Mute,
    MuteBody,
    normalize_uuid,
    now,
    presence_view,
    resolve_rank,
)
from app.repo.base import Repo

router = APIRouter()

# The uuid a dev instance posts under when nothing identifies the caller. Auth off means there
# is no token to take a subject from, and the nil uuid is obviously not a real account.
ANONYMOUS = "00000000-0000-0000-0000-000000000000"


def _cursor(messages: list[ChatMessage], after: int | None, latest: int) -> int:
    """The id the client should send as `after` next time.

    Not simply "the last id you got": an empty first read has to advance the client to the live
    end of the log, or it would ask for the whole backlog again on every poll.
    """
    if messages:
        return messages[-1].id
    return after if after is not None else latest


@router.get("/chat")
async def read_chat(
    after: int | None = Query(default=None, ge=0),
    limit: int | None = Query(default=None, ge=1),
    wait: float = Query(default=0.0, ge=0.0),
    repo: Repo = Depends(get_repo),
    settings: Settings = Depends(settings_dep),
) -> dict:
    """Messages after `after`, oldest first. With no cursor, the tail of the log as a backlog.

    `wait` holds the request open until something arrives, up to the configured ceiling. It only
    applies once the client holds a cursor, since a first read has the backlog to return and
    nothing to wait for.
    """
    capped = min(limit or settings.chat_history_limit, settings.chat_history_limit)
    messages = await repo.chat_since(after, capped)

    if not messages and after is not None and wait > 0:
        deadline = time.monotonic() + min(wait, settings.chat_poll_max_wait_seconds)
        while time.monotonic() < deadline:
            await asyncio.sleep(settings.chat_poll_interval_seconds)
            # Compare ids before fetching rows, so an idle channel costs one cheap aggregate per
            # interval rather than a range scan that returns nothing.
            if await repo.latest_chat_id() > after:
                messages = await repo.chat_since(after, capped)
                break

    latest = messages[-1].id if messages else await repo.latest_chat_id()
    return {
        "messages": [m.model_dump(mode="json") for m in messages],
        "cursor": _cursor(messages, after, latest),
    }


@router.post("/chat")
async def post_chat(
    body: ChatBody,
    caller: str | None = Depends(require_authenticated),
    repo: Repo = Depends(get_repo),
    settings: Settings = Depends(settings_dep),
    limiter: RateLimiter = Depends(get_chat_limiter),
) -> dict:
    """Append a line to the global channel.

    With auth on, who the sender is comes entirely from the token and the stored player record,
    never from the body, so a caller cannot post under another player's name or rank.
    """
    if len(body.message) > settings.chat_max_length:
        raise HTTPException(
            status_code=400, detail=f"message longer than {settings.chat_max_length} characters"
        )

    uuid = caller or normalize_uuid(body.uuid or ANONYMOUS)
    player = await repo.get_player(uuid)

    mute = await repo.get_mute(uuid)
    if mute is not None and mute.is_active():
        detail = f"muted until {mute.until.isoformat()}"
        raise HTTPException(status_code=403, detail=f"{detail}: {mute.reason}" if mute.reason else detail)

    if not limiter.check(uuid, time.monotonic()):
        raise HTTPException(status_code=429, detail="sending too fast")

    # The stored name wins, since the session proof wrote it. The body name is the dev-instance
    # fallback for a player who never completed a handshake and so has no stored name.
    username = player.username or (body.username or "").strip() or "Player"
    rank = resolve_rank(player.rank)

    # Posting proves the client is alive, so it counts as a heartbeat, but it is not in-world
    # activity and so does not clear AFK. That is what makes the stamped state worth reading: it
    # says whether the sender's character was parked while they typed, which on a channel
    # spanning many game servers is the difference between someone playing and someone watching.
    await repo.touch(uuid)
    state = presence_view(
        player.model_copy(update={"last_seen": now()}),
        settings.presence_window_seconds,
        settings.afk_after_seconds,
    ).state

    stored = await repo.post_chat(
        ChatMessage(
            id=0,  # assigned by the store; the log's id is the poll cursor
            uuid=uuid,
            username=username,
            rank=rank.name,
            color=rank.color,
            message=body.message,
            state=state,
            at=now(),
        )
    )

    # Swept here rather than on a schedule, the same way auth nonces are: the write path is the
    # only thing that grows the table, and the filter is indexed.
    await repo.prune_chat(now() - timedelta(hours=settings.chat_retention_hours))
    return stored.model_dump(mode="json")


@router.delete("/chat/{message_id}", status_code=204)
async def delete_message(
    message_id: int, _: str = Depends(require_staff), repo: Repo = Depends(get_repo)
) -> Response:
    # A hard delete, not a tombstone: ids are the poll cursor, so a row that lingers to be
    # skipped would need a second column and every read would have to filter on it. A client
    # that already pulled the line keeps it, which is true of any chat that cannot retract.
    if not await repo.delete_chat(message_id):
        raise HTTPException(status_code=404, detail="no such message")
    return Response(status_code=204)


async def _resolve_player(repo: Repo, raw: str) -> tuple[str, str]:
    """A moderation target given either a UUID or a name, as (uuid, username).

    Moderators read names off chat lines and never see a UUID, so the name path is the one that
    matters; it resolves through the stored, Mojang-verified username.
    """
    candidate = normalize_uuid(raw)
    if len(candidate) == 36 and candidate.count("-") == 4:
        player = await repo.get_player(candidate)
        return candidate, player.username

    player = await repo.find_by_username(raw)
    if player is None:
        raise HTTPException(status_code=404, detail=f"no player known as {raw}")
    return player.uuid, player.username


@router.post("/chat/mutes")
async def mute_player(
    body: MuteBody, actor: str = Depends(require_staff), repo: Repo = Depends(get_repo)
) -> dict:
    uuid, username = await _resolve_player(repo, body.player)
    mute = Mute(
        uuid=uuid,
        username=username,
        until=now() + timedelta(minutes=body.minutes),
        reason=body.reason,
        by=actor,
    )
    await repo.set_mute(mute)
    return mute.model_dump(mode="json")


@router.delete("/chat/mutes/{player}")
async def unmute_player(
    player: str, _: str = Depends(require_staff), repo: Repo = Depends(get_repo)
) -> dict:
    uuid, _username = await _resolve_player(repo, player)
    return {"ok": await repo.clear_mute(uuid)}


@router.get("/chat/mutes")
async def list_mutes(_: str = Depends(require_staff), repo: Repo = Depends(get_repo)) -> dict:
    # Expired rows are filtered by the store, so this is the list that is actually in force.
    return {"mutes": [m.model_dump(mode="json") for m in await repo.active_mutes()]}
