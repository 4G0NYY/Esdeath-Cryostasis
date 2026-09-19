"""Module presets, synced so a player's setups follow them to every machine they play on.

Unlike cosmetics, reads go through require_caller too: a cosmetic is on show to everyone the
player meets, but a preset is how somebody has set their client up, and nobody else needs it.
"""

from __future__ import annotations

from fastapi import APIRouter, Depends, HTTPException, Response

from app.api.deps import get_repo, require_caller
from app.domain.models import now
from app.domain.presets import MAX_PRESETS, Preset, PresetBody, validate_name
from app.repo.base import Repo

router = APIRouter(prefix="/players/{uuid}/presets", tags=["presets"])


def _checked(name: str) -> str:
    try:
        return validate_name(name)
    except ValueError as exc:
        raise HTTPException(status_code=400, detail=str(exc)) from exc


@router.get("")
async def list_presets(caller: str = Depends(require_caller), repo: Repo = Depends(get_repo)) -> dict:
    return {"presets": [p.model_dump(mode="json") for p in await repo.list_presets(caller)]}


@router.put("/{name}", status_code=204)
async def put_preset(
    name: str,
    body: PresetBody,
    caller: str = Depends(require_caller),
    repo: Repo = Depends(get_repo),
) -> Response:
    name = _checked(name)
    existing = {p.name for p in await repo.list_presets(caller)}
    # Overwriting is always allowed, so a player at the cap can still update what they have.
    if name not in existing and len(existing) >= MAX_PRESETS:
        raise HTTPException(status_code=409, detail=f"at most {MAX_PRESETS} presets")
    await repo.put_preset(caller, Preset(name=name, modules=body.modules, updated_at=now()))
    return Response(status_code=204)


@router.delete("/{name}")
async def delete_preset(
    name: str, caller: str = Depends(require_caller), repo: Repo = Depends(get_repo)
) -> dict:
    return {"ok": await repo.delete_preset(caller, name.strip())}
