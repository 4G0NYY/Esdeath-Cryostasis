"""Alembic environment.

Runs migrations against CRYOSTASIS_DATABASE_URL, normalized through the same helper the app
uses so a plain postgres:// URL works. Uses a sync driver for migrations (psycopg-style)
because Alembic's default runner is synchronous; the app itself stays async.
"""

from __future__ import annotations

import os

from alembic import context
from sqlalchemy import create_engine, engine_from_config, pool, text

from app.db.dsn import parse_multihost, sync_engine_args
from app.db.models import Base

config = context.config
target_metadata = Base.metadata

# Arbitrary but fixed key for the migration advisory lock. Every replica must use the same
# value for the lock to actually serialize them; the number itself is meaningless.
_MIGRATION_LOCK_KEY = 5_141_982


def _database_url() -> str:
    url = os.environ.get("CRYOSTASIS_DATABASE_URL", "")
    if not url:
        raise RuntimeError("CRYOSTASIS_DATABASE_URL is required to run migrations")
    # Migrations run on a sync driver; strip any async driver the app would use.
    if url.startswith("postgres://"):
        url = "postgresql://" + url[len("postgres://") :]
    url = url.replace("+asyncpg", "").replace("+aiosqlite", "")
    return url


def run_migrations_offline() -> None:
    context.configure(
        url=_database_url(),
        target_metadata=target_metadata,
        literal_binds=True,
        dialect_opts={"paramstyle": "named"},
    )
    with context.begin_transaction():
        context.run_migrations()


def _make_connectable():
    # Multi-host HA DSN: SQLAlchemy cannot parse the comma authority, so build the sync engine
    # from a hostless URL plus psycopg2 connect_args carrying the comma-joined hosts and
    # target_session_attrs (app/db/dsn.py). Migrations then fail over to the primary exactly like
    # the async app does. A single-host URL takes the ordinary engine_from_config path.
    multihost = parse_multihost(os.environ.get("CRYOSTASIS_DATABASE_URL", ""))
    if multihost is not None:
        url, connect_args = sync_engine_args(multihost)
        return create_engine(url, poolclass=pool.NullPool, connect_args=connect_args)

    section = config.get_section(config.config_ini_section) or {}
    section["sqlalchemy.url"] = _database_url()
    return engine_from_config(section, prefix="sqlalchemy.", poolclass=pool.NullPool)


def run_migrations_online() -> None:
    connectable = _make_connectable()
    with connectable.connect() as connection:
        context.configure(connection=connection, target_metadata=target_metadata)
        with context.begin_transaction():
            # Every Swarm replica runs `alembic upgrade head` on start, so on a fresh database
            # they would all try to create the schema at once and all but one would crash. A
            # transaction-level advisory lock makes the others wait, then find the schema already
            # at head and no-op. It is transaction-scoped (released on commit, not held for the
            # session), so it stays correct through a transaction-pooling pgbouncer, which a
            # session-level lock would not. Postgres only; other dialects have no such function.
            if connection.dialect.name == "postgresql":
                connection.execute(
                    text("SELECT pg_advisory_xact_lock(:key)"), {"key": _MIGRATION_LOCK_KEY}
                )
            context.run_migrations()


if context.is_offline_mode():
    run_migrations_offline()
else:
    run_migrations_online()
