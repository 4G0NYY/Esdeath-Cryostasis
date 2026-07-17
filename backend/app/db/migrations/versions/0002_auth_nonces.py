"""auth nonces table

Revision ID: 0002_auth_nonces
Revises: 0001_initial
Create Date: 2026-07-17

Adds the shared store for single-use login nonces. It moved out of process so the session
handshake works when the service runs as several replicas: /auth/nonce and /auth/session can
be served by different replicas, and an in-process nonce would be unknown to the second.
"""
from __future__ import annotations

from collections.abc import Sequence

import sqlalchemy as sa
from alembic import op

revision: str = "0002_auth_nonces"
down_revision: str | None = "0001_initial"
branch_labels: str | Sequence[str] | None = None
depends_on: str | Sequence[str] | None = None


def upgrade() -> None:
    op.create_table(
        "auth_nonces",
        sa.Column("server_id", sa.String(64), primary_key=True),
        sa.Column("expires_at", sa.DateTime(timezone=True), nullable=False),
    )
    # The sweep on issue and the validity check on consume both filter by expiry, so back it.
    op.create_index("ix_auth_nonces_expires_at", "auth_nonces", ["expires_at"])


def downgrade() -> None:
    op.drop_index("ix_auth_nonces_expires_at", table_name="auth_nonces")
    op.drop_table("auth_nonces")
