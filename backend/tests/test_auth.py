"""Auth tests: the session-proof handshake and the ownership check on mutating routes.

Mojang's hasJoined is patched, since the suite must not reach the network. What is tested
is this service's own logic: nonce single-use, uuid-match enforcement, JWT issue, and that
a token only authorizes its own subject.
"""

from __future__ import annotations

import pytest

from app.auth import session as session_proof
from app.config import Settings

from .conftest import make_client

OWNER = "11111111-2222-3333-4444-555555555555"
OTHER = "99999999-8888-7777-6666-555555555555"


def auth_settings() -> Settings:
    return Settings(
        database_url="",
        require_auth=True,
        jwt_secret="test-secret-not-default-0123456789abcdef",
    )


@pytest.fixture
def no_mojang(monkeypatch):
    async def fake(username, server_id, *, has_joined_url, timeout_seconds):
        # Pretend Mojang authenticated whoever the test set as the "real" account.
        return session_proof.normalize_uuid(monkeypatch._real_uuid)

    monkeypatch._real_uuid = OWNER
    monkeypatch.setattr(session_proof, "verify_with_mojang", fake)
    return monkeypatch


async def obtain_token(client, uuid: str) -> str:
    nonce = (await client.post("/api/auth/nonce")).json()["server_id"]
    r = await client.post(
        "/api/auth/session", json={"uuid": uuid, "username": "Notch", "server_id": nonce}
    )
    assert r.status_code == 200, r.text
    return r.json()["token"]


async def test_mutation_requires_token(no_mojang):
    async with make_client(auth_settings()) as client:
        r = await client.post(f"/api/players/{OWNER}/cosmetics", json={"cosmetic": "halo"})
        assert r.status_code == 401


async def test_owner_can_mutate_own_uuid(no_mojang):
    async with make_client(auth_settings()) as client:
        token = await obtain_token(client, OWNER)
        r = await client.post(
            f"/api/players/{OWNER}/cosmetics",
            json={"cosmetic": "halo"},
            headers={"Authorization": f"Bearer {token}"},
        )
        assert r.status_code == 200 and r.json() == {"ok": True}


async def test_token_cannot_mutate_other_uuid(no_mojang):
    async with make_client(auth_settings()) as client:
        token = await obtain_token(client, OWNER)
        r = await client.post(
            f"/api/players/{OTHER}/cosmetics",
            json={"cosmetic": "halo"},
            headers={"Authorization": f"Bearer {token}"},
        )
        assert r.status_code == 403


async def test_nonce_is_single_use(no_mojang):
    async with make_client(auth_settings()) as client:
        nonce = (await client.post("/api/auth/nonce")).json()["server_id"]
        body = {"uuid": OWNER, "username": "Notch", "server_id": nonce}
        assert (await client.post("/api/auth/session", json=body)).status_code == 200
        # Replaying the same nonce must fail.
        assert (await client.post("/api/auth/session", json=body)).status_code == 400


async def test_session_uuid_must_match_mojang(no_mojang):
    no_mojang._real_uuid = OTHER  # Mojang authenticates OTHER
    async with make_client(auth_settings()) as client:
        nonce = (await client.post("/api/auth/nonce")).json()["server_id"]
        # but the caller claims to be OWNER
        r = await client.post(
            "/api/auth/session",
            json={"uuid": OWNER, "username": "Notch", "server_id": nonce},
        )
        assert r.status_code == 401


async def test_reads_are_open_even_with_auth_on(no_mojang):
    async with make_client(auth_settings()) as client:
        # A renderer must look up any player without a token.
        assert (await client.get(f"/api/players/{OTHER}/cosmetics")).status_code == 200


def test_secret_required_when_auth_enforced():
    from app.main import create_app

    with pytest.raises(RuntimeError):
        create_app(Settings(require_auth=True, jwt_secret="dev-insecure-secret"))
