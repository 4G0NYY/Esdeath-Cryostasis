"""The storage seam.

Store.java proved the value of one class that owns storage; this Protocol is that seam in
Python. The API layer depends only on this, so swapping memory.py for postgres.py (or a
future Redis-fronted variant) never touches a router.

All methods are async because the Postgres implementation is; memory.py satisfies the same
signatures synchronously under the hood.
"""

from __future__ import annotations

from typing import Protocol, runtime_checkable

from datetime import datetime

from app.domain.models import ChatMessage, Mute, Player


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

    async def set_rank(self, uuid: str, rank: str) -> None:
        """Admin-only rank assignment. The rank string is already validated against the
        registry by RankBody, so this only stores it."""
        ...

    async def set_username(self, uuid: str, username: str) -> None:
        """Record the name Mojang authenticated during the session proof, so global chat
        renders a name this service verified rather than one a client sent."""
        ...

    async def find_by_username(self, username: str) -> Player | None:
        """Look a player up by their recorded name, for moderating by the name a moderator
        actually sees in chat. None when no one by that name has ever signed in."""
        ...

    async def set_server(self, uuid: str, server: str) -> None:
        """Also marks the player seen, as ImOnServer implied presence."""
        ...

    async def set_cape(self, uuid: str, cape: str) -> None: ...

    async def touch(self, uuid: str, active: bool = False) -> None:
        """Heartbeat: record that the player was just seen (the addMe / online call).

        `active` additionally records that they did something, which is what separates an idle
        client from a live one. It defaults to false so a caller written against the recovered
        addMe contract, which carried no such flag, cannot silently claim activity.
        """
        ...

    async def presence(self, window_seconds: int) -> list[Player]:
        """Every player whose heartbeat is inside the window, whole records rather than UUIDs.

        Distinct from online_players, which answers the recovered getOnlinePlayingPlayers with a
        list of UUIDs and must keep doing so. The roster needs names, ranks and both presence
        timestamps, and fetching those per UUID would be one query per player on the one call
        that always touches every online row.
        """
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

    # Global chat. The id is the poll cursor, so post_chat must return the stored message with
    # its assigned id rather than just writing it.
    async def post_chat(self, message: ChatMessage) -> ChatMessage:
        """Append a line and return it with its assigned id. The id on the argument is ignored."""
        ...

    async def chat_since(self, after_id: int | None, limit: int) -> list[ChatMessage]:
        """Messages after the given id, oldest first. With no id, the newest `limit` messages,
        still oldest first, which is the backlog a client shows when it starts reading."""
        ...

    async def latest_chat_id(self) -> int:
        """Highest id currently stored, or 0 when empty. The long poll compares against this
        to decide whether to sleep, without pulling rows it would then discard."""
        ...

    async def delete_chat(self, message_id: int) -> bool:
        ...

    async def prune_chat(self, before: datetime) -> None:
        """Drop messages older than `before`. Global chat is a live channel, not an archive."""
        ...

    async def set_mute(self, mute: Mute) -> None:
        ...

    async def clear_mute(self, uuid: str) -> bool:
        ...

    async def get_mute(self, uuid: str) -> Mute | None:
        """The player's mute if one is stored, expired or not. Callers check is_active, so an
        expired row reads the same as none without needing a sweeper to be correct."""
        ...

    async def active_mutes(self) -> list[Mute]:
        ...

    async def close(self) -> None:
        ...
