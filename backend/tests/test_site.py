"""The public site sharing the service with the API.

The one thing worth pinning down is precedence. The site is mounted at the root, so a reordering
in create_app that put the mount ahead of the routers would hand /api/version to StaticFiles and
break every client at once, silently and only in production, since a source checkout with no
built bundle would look fine. These tests fail loudly instead.
"""

from __future__ import annotations

from pathlib import Path

import pytest
from fastapi import FastAPI

from app.config import Settings
from app.web import mount_site

from .conftest import make_client


@pytest.fixture
def site(tmp_path: Path) -> Path:
    (tmp_path / "index.html").write_text("<!doctype html><title>Cryostasis</title>")
    (tmp_path / "assets").mkdir()
    (tmp_path / "assets" / "app.js").write_text("export {}")
    return tmp_path


async def test_api_wins_over_the_root_mount(site: Path):
    settings = Settings(database_url="", require_auth=False, site_dir=str(site))
    async with make_client(settings) as client:
        assert (await client.get("/api/version")).json()["version"]
        assert (await client.get("/health")).json() == {"status": "ok"}
        assert "Cryostasis" in (await client.get("/")).text
        assert (await client.get("/assets/app.js")).status_code == 200


async def test_a_missing_bundle_leaves_the_api_serving():
    # A source checkout has no dist/ until the frontend is built, and the test suite never builds
    # it, so the absence has to be a disabled mount rather than a failed boot.
    settings = Settings(database_url="", require_auth=False, site_dir="does/not/exist")
    async with make_client(settings) as client:
        assert (await client.get("/api/version")).status_code == 200
        assert (await client.get("/")).status_code == 404


def test_mount_reports_whether_it_mounted(site: Path):
    assert mount_site(FastAPI(), str(site)) is True
    assert mount_site(FastAPI(), str(site / "assets")) is False
