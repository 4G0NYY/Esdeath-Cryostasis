"""Serving the public site off the same service as the API.

The site is a static React bundle mounted at the root, behind the routers rather than beside
them. Starlette matches routes in the order they were added, so /api and /health are claimed
first and the mount only ever sees what is left; the mount is added last in create_app for
exactly that reason.

Sharing the service is a deliberate choice, not a shortcut. The site's only dynamic content is
the presence roster it reads from this very backend, so a separate origin would buy a second
container, a second deploy and a CORS policy in order to fetch from the service it is already
sitting on. It also means the page cannot claim the backend is up while it is down: an
unreachable API is a page that says so.

The bundle is optional. A source checkout has no dist/ until someone runs the frontend build, and
the API must still boot for the test suite and for a bare `uvicorn app.main:app`, so a missing
directory disables the mount rather than failing the boot.
"""

from __future__ import annotations

from pathlib import Path

from fastapi import FastAPI
from starlette.staticfiles import StaticFiles


def mount_site(app: FastAPI, directory: str) -> bool:
    """Mount the built site at the root. Returns whether there was one to mount."""
    root = Path(directory)
    if not (root / "index.html").is_file():
        return False

    # html=True serves index.html for the bare root. The site is a single page with no client
    # routing, so nothing else needs a fallback and an unknown path stays a plain 404 rather
    # than silently answering with the app shell.
    app.mount("/", StaticFiles(directory=root, html=True), name="site")
    return True
