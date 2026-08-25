"""Postgres repository.

Satisfies the same Repo Protocol as memory.py. Presence is derived from last_seen with a
window (architecture 6), so there is no online column to keep in sync and a crashed client
falls offline on its own.
"""

from __future__ import annotations

from datetime import datetime, timedelta

from sqlalchemy import delete, func, select
from sqlalchemy.exc import IntegrityError
from sqlalchemy.ext.asyncio import AsyncSession, async_sessionmaker

from app.db.models import CapeRow, ChatMessageRow, ChatMuteRow, PlayerCosmeticRow, PlayerRow
from app.domain.models import ChatMessage, Mute, Player, normalize_uuid, now


class PostgresRepo:
    def __init__(self, sessionmaker: async_sessionmaker[AsyncSession]) -> None:
        self._sessionmaker = sessionmaker

    async def _row(self, session: AsyncSession, uuid: str) -> PlayerRow:
        key = normalize_uuid(uuid)
        row = await session.get(PlayerRow, key)
        if row is None:
            row = PlayerRow(uuid=key)
            session.add(row)
            await session.flush()
        return row

    @staticmethod
    def _to_domain(row: PlayerRow, cosmetics: set[str]) -> Player:
        return Player(
            uuid=row.uuid,
            username=row.username,
            rank=row.rank,
            status=row.status,
            server=row.server,
            cape=row.cape,
            cosmetics=cosmetics,
            last_seen=row.last_seen,
        )

    async def _slugs_for(self, session: AsyncSession, keys: list[str]) -> dict[str, set[str]]:
        # Fetch the active sets with an explicit query rather than the ORM relationship, so no
        # lazy load fires outside the async greenlet (SQLAlchemy raises MissingGreenlet if it
        # does). One query covers a batch.
        result: dict[str, set[str]] = {k: set() for k in keys}
        if not keys:
            return result
        rows = await session.execute(
            select(PlayerCosmeticRow.player_uuid, PlayerCosmeticRow.cosmetic_slug).where(
                PlayerCosmeticRow.player_uuid.in_(keys)
            )
        )
        for player_uuid, slug in rows.all():
            result[player_uuid].add(slug)
        return result

    async def get_player(self, uuid: str) -> Player:
        key = normalize_uuid(uuid)
        async with self._sessionmaker() as session:
            row = await self._row(session, uuid)
            slugs = (await self._slugs_for(session, [key]))[key]
            player = self._to_domain(row, slugs)
            await session.commit()
            return player

    async def get_players(self, uuids: list[str]) -> dict[str, Player]:
        keys = [normalize_uuid(u) for u in uuids]
        async with self._sessionmaker() as session:
            found = (
                await session.execute(select(PlayerRow).where(PlayerRow.uuid.in_(keys)))
            ).scalars().all()
            slugs = await self._slugs_for(session, keys)
            by_uuid = {row.uuid: self._to_domain(row, slugs.get(row.uuid, set())) for row in found}
            # Missing players report as empty, matching computeIfAbsent semantics without
            # writing a row for a mere read.
            return {key: by_uuid.get(key, Player(uuid=key)) for key in keys}

    async def set_status(self, uuid: str, status: str) -> None:
        async with self._sessionmaker() as session:
            (await self._row(session, uuid)).status = status
            await session.commit()

    async def set_rank(self, uuid: str, rank: str) -> None:
        async with self._sessionmaker() as session:
            (await self._row(session, uuid)).rank = rank
            await session.commit()

    async def set_username(self, uuid: str, username: str) -> None:
        async with self._sessionmaker() as session:
            (await self._row(session, uuid)).username = username
            await session.commit()

    async def find_by_username(self, username: str) -> Player | None:
        async with self._sessionmaker() as session:
            row = (
                await session.execute(
                    select(PlayerRow).where(func.lower(PlayerRow.username) == username.strip().lower())
                )
            ).scalars().first()
            if row is None:
                return None
            slugs = (await self._slugs_for(session, [row.uuid]))[row.uuid]
            return self._to_domain(row, slugs)

    async def set_server(self, uuid: str, server: str) -> None:
        async with self._sessionmaker() as session:
            row = await self._row(session, uuid)
            row.server = server
            row.last_seen = now()
            await session.commit()

    async def set_cape(self, uuid: str, cape: str) -> None:
        async with self._sessionmaker() as session:
            (await self._row(session, uuid)).cape = cape
            await session.commit()

    async def touch(self, uuid: str) -> None:
        async with self._sessionmaker() as session:
            (await self._row(session, uuid)).last_seen = now()
            await session.commit()

    async def add_cosmetic(self, uuid: str, slug: str) -> bool:
        key = normalize_uuid(uuid)
        async with self._sessionmaker() as session:
            await self._row(session, uuid)  # ensure the FK target exists
            # The primary key is (player_uuid, cosmetic_slug), so a duplicate raises. Catch
            # it and report "not newly added" rather than special-casing a dialect's upsert,
            # which keeps this identical on Postgres and the sqlite used by the repo tests.
            session.add(PlayerCosmeticRow(player_uuid=key, cosmetic_slug=slug))
            try:
                await session.commit()
            except IntegrityError:
                await session.rollback()
                return False
            return True

    async def remove_cosmetic(self, uuid: str, slug: str) -> bool:
        async with self._sessionmaker() as session:
            key = normalize_uuid(uuid)
            result = await session.execute(
                delete(PlayerCosmeticRow).where(
                    PlayerCosmeticRow.player_uuid == key,
                    PlayerCosmeticRow.cosmetic_slug == slug,
                )
            )
            await session.commit()
            return (result.rowcount or 0) > 0

    async def online_players(self, window_seconds: int) -> list[str]:
        cutoff = now() - timedelta(seconds=window_seconds)
        async with self._sessionmaker() as session:
            rows = await session.execute(
                select(PlayerRow.uuid).where(PlayerRow.last_seen > cutoff)
            )
            return list(rows.scalars().all())

    async def players_on_server(self, server: str, window_seconds: int) -> list[str]:
        cutoff = now() - timedelta(seconds=window_seconds)
        async with self._sessionmaker() as session:
            rows = await session.execute(
                select(PlayerRow.uuid).where(
                    PlayerRow.last_seen > cutoff,
                    func.lower(PlayerRow.server) == server.lower(),
                )
            )
            return list(rows.scalars().all())

    async def capes(self) -> list[str]:
        async with self._sessionmaker() as session:
            rows = await session.execute(select(CapeRow.name))
            return list(rows.scalars().all())

    @staticmethod
    def _chat_to_domain(row: ChatMessageRow) -> ChatMessage:
        return ChatMessage(
            id=row.id,
            uuid=row.player_uuid,
            username=row.username,
            rank=row.rank,
            color=row.color,
            message=row.message,
            at=row.created_at,
        )

    async def post_chat(self, message: ChatMessage) -> ChatMessage:
        async with self._sessionmaker() as session:
            row = ChatMessageRow(
                player_uuid=normalize_uuid(message.uuid),
                username=message.username,
                rank=message.rank,
                color=message.color,
                message=message.message,
                created_at=message.at,
            )
            session.add(row)
            await session.commit()
            # The id is assigned by the database, and it is the cursor clients poll on, so it
            # has to be read back rather than guessed.
            await session.refresh(row)
            return self._chat_to_domain(row)

    async def chat_since(self, after_id: int | None, limit: int) -> list[ChatMessage]:
        async with self._sessionmaker() as session:
            if after_id is None:
                # No cursor: the newest `limit` rows, then reversed, so a starting client gets
                # its backlog in reading order without scanning the whole table.
                rows = await session.execute(
                    select(ChatMessageRow).order_by(ChatMessageRow.id.desc()).limit(limit)
                )
                return [self._chat_to_domain(r) for r in reversed(rows.scalars().all())]
            rows = await session.execute(
                select(ChatMessageRow)
                .where(ChatMessageRow.id > after_id)
                .order_by(ChatMessageRow.id)
                .limit(limit)
            )
            return [self._chat_to_domain(r) for r in rows.scalars().all()]

    async def latest_chat_id(self) -> int:
        async with self._sessionmaker() as session:
            newest = (await session.execute(select(func.max(ChatMessageRow.id)))).scalar()
            return newest or 0

    async def delete_chat(self, message_id: int) -> bool:
        async with self._sessionmaker() as session:
            result = await session.execute(
                delete(ChatMessageRow).where(ChatMessageRow.id == message_id)
            )
            await session.commit()
            return (result.rowcount or 0) > 0

    async def prune_chat(self, before: datetime) -> None:
        async with self._sessionmaker() as session:
            await session.execute(
                delete(ChatMessageRow).where(ChatMessageRow.created_at < before)
            )
            await session.commit()

    async def set_mute(self, mute: Mute) -> None:
        key = normalize_uuid(mute.uuid)
        async with self._sessionmaker() as session:
            row = await session.get(ChatMuteRow, key)
            if row is None:
                row = ChatMuteRow(player_uuid=key)
                session.add(row)
            row.username = mute.username
            row.until = mute.until
            row.reason = mute.reason
            row.by_uuid = mute.by
            await session.commit()

    async def clear_mute(self, uuid: str) -> bool:
        async with self._sessionmaker() as session:
            result = await session.execute(
                delete(ChatMuteRow).where(ChatMuteRow.player_uuid == normalize_uuid(uuid))
            )
            await session.commit()
            return (result.rowcount or 0) > 0

    @staticmethod
    def _mute_to_domain(row: ChatMuteRow) -> Mute:
        return Mute(
            uuid=row.player_uuid,
            username=row.username,
            until=row.until,
            reason=row.reason,
            by=row.by_uuid,
        )

    async def get_mute(self, uuid: str) -> Mute | None:
        async with self._sessionmaker() as session:
            row = await session.get(ChatMuteRow, normalize_uuid(uuid))
            return self._mute_to_domain(row) if row is not None else None

    async def active_mutes(self) -> list[Mute]:
        async with self._sessionmaker() as session:
            rows = await session.execute(select(ChatMuteRow).where(ChatMuteRow.until > now()))
            return [self._mute_to_domain(r) for r in rows.scalars().all()]

    async def close(self) -> None:
        return None
