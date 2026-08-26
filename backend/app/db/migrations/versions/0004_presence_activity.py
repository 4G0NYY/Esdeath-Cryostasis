"""player activity timestamp plus the chat sender's presence snapshot

Revision ID: 0004_presence_activity
Revises: 0003_ranks_and_chat
Create Date: 2026-08-26

Two additive columns, so a replica on the previous image keeps serving against a migrated
database during a rolling update (expand then contract). players.last_active is nullable rather
than defaulted: a row that has never reported activity has genuinely never been active, and
presence_state reads that absence as AFK rather than inventing a timestamp that would claim the
player was at the keyboard when the migration ran.
"""
from __future__ import annotations

from collections.abc import Sequence

import sqlalchemy as sa
from alembic import op

revision: str = "0004_presence_activity"
down_revision: str | None = "0003_ranks_and_chat"
branch_labels: str | Sequence[str] | None = None
depends_on: str | Sequence[str] | None = None


def upgrade() -> None:
    op.add_column(
        "players",
        sa.Column("last_active", sa.DateTime(timezone=True), nullable=True),
    )
    # Existing lines predate the snapshot and cannot be reconstructed, so they take the default
    # rather than a guess: a historical line saying "online" is the same claim it made before
    # this column existed.
    op.add_column(
        "chat_messages",
        sa.Column("state", sa.String(16), nullable=False, server_default="online"),
    )
    # The roster query filters on the presence window and orders by activity, so both presence
    # timestamps are worth an index once the table holds more than a handful of players.
    op.create_index("ix_players_last_seen", "players", ["last_seen"])


def downgrade() -> None:
    op.drop_index("ix_players_last_seen", table_name="players")
    op.drop_column("chat_messages", "state")
    op.drop_column("players", "last_active")
