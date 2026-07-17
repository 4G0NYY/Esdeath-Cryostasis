"""SQLAlchemy 2.0 async models.

The three tables from architecture 5. Cape stays a column on players (not a row in
player_cosmetics) because the contract splits them and capes are a texture layer while the
rest are ModelPart meshes.
"""

from __future__ import annotations

from datetime import datetime

from sqlalchemy import DateTime, ForeignKey, String, func
from sqlalchemy.orm import DeclarativeBase, Mapped, mapped_column, relationship


class Base(DeclarativeBase):
    pass


class PlayerRow(Base):
    __tablename__ = "players"

    uuid: Mapped[str] = mapped_column(String(36), primary_key=True)
    rank: Mapped[str] = mapped_column(String(32), default="Default", server_default="Default")
    status: Mapped[str] = mapped_column(String(256), default="", server_default="")
    server: Mapped[str] = mapped_column(String(128), default="", server_default="")
    cape: Mapped[str] = mapped_column(String(128), default="", server_default="")
    last_seen: Mapped[datetime | None] = mapped_column(DateTime(timezone=True), nullable=True)
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
