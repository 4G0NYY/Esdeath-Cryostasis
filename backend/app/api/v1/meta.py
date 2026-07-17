"""Version and cape-catalogue endpoints. Both are read-only and unauthenticated."""

from __future__ import annotations

from fastapi import APIRouter, Depends

from app.api.deps import get_repo, settings_dep
from app.config import Settings
from app.domain.models import CATALOGUE
from app.repo.base import Repo
from app.storage.cdn import texture_url

router = APIRouter()


@router.get("/version")
async def version(settings: Settings = Depends(settings_dep)) -> dict:
    return {"version": settings.version}


@router.get("/cosmetics")
async def catalogue(settings: Settings = Depends(settings_dep)) -> dict:
    # The full catalogue for the in-game cosmetics menu (Phase 4). Read-only, and the
    # texture URL is built from the object key so the client fetches bytes from the CDN
    # directly (architecture 7). It is null for the bundled cosmetics, whose bytes the
    # client still loads from its own mod resources.
    return {
        "cosmetics": [
            {
                "slug": entry.slug,
                "kind": entry.kind,
                "rarity": entry.rarity,
                "model": entry.model,
                "texture_url": texture_url(settings.cdn_base_url, entry.texture_key),
            }
            for entry in CATALOGUE.values()
        ]
    }


@router.get("/capes")
async def capes(repo: Repo = Depends(get_repo)) -> dict:
    # The catalogue is stored as "name-rarity" strings (Store seeded classic-Default and
    # friends). The contract in docs/backend-api.md returns objects, so split on the last
    # hyphen into name and rarity. A string with no hyphen falls back to Default rarity.
    out = []
    for entry in await repo.capes():
        name, sep, rarity = entry.rpartition("-")
        if sep:
            out.append({"name": name, "rarity": rarity})
        else:
            out.append({"name": entry, "rarity": "Default"})
    return {"capes": out}
