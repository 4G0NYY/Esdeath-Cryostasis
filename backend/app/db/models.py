"""SQLAlchemy 2.0 async models.

The tables from architecture 5, plus the two global chat added (chat_messages, chat_mutes).
Cape stays a column on players (not a row in player_cosmetics) because the contract splits them
and capes are a texture layer while the rest are ModelPart meshes.
"""

from __future__ import annotations

from datetime import datetime

from sqlalchemy import BigInteger, DateTime, ForeignKey, Integer, String, Text, func
from sqlalchemy.orm import DeclarativeBase, Mapped, mapped_column, relationship


class Base(DeclarativeBase):
    pass


class PlayerRow(Base):
    __tablename__ = "players"

    uuid: Mapped[str] = mapped_column(String(36), primary_key=True)
    # Written during the session proof, so it is a Mojang-authenticated name rather than one a
    # client sent. Global chat renders it; nothing else reads it.
    username: Mapped[str] = mapped_column(String(32), default="", server_default="")
    rank: Mapped[str] = mapped_column(String(32), default="Default", server_default="Default")
    status: Mapped[str] = mapped_column(String(256), default="", server_default="")
    server: Mapped[str] = mapped_column(String(128), default="", server_default="")
    cape: Mapped[str] = mapped_column(String(128), default="", server_default="")
    last_seen: Mapped[datetime | None] = mapped_column(
        DateTime(timezone=True), nullable=True, index=True
    )
    # Written only when a heartbeat reports the player did something, so the gap between this
    # and last_seen is what separates an idle client from a live one.
    last_active: Mapped[datetime | None] = mapped_column(DateTime(timezone=True), nullable=True)
    updated_at: Mapped[datetime] = mapped_column(
        DateTime(timezone=True), server_default=func.now(), onupdate=func.now()
    )

    cosmetics: Mapped[list["PlayerCosmeticRow"]] = relationship(
        back_populates="player", cascade="all, delete-orphan", lazy="selectin"
    )


class CosmeticRow(Base):
    """The catalogue. Seeded from a file, effectively read-only at runtime."""

    __tablename__ = "cosmetics"

    slug: Mapped[str] = mapped_column(String(64), primary_key=True)
    kind: Mapped[str] = mapped_column(String(32))
    rarity: Mapped[str] = mapped_column(String(32), default="Default", server_default="Default")
    texture_key: Mapped[str] = mapped_column(String(256), default="", server_default="")
    model: Mapped[str] = mapped_column(String(64), default="", server_default="")


class PlayerCosmeticRow(Base):
    """The active set, not an ownership record (architecture 5)."""

    __tablename__ = "player_cosmetics"

    player_uuid: Mapped[str] = mapped_column(
        String(36), ForeignKey("players.uuid", ondelete="CASCADE"), primary_key=True
    )
    cosmetic_slug: Mapped[str] = mapped_column(String(64), primary_key=True)

    player: Mapped[PlayerRow] = relationship(back_populates="cosmetics")


class CapeRow(Base):
    """The cape catalogue served by GET /capes (name-rarity strings)."""

    __tablename__ = "capes"

    name: Mapped[str] = mapped_column(String(128), primary_key=True)


class ChatMessageRow(Base):
    """One global chat line.

    The id is the poll cursor: clients ask for everything after the highest id they hold, which
    is what makes the channel work across replicas with no shared broadcast bus. It has to be
    monotonic for that, which is why moderation deletes a row outright instead of tombstoning it
    - a tombstone would either reuse a cursor position or need a second column to skip.

    The sender's rank and colour are snapshotted here rather than joined from players at read
    time: a read is then one indexed range scan, and a later promotion does not rewrite history.
    """

    __tablename__ = "chat_messages"

    # BIGSERIAL on Postgres. The sqlite variant is not cosmetic: sqlite only auto-assigns a
    # primary key when the column is declared INTEGER, which is the alias for its rowid, so a
    # BIGINT key would insert a NULL and fail. The repo tests run on sqlite (test_repo_postgres).
    id: Mapped[int] = mapped_column(
        BigInteger().with_variant(Integer, "sqlite"), primary_key=True, autoincrement=True
    )
    player_uuid: Mapped[str] = mapped_column(String(36), index=True)
    username: Mapped[str] = mapped_column(String(32), default="", server_default="")
    rank: Mapped[str] = mapped_column(String(32), default="Default", server_default="Default")
    color: Mapped[str] = mapped_column(String(16), default="", server_default="")
    message: Mapped[str] = mapped_column(Text)
    state: Mapped[str] = mapped_column(String(16), default="online", server_default="online")
    created_at: Mapped[datetime] = mapped_column(
        DateTime(timezone=True), server_default=func.now(), index=True
    )


class ChatMuteRow(Base):
    """An active chat mute, keyed by the muted player.

    An expiry rather than a flag, for the same reason presence is a timestamp: it lapses on its
    own and needs no sweeper to be correct. Rows outlive their expiry harmlessly, since every
    read filters on it.
    """

    __tablename__ = "chat_mutes"

    player_uuid: Mapped[str] = mapped_column(String(36), primary_key=True)
    username: Mapped[str] = mapped_column(String(32), default="", server_default="")
    until: Mapped[datetime] = mapped_column(DateTime(timezone=True), nullable=False)
    reason: Mapped[str] = mapped_column(String(256), default="", server_default="")
    by_uuid: Mapped[str] = mapped_column(String(36), default="", server_default="")


class NonceRow(Base):
    """Single-use, short-lived auth nonces (architecture 4).

    In the database rather than in process so the login handshake survives horizontal scaling:
    a nonce issued by one replica must be consumable by another, since the /auth/nonce and
    /auth/session calls can land on different replicas behind a load balancer. Rows are swept on
    issue and validated against expiry on consume, so a restart or a lost sweep just means the
    client asks for a fresh nonce.
    """

    __tablename__ = "auth_nonces"

    server_id: Mapped[str] = mapped_column(String(64), primary_key=True)
    expires_at: Mapped[datetime] = mapped_column(DateTime(timezone=True), nullable=False)
