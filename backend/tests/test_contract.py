"""Contract tests asserting the wire shapes in docs/backend-api.md and the deltas in
docs/backend-architecture.md, so the service and the docs cannot drift silently.

Every response shape checked here is one the shipped client or the recovered protocol
depends on. The cosmetics batch-per-player shape in particular is a hard contract with
CosmeticService.parse and must not change.
"""

from __future__ import annotations

import pytest


async def test_health(client):
    r = await client.get("/health")
    assert r.status_code == 200
    assert r.json() == {"status": "ok"}


async def test_version(client):
    r = await client.get("/api/version")
    assert r.status_code == 200
    assert r.json() == {"version": "cryostasis-1"}


async def test_capes_shape(client):
    r = await client.get("/api/capes")
    assert r.status_code == 200
    capes = r.json()["capes"]
    # Contract §4: objects with name and rarity, split from the seeded "name-rarity" strings.
    assert {"name": "classic", "rarity": "Default"} in capes
    assert {"name": "aqua", "rarity": "Premium"} in capes


async def test_catalogue(client):
    r = await client.get("/api/cosmetics")
    assert r.status_code == 200
    by_slug = {c["slug"]: c for c in r.json()["cosmetics"]}
    # The three built cosmetics are all present and carry their model kind.
    assert by_slug["halo"]["model"] == "ring"
    assert by_slug["tophat"]["kind"] == "head"
    # Bundled cosmetics have no stored texture key, so the CDN URL is null (the client loads
    # those bytes from its own mod resources).
    assert by_slug["halo"]["texture_url"] is None


async def test_empty_player_cosmetics(client, uuid):
    r = await client.get(f"/api/players/{uuid}/cosmetics")
    assert r.status_code == 200
    assert r.json() == {"cosmetics": [], "cape": ""}


async def test_cosmetic_add_get_remove_roundtrip(client, uuid):
    # POST -> {"ok": true} on first add, false on repeat (Store.addCosmetic semantics).
    assert (await client.post(f"/api/players/{uuid}/cosmetics", json={"cosmetic": "Halo"})).json() == {"ok": True}
    assert (await client.post(f"/api/players/{uuid}/cosmetics", json={"cosmetic": "halo"})).json() == {"ok": False}

    # Slug is lower-cased on the way in, so the active set carries the canonical form.
    got = (await client.get(f"/api/players/{uuid}/cosmetics")).json()
    assert got == {"cosmetics": ["halo"], "cape": ""}

    assert (await client.delete(f"/api/players/{uuid}/cosmetics/halo")).json() == {"ok": True}
    assert (await client.delete(f"/api/players/{uuid}/cosmetics/halo")).json() == {"ok": False}
    assert (await client.get(f"/api/players/{uuid}/cosmetics")).json()["cosmetics"] == []


async def test_has_cosmetic_is_catalogue_check(client, uuid):
    # Delta §9: hasThePlayerTheCosmetic is now a catalogue membership check, true for any
    # real cosmetic even when the player has not selected it.
    assert (await client.get(f"/api/players/{uuid}/cosmetics/halo")).json() == {"has": True}
    assert (await client.get(f"/api/players/{uuid}/cosmetics/not-a-real-one")).json() == {"has": False}


async def test_cape_roundtrip(client, uuid):
    assert (await client.put(f"/api/players/{uuid}/cape", json={"cape": "aqua-Premium"})).status_code == 204
    assert (await client.get(f"/api/players/{uuid}/cosmetics")).json()["cape"] == "aqua-Premium"


async def test_status_roundtrip(client, uuid):
    assert (await client.put(f"/api/players/{uuid}/status", json={"status": "afk"})).status_code == 204
    assert (await client.get(f"/api/players/{uuid}/status")).json() == {"status": "afk"}


async def test_rank_default(client, uuid):
    assert (await client.get(f"/api/players/{uuid}/rank")).json() == {"rank": "Default"}


async def test_presence_online_and_by_server(client, uuid):
    # No heartbeat yet: offline.
    assert (await client.get("/api/players/online")).json() == {"players": [], "count": 0}

    assert (await client.post(f"/api/players/{uuid}/online")).status_code == 204
    online = (await client.get("/api/players/online")).json()
    assert online["count"] == 1 and uuid in online["players"]

    assert (await client.put(f"/api/players/{uuid}/server", json={"server": "hypixel"})).status_code == 204
    on_server = (await client.get("/api/servers/hypixel/players")).json()
    assert uuid in on_server["players"]
    # Case-insensitive server match, as Store.playersOnServer used equalsIgnoreCase.
    assert uuid in (await client.get("/api/servers/HYPIXEL/players")).json()["players"]


async def test_batch_cosmetics(client):
    a = "aaaaaaaa-0000-0000-0000-000000000000"
    b = "bbbbbbbb-0000-0000-0000-000000000000"
    await client.post(f"/api/players/{a}/cosmetics", json={"cosmetic": "halo"})
    await client.post(f"/api/players/{b}/cosmetics", json={"cosmetic": "tophat"})

    r = await client.post("/api/players/cosmetics/batch", json={"uuids": [a, b]})
    assert r.status_code == 200
    players = r.json()["players"]
    assert players[a] == {"cosmetics": ["halo"], "cape": ""}
    assert players[b] == {"cosmetics": ["tophat"], "cape": ""}


async def test_undashed_uuid_normalizes(client):
    # Minecraft sends both forms; both must key the same player.
    undashed = "11111111222233334444555555555555"
    dashed = "11111111-2222-3333-4444-555555555555"
    await client.post(f"/api/players/{undashed}/cosmetics", json={"cosmetic": "halo"})
    assert (await client.get(f"/api/players/{dashed}/cosmetics")).json()["cosmetics"] == ["halo"]


async def test_etag_304(client, uuid):
    r1 = await client.get(f"/api/players/{uuid}/cosmetics")
    etag = r1.headers["ETag"]
    assert r1.headers["Cache-Control"].startswith("public, max-age=")

    r2 = await client.get(f"/api/players/{uuid}/cosmetics", headers={"If-None-Match": etag})
    assert r2.status_code == 304

    # A change to the active set must invalidate the ETag.
    await client.post(f"/api/players/{uuid}/cosmetics", json={"cosmetic": "halo"})
    r3 = await client.get(f"/api/players/{uuid}/cosmetics", headers={"If-None-Match": etag})
    assert r3.status_code == 200


@pytest.mark.parametrize("path", ["/api/players/x/nope", "/api/nope"])
async def test_unknown_route_404(client, path):
    assert (await client.get(path)).status_code == 404
