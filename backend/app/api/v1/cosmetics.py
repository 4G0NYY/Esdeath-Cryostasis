"""Active cosmetics: the hot path.

GET /players/{uuid}/cosmetics is called once per visible player by the client's
CosmeticService, which already keeps a 30s TTL cache. This endpoint serves ETag and
Cache-Control so that cache cooperates rather than duplicating this one (architecture 8),
and adds a batch variant so a full server is one request per refresh instead of one per
player.

The response shape here is a hard contract: the shipped client parses exactly
{"cosmetics": [slug, ...], "cape": "..."} (see CosmeticService.parse). Do not change it.
"""

from __future__ import annotations

import hashlib

from fastapi import APIRouter, Depends, Header, HTTPException, Request, Response
from pydantic import BaseModel

from app.api.deps import get_repo, require_caller, settings_dep
from app.config import Settings
from app.domain.models import CATALOGUE, CosmeticBody, Player, normalize_uuid
from app.repo.base import Repo

router = APIRouter()


def _active(player: Player) -> dict:
    # Sorted so the ETag is stable regardless of set iteration order.
    return {"cosmetics": sorted(player.cosmetics), "cape": player.cape}


def _etag(payload: dict) -> str:
    raw = repr(payload).encode("utf-8")
    return '"' + hashlib.sha1(raw).hexdigest() + '"'


class BatchBody(BaseModel):
    uuids: list[str]


@router.post("/players/cosmetics/batch")
async def batch_cosmetics(body: BatchBody, repo: Repo = Depends(get_repo)) -> dict:
    # One request per refresh on a full server. Keyed by the UUID as sent, so the client can
    # match responses back to the players it asked about without re-normalizing.
    players = await repo.get_players(body.uuids)
    return {"players": {uuid: _active(player) for uuid, player in players.items()}}


@router.get("/players/{uuid}/cosmetics", response_model=None)
async def get_cosmetics(
    uuid: str,
    response: Response,
    repo: Repo = Depends(get_repo),
    settings: Settings = Depends(settings_dep),
    if_none_match: str | None = Header(default=None),
) -> Response | dict:
    player = await repo.get_player(normalize_uuid(uuid))
    payload = _active(player)
    etag = _etag(payload)

    cache_control = f"public, max-age={settings.cosmetics_cache_seconds}"
    if if_none_match == etag:
        return Response(status_code=304, headers={"ETag": etag, "Cache-Control": cache_control})

    response.headers["ETag"] = etag
    response.headers["Cache-Control"] = cache_control
    return payload


@router.post("/players/{uuid}/cosmetics")
async def add_cosmetic(
    body: CosmeticBody, caller: str = Depends(require_caller), repo: Repo = Depends(get_repo)
) -> dict:
    # Only catalogue members are addable: the active set must stay a subset of CATALOGUE so
    # the client never receives a slug it has no model/texture for. body.cosmetic is already
    # lowercased by CosmeticBody, matching the catalogue keys.
    if body.cosmetic not in CATALOGUE:
        raise HTTPException(status_code=400, detail=f"unknown cosmetic: {body.cosmetic}")
    ok = await repo.add_cosmetic(caller, body.cosmetic)
    return {"ok": ok}


@router.delete("/players/{uuid}/cosmetics/{cosmetic}")
async def remove_cosmetic(
    cosmetic: str, caller: str = Depends(require_caller), repo: Repo = Depends(get_repo)
) -> dict:
    ok = await repo.remove_cosmetic(caller, cosmetic.strip().lower())
    return {"ok": ok}


@router.get("/players/{uuid}/cosmetics/{cosmetic}")
async def has_cosmetic(uuid: str, cosmetic: str) -> dict:
    # With cosmetics free, ownership no longer exists: this degrades to a catalogue check
    # and is true for any real cosmetic (architecture 9). Kept only for contract
    # compatibility with the original hasThePlayerTheCosmetic call.
    return {"has": cosmetic.strip().lower() in CATALOGUE}
