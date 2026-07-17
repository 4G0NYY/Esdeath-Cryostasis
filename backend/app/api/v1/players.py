"""Player state and presence: status, rank, server, cape, and the online lists.

Reads are open (any caller may look up any player, as the renderer must). Writes go through
require_caller, so with auth on only the owning account can change its own record.
"""

from __future__ import annotations

from fastapi import APIRouter, Depends, Response

from app.api.deps import get_repo, require_caller, settings_dep
from app.config import Settings
from app.domain.models import CapeBody, ServerBody, StatusBody, normalize_uuid
from app.repo.base import Repo

router = APIRouter()


# Static path first so "online" is never parsed as a {uuid}.
@router.get("/players/online")
async def online_players(
    repo: Repo = Depends(get_repo), settings: Settings = Depends(settings_dep)
) -> dict:
    players = await repo.online_players(settings.presence_window_seconds)
    return {"players": players, "count": len(players)}


@router.get("/servers/{server}/players")
async def players_on_server(
    server: str, repo: Repo = Depends(get_repo), settings: Settings = Depends(settings_dep)
) -> dict:
    players = await repo.players_on_server(server, settings.presence_window_seconds)
    return {"players": players}


@router.post("/players/{uuid}/online", status_code=204)
async def mark_online(
    caller: str = Depends(require_caller), repo: Repo = Depends(get_repo)
) -> Response:
    # Presence heartbeat: records last_seen so online is derived from a fresh timestamp
    # (architecture 6) rather than a boolean that leaks on a crash.
    await repo.touch(caller)
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
async def get_status(uuid: str, repo: Repo = Depends(get_repo)) -> dict:
    return {"status": (await repo.get_player(normalize_uuid(uuid))).status}


@router.get("/players/{uuid}/rank")
async def get_rank(uuid: str, repo: Repo = Depends(get_repo)) -> dict:
    return {"rank": (await repo.get_player(normalize_uuid(uuid))).rank}


@router.put("/players/{uuid}/cape", status_code=204)
async def set_cape(
    body: CapeBody, caller: str = Depends(require_caller), repo: Repo = Depends(get_repo)
) -> Response:
    await repo.set_cape(caller, body.cape)
    return Response(status_code=204)
