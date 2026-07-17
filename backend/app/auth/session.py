"""Mojang session-handshake proof of UUID ownership (architecture 4).

The flow reuses the handshake every Minecraft server already performs, so it needs no Azure
app and no second login:

  1. Client asks this service for a nonce (a server id string).
  2. Client calls Mojang's `joinServer` with the session token it already holds and that
     nonce, exactly as it would when joining a real server.
  3. Client posts {uuid, username, server_id} here.
  4. This service calls Mojang's `hasJoined`; a match proves the caller controls that
     account, and we issue our own JWT.

Nonces are single-use and short-lived. They are a replay guard for the seconds between issue
and proof, not durable state. There are two stores behind one Protocol: an in-process one for
dev and tests, and a Postgres-backed one for production. The Postgres store exists because the
service runs as several replicas: /auth/nonce and /auth/session can land on different replicas,
so an in-process nonce issued by one would be unknown to the other.
"""

from __future__ import annotations

import secrets
from datetime import timedelta
from typing import Protocol

import httpx
from sqlalchemy import delete
from sqlalchemy.ext.asyncio import AsyncSession, async_sessionmaker

from app.db.models import NonceRow
from app.domain.models import normalize_uuid, now


class ProofError(Exception):
    """Raised when a nonce is unknown/expired or Mojang does not confirm the session."""


class NonceStore(Protocol):
    """A store of single-use, short-lived nonces. Both methods are async so the Postgres
    implementation fits; the in-process one satisfies the same shape."""

    async def issue(self) -> str: ...

    async def consume(self, nonce: str) -> bool: ...


class MemoryNonceStore:
    """In-process nonce store for dev and tests. Correct for a single replica only; production
    uses PostgresNonceStore so the handshake works across replicas."""

    def __init__(self, ttl_seconds: int = 120) -> None:
        self._ttl = ttl_seconds
        self._issued: dict[str, object] = {}

    async def issue(self) -> str:
        self._sweep()
        nonce = secrets.token_hex(16)
        self._issued[nonce] = now() + timedelta(seconds=self._ttl)
        return nonce

    async def consume(self, nonce: str) -> bool:
        self._sweep()
        return self._issued.pop(nonce, None) is not None

    def _sweep(self) -> None:
        current = now()
        expired = [n for n, exp in self._issued.items() if exp <= current]
        for n in expired:
            del self._issued[n]


class PostgresNonceStore:
    """Shared nonce store backed by the auth_nonces table, so a nonce issued by one replica is
    consumable by any other. Single-use is enforced by the database: consume is a conditional
    DELETE, so of two replicas racing on the same nonce exactly one sees rowcount 1."""

    def __init__(self, sessionmaker: async_sessionmaker[AsyncSession], ttl_seconds: int = 120) -> None:
        self._sessionmaker = sessionmaker
        self._ttl = ttl_seconds

    async def issue(self) -> str:
        nonce = secrets.token_hex(16)
        async with self._sessionmaker() as session:
            # Sweep expired rows opportunistically so the table stays small; at this volume and
            # TTL a full scan is cheap and needs no scheduled job.
            await session.execute(delete(NonceRow).where(NonceRow.expires_at <= now()))
            session.add(NonceRow(server_id=nonce, expires_at=now() + timedelta(seconds=self._ttl)))
            await session.commit()
        return nonce

    async def consume(self, nonce: str) -> bool:
        async with self._sessionmaker() as session:
            # Delete-if-valid in one statement: the row-level lock makes this atomic, so a
            # replayed nonce (or two replicas racing) can succeed at most once.
            result = await session.execute(
                delete(NonceRow).where(NonceRow.server_id == nonce, NonceRow.expires_at > now())
            )
            await session.commit()
            return (result.rowcount or 0) > 0


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
