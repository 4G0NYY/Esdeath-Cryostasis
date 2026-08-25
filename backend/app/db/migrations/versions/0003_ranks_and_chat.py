"""player username plus the global chat tables

Revision ID: 0003_ranks_and_chat
Revises: 0002_auth_nonces
Create Date: 2026-08-25

Purely additive: a nullable-with-default column and two new tables, so a replica running the
previous image keeps working against a migrated database during a rolling update (expand then
contract). Ranks themselves needed no schema change - players.rank has existed since 0001 and
only ever lacked a write path.
"""
from __future__ import annotations

from collections.abc import Sequence

import sqlalchemy as sa
from alembic import op

revision: str = "0003_ranks_and_chat"
down_revision: str | None = "0002_auth_nonces"
branch_labels: str | Sequence[str] | None = None
depends_on: str | Sequence[str] | None = None


def upgrade() -> None:
    op.add_column(
        "players",
        sa.Column("username", sa.String(32), nullable=False, server_default=""),
    )

    op.create_table(
        "chat_messages",
        # Matches the model's variant: sqlite only auto-assigns a primary key declared INTEGER
        # (its rowid alias), so a plain BIGINT would insert NULL there. Postgres still gets
        # BIGSERIAL, which is what production runs.
        sa.Column(
            "id",
            sa.BigInteger().with_variant(sa.Integer, "sqlite"),
            primary_key=True,
            autoincrement=True,
        ),
        sa.Column("player_uuid", sa.String(36), nullable=False),
        sa.Column("username", sa.String(32), nullable=False, server_default=""),
        sa.Column("rank", sa.String(32), nullable=False, server_default="Default"),
        sa.Column("color", sa.String(16), nullable=False, server_default=""),
        sa.Column("message", sa.Text, nullable=False),
        sa.Column(
            "created_at", sa.DateTime(timezone=True), nullable=False, server_default=sa.func.now()
        ),
    )
    # Reads are "everything after this id" and the retention sweep is "everything before this
    # timestamp", so both filters get an index.
    op.create_index("ix_chat_messages_player_uuid", "chat_messages", ["player_uuid"])
    op.create_index("ix_chat_messages_created_at", "chat_messages", ["created_at"])

    op.create_table(
        "chat_mutes",
        sa.Column("player_uuid", sa.String(36), primary_key=True),
        sa.Column("username", sa.String(32), nullable=False, server_default=""),
        sa.Column("until", sa.DateTime(timezone=True), nullable=False),
        sa.Column("reason", sa.String(256), nullable=False, server_default=""),
        sa.Column("by_uuid", sa.String(36), nullable=False, server_default=""),
    )


def downgrade() -> None:
    op.drop_table("chat_mutes")
    op.drop_index("ix_chat_messages_created_at", table_name="chat_messages")
    op.drop_index("ix_chat_messages_player_uuid", table_name="chat_messages")
    op.drop_table("chat_messages")
    op.drop_column("players", "username")
