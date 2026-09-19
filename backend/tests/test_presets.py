"""Preset sync: the envelope rules (names, count, size) and that a preset is private to its owner."""

from __future__ import annotations

from .conftest import make_client
from .test_auth import OTHER, OWNER, auth_settings, no_mojang, obtain_token  # noqa: F401

PVP = {
    "killaura": {"enabled": True, "settings": {"Reach": 3.5, "Targets": "Players"}},
    "fps": {"enabled": True, "settings": {"Label": True}},
    "xray": {"enabled": False, "settings": {}},
}


async def test_starts_empty(client, uuid):
    r = await client.get(f"/api/players/{uuid}/presets")
    assert r.status_code == 200
    assert r.json() == {"presets": []}


async def test_put_list_roundtrip(client, uuid):
    assert (await client.put(f"/api/players/{uuid}/presets/PvP", json={"modules": PVP})).status_code == 204

    [preset] = (await client.get(f"/api/players/{uuid}/presets")).json()["presets"]
    assert preset["name"] == "PvP"
    assert preset["modules"] == PVP
    assert preset["updated_at"]


async def test_put_overwrites(client, uuid):
    await client.put(f"/api/players/{uuid}/presets/PvP", json={"modules": PVP})
    await client.put(f"/api/players/{uuid}/presets/PvP", json={"modules": {}})

    presets = (await client.get(f"/api/players/{uuid}/presets")).json()["presets"]
    assert [(p["name"], p["modules"]) for p in presets] == [("PvP", {})]


async def test_listed_by_name_ignoring_case(client, uuid):
    for name in ["building", "Anarchy", "PvP"]:
        await client.put(f"/api/players/{uuid}/presets/{name}", json={"modules": {}})
    presets = (await client.get(f"/api/players/{uuid}/presets")).json()["presets"]
    assert [p["name"] for p in presets] == ["Anarchy", "building", "PvP"]


async def test_name_with_space_and_unicode(client, uuid):
    assert (await client.put(f"/api/players/{uuid}/presets/Größe%20Bau", json={"modules": {}})).status_code == 204
    presets = (await client.get(f"/api/players/{uuid}/presets")).json()["presets"]
    assert presets[0]["name"] == "Größe Bau"
    assert (await client.delete(f"/api/players/{uuid}/presets/Größe%20Bau")).json() == {"ok": True}


async def test_delete(client, uuid):
    await client.put(f"/api/players/{uuid}/presets/PvP", json={"modules": PVP})
    assert (await client.delete(f"/api/players/{uuid}/presets/PvP")).json() == {"ok": True}
    assert (await client.delete(f"/api/players/{uuid}/presets/PvP")).json() == {"ok": False}
    assert (await client.get(f"/api/players/{uuid}/presets")).json() == {"presets": []}


async def test_bad_names_rejected(client, uuid):
    for name in ["%20", "a" * 25, "bad*name", "QoL", "qol"]:
        r = await client.put(f"/api/players/{uuid}/presets/{name}", json={"modules": {}})
        assert r.status_code == 400, name


async def test_count_is_capped_but_overwrite_still_works(client, uuid):
    for i in range(32):
        r = await client.put(f"/api/players/{uuid}/presets/p{i}", json={"modules": {}})
        assert r.status_code == 204
    assert (await client.put(f"/api/players/{uuid}/presets/one-more", json={"modules": {}})).status_code == 409
    assert (await client.put(f"/api/players/{uuid}/presets/p0", json={"modules": PVP})).status_code == 204


async def test_oversized_modules_rejected(client, uuid):
    huge = {"xray": {"enabled": True, "settings": {"Extra": "x" * 40_000}}}
    r = await client.put(f"/api/players/{uuid}/presets/Huge", json={"modules": huge})
    assert r.status_code == 422


async def test_presets_are_private(no_mojang):  # noqa: F811
    async with make_client(auth_settings()) as client:
        assert (await client.get(f"/api/players/{OWNER}/presets")).status_code == 401

        token = await obtain_token(client, OWNER)
        auth = {"Authorization": f"Bearer {token}"}
        r = await client.put(f"/api/players/{OWNER}/presets/PvP", json={"modules": PVP}, headers=auth)
        assert r.status_code == 204
        assert len((await client.get(f"/api/players/{OWNER}/presets", headers=auth)).json()["presets"]) == 1

        # A valid token for one player reads and writes nothing of another's.
        assert (await client.get(f"/api/players/{OTHER}/presets", headers=auth)).status_code == 403
        r = await client.put(f"/api/players/{OTHER}/presets/PvP", json={"modules": {}}, headers=auth)
        assert r.status_code == 403
