"""Rank assignment: the admin surface and the registry it validates against.

Ranks are the one player field that is granted rather than chosen, so what these tests pin down
is the negative case: a player holding a perfectly valid token for their own UUID still cannot
set their own rank.
"""

from __future__ import annotations

import pytest

from app.config import Settings

from .conftest import make_client
from .test_auth import OWNER, auth_settings, no_mojang, obtain_token  # noqa: F401

ADMIN_TOKEN = "admin-token-not-the-default-0123456789"


def admin_settings() -> Settings:
    return Settings(database_url="", require_auth=False, admin_token=ADMIN_TOKEN)


async def test_rank_registry_is_served(client):
    ranks = {r["name"]: r for r in (await client.get("/api/ranks")).json()["ranks"]}
    assert ranks["Default"]["staff"] is False
    assert ranks["Admin"]["staff"] is True
    # Every entry carries the colour the client tags chat with, so it never needs its own copy.
    assert all(r["color"].startswith("#") for r in ranks.values())


async def test_admin_sets_rank(uuid):
    async with make_client(admin_settings()) as client:
        r = await client.put(
            f"/api/players/{uuid}/rank",
            json={"rank": "chef"},
            headers={"X-Admin-Token": ADMIN_TOKEN},
        )
        assert r.status_code == 204
        # Stored in the registry's spelling, not the caller's casing.
        assert (await client.get(f"/api/players/{uuid}/rank")).json()["rank"] == "Chef"


async def test_unknown_rank_rejected(uuid):
    async with make_client(admin_settings()) as client:
        r = await client.put(
            f"/api/players/{uuid}/rank",
            json={"rank": "supreme-overlord"},
            headers={"X-Admin-Token": ADMIN_TOKEN},
        )
        assert r.status_code == 422
        assert (await client.get(f"/api/players/{uuid}/rank")).json()["rank"] == "Default"


async def test_rank_write_needs_the_admin_token(uuid):
    async with make_client(admin_settings()) as client:
        assert (await client.put(f"/api/players/{uuid}/rank", json={"rank": "Chef"})).status_code == 403
        r = await client.put(
            f"/api/players/{uuid}/rank",
            json={"rank": "Chef"},
            headers={"X-Admin-Token": "wrong"},
        )
        assert r.status_code == 403


async def test_unset_admin_token_disables_the_surface(client, uuid):
    # The default fixture leaves admin_token empty. An unset token must close the surface, not
    # open it: a deployment that forgot to configure one cannot have its ranks rewritten.
    r = await client.put(
        f"/api/players/{uuid}/rank", json={"rank": "Chef"}, headers={"X-Admin-Token": ""}
    )
    assert r.status_code == 403


async def test_player_cannot_promote_themselves(no_mojang):
    settings = auth_settings()
    settings.admin_token = ADMIN_TOKEN
    async with make_client(settings) as client:
        token = await obtain_token(client, OWNER)
        # A token that authorizes every other write on this UUID still does not carry rank.
        r = await client.put(
            f"/api/players/{OWNER}/rank",
            json={"rank": "Admin"},
            headers={"Authorization": f"Bearer {token}"},
        )
        assert r.status_code == 403
        assert (await client.get(f"/api/players/{OWNER}/rank")).json()["rank"] == "Default"
