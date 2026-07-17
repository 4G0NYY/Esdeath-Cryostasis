"""In-memory repository.

Not only a dev convenience: it lets the whole test suite run with no database, which is
how contract parity with docs/backend-api.md stays cheap to enforce (architecture 3).

State is a dict of players plus the seeded cape catalogue, guarded by an asyncio lock so
concurrent requests behave like the Java store's synchronized methods.
"""

from __future__ import annotations

import asyncio

from app.domain.models import Player, normalize_uuid, now

# Same seed the Java dev instance shipped, so switching backends does not change what the
# client sees for capes.
SEED_CAPES = ["classic-Default", "aqua-Premium", "ember-Epic", "mythic-Chef"]


class MemoryRepo:
    def __init__(self) -> None:
        self._players: dict[str, Player] = {}
        self._capes: list[str] = list(SEED_CAPES)
        self._lock = asyncio.Lock()

    def _ensure(self, uuid: str) -> Player:
        key = normalize_uuid(uuid)
        player = self._players.get(key)
        if player is None:
            player = Player(uuid=key)
            self._players[key] = player
        return player

    async def get_player(self, uuid: str) -> Player:
        async with self._lock:
            # Return a copy so callers cannot mutate the store's set by reference.
            return self._ensure(uuid).model_copy(deep=True)

    async def get_players(self, uuids: list[str]) -> dict[str, Player]:
        async with self._lock:
            return {normalize_uuid(u): self._ensure(u).model_copy(deep=True) for u in uuids}

    async def set_status(self, uuid: str, status: str) -> None:
        async with self._lock:
            self._ensure(uuid).status = status

    async def set_server(self, uuid: str, server: str) -> None:
        async with self._lock:
            player = self._ensure(uuid)
            player.server = server
            player.last_seen = now()

    async def set_cape(self, uuid: str, cape: str) -> None:
        async with self._lock:
            self._ensure(uuid).cape = cape

    async def touch(self, uuid: str) -> None:
        async with self._lock:
            self._ensure(uuid).last_seen = now()

    async def add_cosmetic(self, uuid: str, slug: str) -> bool:
        async with self._lock:
            cosmetics = self._ensure(uuid).cosmetics
            if slug in cosmetics:
                return False
            cosmetics.add(slug)
            return True

    async def remove_cosmetic(self, uuid: str, slug: str) -> bool:
        async with self._lock:
            cosmetics = self._ensure(uuid).cosmetics
            if slug not in cosmetics:
                return False
            cosmetics.discard(slug)
            return True

    async def online_players(self, window_seconds: int) -> list[str]:
        async with self._lock:
            return [u for u, p in self._players.items() if p.is_online(window_seconds)]

    async def players_on_server(self, server: str, window_seconds: int) -> list[str]:
        async with self._lock:
            return [
                u
                for u, p in self._players.items()
                if p.is_online(window_seconds) and p.server.lower() == server.lower()
            ]

    async def capes(self) -> list[str]:
        async with self._lock:
            return list(self._capes)

    async def close(self) -> None:
        return None
