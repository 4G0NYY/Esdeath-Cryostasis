"""The storage seam.

Store.java proved the value of one class that owns storage; this Protocol is that seam in
Python. The API layer depends only on this, so swapping memory.py for postgres.py (or a
future Redis-fronted variant) never touches a router.

All methods are async because the Postgres implementation is; memory.py satisfies the same
signatures synchronously under the hood.
"""

from __future__ import annotations

from typing import Protocol, runtime_checkable

from app.domain.models import Player


@runtime_checkable
class Repo(Protocol):
    async def get_player(self, uuid: str) -> Player:
        """Return the player, creating an empty record if none exists. Never returns None,
        matching Store.player which used computeIfAbsent."""
        ...

    async def get_players(self, uuids: list[str]) -> dict[str, Player]:
        """Batch lookup for the batch cosmetics endpoint (architecture 8)."""
        ...

    async def set_status(self, uuid: str, status: str) -> None: ...

    async def set_server(self, uuid: str, server: str) -> None:
        """Also marks the player seen, as ImOnServer implied presence."""
        ...

    async def set_cape(self, uuid: str, cape: str) -> None: ...

    async def touch(self, uuid: str) -> None:
        """Heartbeat: record that the player was just seen (the addMe / online call)."""
        ...

    async def add_cosmetic(self, uuid: str, slug: str) -> bool:
        """Add to the active set. Returns whether it was newly added (Store.addCosmetic)."""
        ...

    async def remove_cosmetic(self, uuid: str, slug: str) -> bool:
        """Remove from the active set. Returns whether it was present."""
        ...

    async def online_players(self, window_seconds: int) -> list[str]:
        ...

    async def players_on_server(self, server: str, window_seconds: int) -> list[str]:
        ...

    async def capes(self) -> list[str]:
        ...

    async def close(self) -> None:
        ...
