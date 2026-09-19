"""The Alembic chain applies to an empty database.

This exists because nothing else ran the migrations. The repo tests build their schema with
`Base.metadata.create_all`, which reads the models and never opens a migration file, so a
migration could be wrong in any way at all and the whole suite would still pass. Exactly that
happened: 0004 created an index 0001 had already created, every test passed, and the first thing
to find out was a replica crash-looping in production.

Sqlite is not the production database, and this deliberately does not pretend otherwise. What it
checks is the property that broke, and that is dialect independent: applying the revisions in
order, to nothing, must succeed and must land on head. A duplicate index, a column added twice, a
table created before the one it references, and a broken down_revision link all fail here.
"""

from __future__ import annotations

from pathlib import Path

import pytest
from alembic import command
from alembic.config import Config
from alembic.script import ScriptDirectory
from sqlalchemy import create_engine, inspect

BACKEND_ROOT = Path(__file__).resolve().parents[1]


def _alembic_config(database_url: str) -> Config:
    config = Config(str(BACKEND_ROOT / "alembic.ini"))
    config.set_main_option("script_location", str(BACKEND_ROOT / "app" / "db" / "migrations"))
    # env.py reads the URL from the environment rather than the config, so this goes through
    # monkeypatch at the call site; setting it here as well keeps offline use working.
    config.set_main_option("sqlalchemy.url", database_url)
    return config


@pytest.fixture
def database(tmp_path: Path, monkeypatch: pytest.MonkeyPatch) -> str:
    url = f"sqlite:///{tmp_path / 'chain.db'}"
    monkeypatch.setenv("CRYOSTASIS_DATABASE_URL", url)
    return url


def test_chain_applies_to_an_empty_database(database: str):
    command.upgrade(_alembic_config(database), "head")

    inspector = inspect(create_engine(database))
    tables = set(inspector.get_table_names())
    assert {
        "players",
        "cosmetics",
        "player_cosmetics",
        "chat_messages",
        "chat_mutes",
        "player_presets",
    } <= tables

    # The columns 0004 adds, which are what the presence feature rests on.
    players = {c["name"] for c in inspector.get_columns("players")}
    assert {"last_seen", "last_active"} <= players
    assert "state" in {c["name"] for c in inspector.get_columns("chat_messages")}


def test_chain_is_linear_and_ends_at_one_head(database: str):
    # Two heads mean two migrations claim the same parent, which `upgrade head` refuses to
    # resolve. It is the other way a chain breaks, and it does not need a database to catch.
    script = ScriptDirectory.from_config(_alembic_config(database))
    assert len(script.get_heads()) == 1
