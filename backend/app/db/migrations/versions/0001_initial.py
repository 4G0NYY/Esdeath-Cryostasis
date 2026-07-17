"""initial schema plus catalogue and cape seed

Revision ID: 0001_initial
Revises:
Create Date: 2026-07-17

The catalogue is seeded here rather than at boot because it is effectively read-only at
runtime (architecture 5) and this keeps a fresh database usable with no separate seed step.
The seed rows mirror app/domain/models.CATALOGUE and repo/memory.SEED_CAPES so the Postgres
and in-memory backends serve the same data.
"""
from __future__ import annotations

from collections.abc import Sequence

import sqlalchemy as sa
from alembic import op

revision: str = "0001_initial"
down_revision: str | None = None
branch_labels: str | Sequence[str] | None = None
depends_on: str | Sequence[str] | None = None


def upgrade() -> None:
    op.create_table(
        "players",
        sa.Column("uuid", sa.String(36), primary_key=True),
        sa.Column("rank", sa.String(32), nullable=False, server_default="Default"),
        sa.Column("status", sa.String(256), nullable=False, server_default=""),
        sa.Column("server", sa.String(128), nullable=False, server_default=""),
        sa.Column("cape", sa.String(128), nullable=False, server_default=""),
        sa.Column("last_seen", sa.DateTime(timezone=True), nullable=True),
        sa.Column("updated_at", sa.DateTime(timezone=True), server_default=sa.func.now()),
    )
    # Presence is "last_seen > now() - window", so this index backs the online lists.
    op.create_index("ix_players_last_seen", "players", ["last_seen"])

    op.create_table(
        "cosmetics",
        sa.Column("slug", sa.String(64), primary_key=True),
        sa.Column("kind", sa.String(32), nullable=False),
        sa.Column("rarity", sa.String(32), nullable=False, server_default="Default"),
        sa.Column("texture_key", sa.String(256), nullable=False, server_default=""),
        sa.Column("model", sa.String(64), nullable=False, server_default=""),
    )

    op.create_table(
        "player_cosmetics",
        sa.Column(
            "player_uuid",
            sa.String(36),
            sa.ForeignKey("players.uuid", ondelete="CASCADE"),
            primary_key=True,
        ),
        sa.Column("cosmetic_slug", sa.String(64), primary_key=True),
    )

    op.create_table(
        "capes",
        sa.Column("name", sa.String(128), primary_key=True),
    )

    _seed()


def _seed() -> None:
    catalogue = sa.table(
        "cosmetics",
        sa.column("slug", sa.String),
        sa.column("kind", sa.String),
        sa.column("rarity", sa.String),
        sa.column("texture_key", sa.String),
        sa.column("model", sa.String),
    )
    op.bulk_insert(
        catalogue,
        [
            {"slug": "halo", "kind": "aura", "rarity": "Epic", "texture_key": "", "model": "ring"},
            {"slug": "bandana", "kind": "head", "rarity": "Default", "texture_key": "", "model": "head"},
            {"slug": "tophat", "kind": "head", "rarity": "Premium", "texture_key": "", "model": "head"},
            {"slug": "wings", "kind": "back", "rarity": "Epic", "texture_key": "", "model": "wings"},
            {"slug": "tail", "kind": "back", "rarity": "Default", "texture_key": "", "model": "tail"},
            {"slug": "rabbitears", "kind": "head", "rarity": "Default", "texture_key": "", "model": "head"},
            {"slug": "reifen", "kind": "aura", "rarity": "Epic", "texture_key": "", "model": "ring"},
            {"slug": "susanoo", "kind": "aura", "rarity": "Chef", "texture_key": "", "model": "aura"},
            {"slug": "stripes", "kind": "body", "rarity": "Default", "texture_key": "", "model": "body"},
        ],
    )

    capes = sa.table("capes", sa.column("name", sa.String))
    op.bulk_insert(
        capes,
        [
            {"name": "classic-Default"},
            {"name": "aqua-Premium"},
            {"name": "ember-Epic"},
            {"name": "mythic-Chef"},
        ],
    )


def downgrade() -> None:
    op.drop_table("player_cosmetics")
    op.drop_table("capes")
    op.drop_table("cosmetics")
    op.drop_index("ix_players_last_seen", table_name="players")
    op.drop_table("players")
