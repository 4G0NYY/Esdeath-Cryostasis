"""player module presets

Revision ID: 0005_presets
Revises: 0004_presence_activity
Create Date: 2026-09-19

One new table and nothing else touched, so a replica on the previous image keeps serving against
a migrated database during a rolling update.
"""
from __future__ import annotations

from collections.abc import Sequence

import sqlalchemy as sa
from alembic import op

revision: str = "0005_presets"
down_revision: str | None = "0004_presence_activity"
branch_labels: str | Sequence[str] | None = None
depends_on: str | Sequence[str] | None = None


def upgrade() -> None:
    op.create_table(
        "player_presets",
        sa.Column(
            "player_uuid",
            sa.String(36),
            sa.ForeignKey("players.uuid", ondelete="CASCADE"),
            primary_key=True,
        ),
        sa.Column("name", sa.String(24), primary_key=True),
        sa.Column("modules", sa.JSON, nullable=False),
        sa.Column("updated_at", sa.DateTime(timezone=True), nullable=False),
    )


def downgrade() -> None:
    op.drop_table("player_presets")
