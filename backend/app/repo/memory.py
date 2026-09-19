"""In-memory repository.

Not only a dev convenience: it lets the whole test suite run with no database, which is
how contract parity with docs/backend-api.md stays cheap to enforce (architecture 3).

State is a dict of players, the seeded cape catalogue, and the chat log with its mutes, all
guarded by an asyncio lock so concurrent requests behave like the Java store's synchronized
methods.
"""

from __future__ import annotations

import asyncio
import itertools
from datetime import datetime

from app.domain.models import ChatMessage, Mute, Player, normalize_uuid, now
from app.domain.presets import Preset

# Same seed the Java dev instance shipped, so switching backends does not change what the
# client sees for capes.
SEED_CAPES = ["classic-Default", "aqua-Premium", "ember-Epic", "mythic-Chef"]


class MemoryRepo:
    def __init__(self) -> None:
        self._players: dict[str, Player] = {}
        self._capes: list[str] = list(SEED_CAPES)
        # A list plus a counter stands in for the chat_messages table's autoincrement id, which
        # is the poll cursor the API hands clients.
        self._chat: list[ChatMessage] = []
        self._chat_ids = itertools.count(1)
        self._mutes: dict[str, Mute] = {}
        self._presets: dict[str, dict[str, Preset]] = {}
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

    async def set_rank(self, uuid: str, rank: str) -> None:
        async with self._lock:
            self._ensure(uuid).rank = rank

    async def set_username(self, uuid: str, username: str) -> None:
        async with self._lock:
            self._ensure(uuid).username = username

    async def find_by_username(self, username: str) -> Player | None:
        key = username.strip().lower()
        async with self._lock:
            for player in self._players.values():
                if player.username.lower() == key:
                    return player.model_copy(deep=True)
            return None

    async def set_server(self, uuid: str, server: str) -> None:
        async with self._lock:
            player = self._ensure(uuid)
            player.server = server
            # Joining a server is both presence and activity: somebody clicked something.
            player.last_seen = now()
            player.last_active = now()

    async def set_cape(self, uuid: str, cape: str) -> None:
        async with self._lock:
            self._ensure(uuid).cape = cape

    async def touch(self, uuid: str, active: bool = False) -> None:
        async with self._lock:
            player = self._ensure(uuid)
            player.last_seen = now()
            if active:
                player.last_active = now()

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

    async def presence(self, window_seconds: int) -> list[Player]:
        async with self._lock:
            return [
                p.model_copy(deep=True)
                for p in self._players.values()
                if p.is_online(window_seconds)
            ]

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

    async def post_chat(self, message: ChatMessage) -> ChatMessage:
        async with self._lock:
            stored = message.model_copy(update={"id": next(self._chat_ids)})
            self._chat.append(stored)
            return stored

    async def chat_since(self, after_id: int | None, limit: int) -> list[ChatMessage]:
        async with self._lock:
            if after_id is None:
                return list(self._chat[-limit:])
            return [m for m in self._chat if m.id > after_id][:limit]

    async def latest_chat_id(self) -> int:
        async with self._lock:
            return self._chat[-1].id if self._chat else 0

    async def delete_chat(self, message_id: int) -> bool:
        async with self._lock:
            before = len(self._chat)
            self._chat = [m for m in self._chat if m.id != message_id]
            return len(self._chat) != before

    async def prune_chat(self, before: datetime) -> None:
        async with self._lock:
            self._chat = [m for m in self._chat if m.at >= before]

    async def set_mute(self, mute: Mute) -> None:
        async with self._lock:
            self._mutes[normalize_uuid(mute.uuid)] = mute

    async def clear_mute(self, uuid: str) -> bool:
        async with self._lock:
            return self._mutes.pop(normalize_uuid(uuid), None) is not None

    async def get_mute(self, uuid: str) -> Mute | None:
        async with self._lock:
            return self._mutes.get(normalize_uuid(uuid))

    async def active_mutes(self) -> list[Mute]:
        async with self._lock:
            return [m for m in self._mutes.values() if m.is_active()]

    async def list_presets(self, uuid: str) -> list[Preset]:
        async with self._lock:
            presets = self._presets.get(normalize_uuid(uuid), {}).values()
            return [p.model_copy(deep=True) for p in sorted(presets, key=lambda p: p.name.lower())]

    async def put_preset(self, uuid: str, preset: Preset) -> None:
        async with self._lock:
            self._presets.setdefault(normalize_uuid(uuid), {})[preset.name] = preset.model_copy(deep=True)

    async def delete_preset(self, uuid: str, name: str) -> bool:
        async with self._lock:
            return self._presets.get(normalize_uuid(uuid), {}).pop(name, None) is not None

    async def close(self) -> None:
        return None
