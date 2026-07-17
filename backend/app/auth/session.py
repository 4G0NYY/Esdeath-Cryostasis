"""Mojang session-handshake proof of UUID ownership (architecture 4).

The flow reuses the handshake every Minecraft server already performs, so it needs no Azure
app and no second login:

  1. Client asks this service for a nonce (a server id string).
  2. Client calls Mojang's `joinServer` with the session token it already holds and that
     nonce, exactly as it would when joining a real server.
  3. Client posts {uuid, username, server_id} here.
  4. This service calls Mojang's `hasJoined`; a match proves the caller controls that
     account, and we issue our own JWT.

Nonces are single-use and short-lived, held in process. That is enough because a nonce is
only a replay guard for the seconds between issue and proof; it is not durable state, so it
does not belong in the database and survives a restart as "client asks for a new one".
"""

from __future__ import annotations

import secrets
from datetime import timedelta

import httpx

from app.domain.models import normalize_uuid, now


class ProofError(Exception):
    """Raised when a nonce is unknown/expired or Mojang does not confirm the session."""


class NonceStore:
    def __init__(self, ttl_seconds: int = 120) -> None:
        self._ttl = ttl_seconds
        self._issued: dict[str, object] = {}

    def issue(self) -> str:
        self._sweep()
        nonce = secrets.token_hex(16)
        self._issued[nonce] = now() + timedelta(seconds=self._ttl)
        return nonce

    def consume(self, nonce: str) -> bool:
        self._sweep()
        return self._issued.pop(nonce, None) is not None

    def _sweep(self) -> None:
        current = now()
        expired = [n for n, exp in self._issued.items() if exp <= current]
        for n in expired:
            del self._issued[n]


async def verify_with_mojang(
    username: str,
    server_id: str,
    *,
    has_joined_url: str,
    timeout_seconds: float,
) -> str:
    """Confirm the session with Mojang and return the canonical UUID it authenticates.

    Raises ProofError if Mojang returns no session (204) or a different account.
    """
    async with httpx.AsyncClient(timeout=timeout_seconds) as client:
        response = await client.get(
            has_joined_url, params={"username": username, "serverId": server_id}
        )
    if response.status_code != 200 or not response.content:
        raise ProofError("mojang did not confirm the session")
    data = response.json()
    mojang_id = data.get("id")
    if not mojang_id:
        raise ProofError("mojang response missing id")
    return normalize_uuid(mojang_id)
