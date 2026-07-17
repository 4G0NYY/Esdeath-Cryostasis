"""Multi-host DSN support for HA Postgres (Patroni + pgbouncer client-side failover).

A libpq multi-host URL - `postgresql://user:pw@h1:6433,h2:6433,h3:6433/db?target_session_attrs=read-write`
- lets the client itself pick the read-write primary among several nodes, so no VIP or
HAProxy sits in front as a single point of failure. SQLAlchemy's URL parser cannot read that
authority: it int-parses the whole `h1:6433,h2:6433,...` as one port and raises. Both drivers
this app uses, however, take multiple hosts natively when they arrive as connect arguments
rather than inside the URL string - asyncpg as a `host` list, psycopg2/libpq as a comma-joined
`host` string - and both honor `target_session_attrs`.

So for a multi-host URL we lift the hosts, ports and query out of the string, hand SQLAlchemy a
hostless URL it can parse, and give the driver the rest through connect_args. A single-host URL
is not multi-host and `parse_multihost` returns None, so the ordinary code path is untouched.
"""

from __future__ import annotations

from dataclasses import dataclass
from urllib.parse import parse_qsl, urlsplit

from sqlalchemy.engine import URL


@dataclass(frozen=True)
class MultiHost:
    username: str | None
    password: str | None
    database: str | None
    hosts: list[str]
    ports: list[str]  # kept as written; "" means "let the driver use its default"
    query: dict[str, str]


def _canonical(url: str) -> str:
    # Accept the postgres:// alias some platforms hand out, matching session.py._normalize.
    if url.startswith("postgres://"):
        return "postgresql://" + url[len("postgres://") :]
    return url


def parse_multihost(url: str) -> MultiHost | None:
    """Parse a multi-host Postgres URL; return None when the authority names one host.

    Only the authority is split by hand. Everything else goes through urlsplit, which does not
    validate the port until `.port` is read, so a comma authority survives to be parsed here
    instead of raising the way SQLAlchemy's stricter parser does.
    """
    split = urlsplit(_canonical(url))
    userinfo, at, hostspec = split.netloc.rpartition("@")
    if not at:  # no credentials in the URL: the whole netloc is the host spec
        userinfo, hostspec = "", split.netloc
    if "," not in hostspec:
        return None  # single host: the caller keeps its existing single-host path

    username = password = None
    if userinfo:
        username, _, password = userinfo.partition(":")
        username, password = username or None, password or None

    hosts: list[str] = []
    ports: list[str] = []
    for entry in hostspec.split(","):
        host, sep, port = entry.rpartition(":")
        if not sep:  # entry had no colon, so rpartition put the host in `port`
            host, port = port, ""
        hosts.append(host)
        ports.append(port)

    return MultiHost(
        username=username,
        password=password,
        database=split.path.lstrip("/") or None,
        hosts=hosts,
        ports=ports,
        query=dict(parse_qsl(split.query)),
    )


def _hostless(drivername: str, mh: MultiHost) -> URL:
    # URL.create escapes the password for us, so it never has to be reconstructed by hand.
    # host/port are left off entirely and supplied through connect_args instead.
    return URL.create(
        drivername,
        username=mh.username,
        password=mh.password,
        database=mh.database,
    )


def async_engine_args(mh: MultiHost) -> tuple[URL, dict]:
    """A hostless SQLAlchemy URL and asyncpg connect_args for a multi-host DSN.

    asyncpg takes `host` as a list and `port` as a matching list of ints; a missing port
    falls back to the Postgres default. `target_session_attrs` (and any other query key) is
    passed straight through - asyncpg understands it.
    """
    connect_args = {
        "host": mh.hosts,
        "port": [int(p) if p else 5432 for p in mh.ports],
        **mh.query,
    }
    return _hostless("postgresql+asyncpg", mh), connect_args


def sync_engine_args(mh: MultiHost) -> tuple[URL, dict]:
    """A hostless SQLAlchemy URL and psycopg2 connect_args for a multi-host DSN.

    libpq reads a comma-joined `host`/`port` and honors `target_session_attrs`, so the sync
    migration path fails over to the primary exactly like the async app does.
    """
    connect_args = {
        "host": ",".join(mh.hosts),
        "port": ",".join(mh.ports),
        **mh.query,
    }
    return _hostless("postgresql", mh), connect_args
