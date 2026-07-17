"""Multi-host DSN parsing and engine wiring (app/db/dsn.py).

The production database is an HA Postgres reached through a libpq multi-host failover URL,
which SQLAlchemy's URL parser rejects outright. These tests pin the workaround: the hosts,
ports and target_session_attrs are lifted into driver connect_args, and a single-host URL is
left on its ordinary path. asyncpg is a real dependency so the async engine is built for real
(construction does not open a connection); psycopg2 is not installed here - it lives only in
the runtime image - so the sync path is checked at the argument-builder level, which is where
all the shaping logic lives.
"""

from __future__ import annotations

from sqlalchemy.engine import make_url
from sqlalchemy.ext.asyncio import AsyncEngine

from app.db.dsn import async_engine_args, parse_multihost, sync_engine_args
from app.db.session import make_engine

MULTI = (
    "postgresql://cryostasisdb:secretpw@192.168.2.100:6433,192.168.2.101:6433,"
    "192.168.2.102:6433/cryostasisdb?target_session_attrs=read-write"
)


def test_single_host_is_not_multihost():
    assert parse_multihost("postgresql://u:p@db:5432/cryostasis") is None
    assert parse_multihost("sqlite+aiosqlite://") is None
    assert parse_multihost("postgresql://u:p@pgbouncer:6432/db?target_session_attrs=read-write") is None


def test_parse_pulls_out_every_component():
    mh = parse_multihost(MULTI)
    assert mh is not None
    assert mh.username == "cryostasisdb"
    assert mh.password == "secretpw"
    assert mh.database == "cryostasisdb"
    assert mh.hosts == ["192.168.2.100", "192.168.2.101", "192.168.2.102"]
    assert mh.ports == ["6433", "6433", "6433"]
    assert mh.query == {"target_session_attrs": "read-write"}


def test_postgres_alias_and_missing_port():
    mh = parse_multihost("postgres://u:p@a,b:5433/db")
    assert mh is not None
    assert mh.hosts == ["a", "b"]
    assert mh.ports == ["", "5433"]  # "" means the driver's default port


def test_async_args_are_hostless_url_plus_asyncpg_connect_args():
    mh = parse_multihost(MULTI)
    url, connect_args = async_engine_args(mh)
    # The URL SQLAlchemy sees carries no host, so its parser never chokes on the comma authority.
    assert url.host is None
    assert url.get_backend_name() == "postgresql" and url.get_driver_name() == "asyncpg"
    assert url.username == "cryostasisdb" and url.database == "cryostasisdb"
    # asyncpg takes a host list and a matching int port list, plus target_session_attrs verbatim.
    assert connect_args["host"] == ["192.168.2.100", "192.168.2.101", "192.168.2.102"]
    assert connect_args["port"] == [6433, 6433, 6433]
    assert connect_args["target_session_attrs"] == "read-write"


def test_sync_args_use_libpq_comma_joined_host_and_port():
    mh = parse_multihost(MULTI)
    url, connect_args = sync_engine_args(mh)
    assert url.host is None
    assert url.get_backend_name() == "postgresql" and url.get_driver_name() == "psycopg2"
    # libpq reads comma-separated host/port and honors target_session_attrs.
    assert connect_args["host"] == "192.168.2.100,192.168.2.101,192.168.2.102"
    assert connect_args["port"] == "6433,6433,6433"
    assert connect_args["target_session_attrs"] == "read-write"


def test_make_engine_builds_a_multihost_engine_without_raising():
    # The regression this whole change exists for: make_url(MULTI) raises, but make_engine must
    # not. Construction does not connect, so this is safe without a database.
    engine = make_engine(MULTI)
    assert isinstance(engine, AsyncEngine)
    assert engine.url.get_driver_name() == "asyncpg"
    assert engine.url.host is None


def test_the_raw_url_still_defeats_sqlalchemy_directly():
    # Guards the premise: if a future SQLAlchemy learns to parse this, revisit whether the
    # workaround is still needed. Until then, the raw multi-host URL must fail to parse.
    try:
        make_url(MULTI)
    except Exception:
        return
    raise AssertionError("expected make_url to reject the multi-host authority")
