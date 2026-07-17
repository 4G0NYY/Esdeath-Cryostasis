"""Test fixtures.

The whole suite runs against repo/memory.py, so it needs no database and finishes in
seconds in CI (architecture 10). Each test gets a fresh app so state never leaks between
cases.
"""

from __future__ import annotations

import contextlib
from collections.abc import AsyncIterator

import pytest
import pytest_asyncio
from httpx import ASGITransport, AsyncClient

from app.config import Settings
from app.main import create_app


@contextlib.asynccontextmanager
async def make_client(settings: Settings) -> AsyncIterator[AsyncClient]:
    app = create_app(settings)
    # httpx's ASGITransport does not fire lifespan events, so drive the lifespan by hand to
    # populate app.state (repo, nonces, rate limiter) the routes depend on.
    async with app.router.lifespan_context(app):
        transport = ASGITransport(app=app)
        async with AsyncClient(transport=transport, base_url="http://test") as c:
            yield c


@pytest_asyncio.fixture
async def client() -> AsyncIterator[AsyncClient]:
    """Open dev mode: no database, no auth. The default the client is smoke-tested against."""
    async with make_client(Settings(database_url="", require_auth=False)) as c:
        yield c


@pytest.fixture
def uuid() -> str:
    return "11111111-2222-3333-4444-555555555555"
