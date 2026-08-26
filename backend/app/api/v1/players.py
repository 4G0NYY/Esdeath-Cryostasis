"""Player state and presence: status, rank, server, cape, and the online lists.

Reads are open (any caller may look up any player, as the renderer must). Writes go through
require_caller, so with auth on only the owning account can change its own record. Rank is the
one exception in both directions: it is granted rather than chosen, so it takes the admin token
and is the only write a player cannot make to their own row.
"""

from __future__ import annotations

from fastapi import APIRouter, Depends, Response

from app.api.deps import get_repo, require_admin, require_caller, settings_dep
from app.config import Settings
from app.domain.models import (
    CapeBody,
    PresenceBatchBody,
    PresenceBody,
    RankBody,
    ServerBody,
    StatusBody,
    normalize_uuid,
    presence_view,
    resolve_rank,
)
from app.repo.base import Repo

router = APIRouter()


# Static paths first so "online" and "presence" are never parsed as a {uuid}.
@router.get("/players/online")
async def online_players(
    repo: Repo = Depends(get_repo), settings: Settings = Depends(settings_dep)
) -> dict:
    players = await repo.online_players(settings.presence_window_seconds)
    return {"players": players, "count": len(players)}


@router.get("/players/presence")
async def presence_roster(
    repo: Repo = Depends(get_repo), settings: Settings = Depends(settings_dep)
) -> dict:
    """Everyone whose client is currently beating, AFK players included.

    Separate from /players/online rather than an extra field on it: that route answers the
    recovered getOnlinePlayingPlayers with a bare list of UUIDs and the shipped client parses it,
    so it stays exactly as it is. This one is the roster, and it carries enough per player to
    render a line without a follow-up call.

    AFK is a sub-state of connected, so an idle player appears here. Dropping them would be the
    same mistake a stored online flag makes: the roster would say nobody is around whenever
    everybody is merely standing still.
    """
    players = [
        presence_view(p, settings.presence_window_seconds, settings.afk_after_seconds)
        for p in await repo.presence(settings.presence_window_seconds)
    ]
    return {
        "players": [p.model_dump(mode="json") for p in players],
        "count": len(players),
        "online": sum(1 for p in players if p.state == "online"),
        "afk": sum(1 for p in players if p.state == "afk"),
    }


@router.post("/players/presence/batch")
async def presence_batch(
    body: PresenceBatchBody,
    repo: Repo = Depends(get_repo),
    settings: Settings = Depends(settings_dep),
) -> dict:
    """Presence for a named set of players, including offline ones.

    The roster above answers "who is around"; this answers "what about these players", which is
    what the nametag and chat surfaces need: they already know which UUIDs are in front of them
    and would otherwise pull the whole roster to find three rows in it. Keyed by the UUID as
    sent, matching the cosmetics batch, so a caller matches replies back without re-normalizing.
    """
    players = await repo.get_players(body.uuids)
    return {
        "players": {
            uuid: presence_view(
                player, settings.presence_window_seconds, settings.afk_after_seconds
            ).model_dump(mode="json")
            for uuid, player in players.items()
        }
    }


@router.get("/servers/{server}/players")
async def players_on_server(
    server: str, repo: Repo = Depends(get_repo), settings: Settings = Depends(settings_dep)
) -> dict:
    players = await repo.players_on_server(server, settings.presence_window_seconds)
    return {"players": players}


@router.post("/players/{uuid}/online", status_code=204)
async def mark_online(
    body: PresenceBody | None = None,
    caller: str = Depends(require_caller),
    repo: Repo = Depends(get_repo),
) -> Response:
    """Presence heartbeat.

    Records last_seen so online is derived from a fresh timestamp (architecture 6) rather than a
    boolean that leaks on a crash, and last_active when the body says the player did something,
    which is what AFK is derived from.

    The body is optional so the recovered addMe contract, which had none, still beats: such a
    caller is simply always AFK, which is the honest answer for a client that cannot report
    otherwise.
    """
    await repo.touch(caller, active=body.active if body else False)
    if body is not None and body.server is not None:
        await repo.set_server(caller, body.server)
    return Response(status_code=204)


@router.put("/players/{uuid}/server", status_code=204)
async def set_server(
    body: ServerBody, caller: str = Depends(require_caller), repo: Repo = Depends(get_repo)
) -> Response:
    await repo.set_server(caller, body.server)
    return Response(status_code=204)


@router.put("/players/{uuid}/status", status_code=204)
async def set_status(
    body: StatusBody, caller: str = Depends(require_caller), repo: Repo = Depends(get_repo)
) -> Response:
    await repo.set_status(caller, body.status)
    return Response(status_code=204)


@router.get("/players/{uuid}/status")
async def get_status(
    uuid: str, repo: Repo = Depends(get_repo), settings: Settings = Depends(settings_dep)
) -> dict:
    # status is the contract field the recovered getTheStatusOfThePlayer returned and stays the
    # free text the player set. Everything beside it is derived and additive, so a caller written
    # against the recovered shape still reads correctly.
    player = await repo.get_player(normalize_uuid(uuid))
    view = presence_view(player, settings.presence_window_seconds, settings.afk_after_seconds)
    return view.model_dump(mode="json")


@router.get("/players/{uuid}/rank")
async def get_rank(uuid: str, repo: Repo = Depends(get_repo)) -> dict:
    # rank is the contract field the recovered getRankofPlayer returned; color and staff are
    # additive, so the shipped client keeps parsing this while the rebuilt one can colour a
    # chat tag without carrying its own copy of the registry.
    entry = resolve_rank((await repo.get_player(normalize_uuid(uuid))).rank)
    return {"rank": entry.name, "color": entry.color, "staff": entry.staff}


@router.put("/players/{uuid}/rank", status_code=204, dependencies=[Depends(require_admin)])
async def set_rank(uuid: str, body: RankBody, repo: Repo = Depends(get_repo)) -> Response:
    # Admin token rather than require_caller: a rank is granted to a player, so the one caller
    # who must not be able to set it is the player themselves.
    await repo.set_rank(normalize_uuid(uuid), body.rank)
    return Response(status_code=204)


@router.put("/players/{uuid}/cape", status_code=204)
async def set_cape(
    body: CapeBody, caller: str = Depends(require_caller), repo: Repo = Depends(get_repo)
) -> Response:
    await repo.set_cape(caller, body.cape)
    return Response(status_code=204)
