"""Dependency injection: repo, settings, the authenticated caller, and the rate limiter.

Shared singletons (the repo, the nonce store, the rate limiters) live on app.state, set up
in the lifespan handler in main.py. These functions just read them off the request, so the
same wiring serves tests (which inject a MemoryRepo) and production (Postgres).
"""

from __future__ import annotations

import secrets
import time
from collections import deque

from fastapi import Depends, HTTPException, Path, Request

from app.auth import tokens
from app.auth.session import NonceStore
from app.config import Settings
from app.domain.models import normalize_uuid, resolve_rank
from app.repo.base import Repo


def get_repo(request: Request) -> Repo:
    return request.app.state.repo


def get_nonces(request: Request) -> NonceStore:
    return request.app.state.nonces


def settings_dep(request: Request) -> Settings:
    # Read the settings this app was built with, not the process-global cache, so a test or
    # an alternate factory instance gets the config it asked for.
    return request.app.state.settings


class RateLimiter:
    """Sliding-window limiter keyed by authenticated UUID, not IP, so players behind one NAT do
    not share a bucket (architecture 8). Fixed at one minute; the cap is the config value.

    Deliberately in-process, and the one component that is not shared across replicas: the
    effective cap becomes per_minute times the replica count, since each replica keeps its own
    buckets. That is an accepted trade for a cosmetics backend, where the limiter is abuse
    protection rather than a metered quota, and it keeps this hot check off the database. A
    strict cluster-wide cap would need a shared store (Postgres or Redis) and a round-trip per
    mutating request. Everything else in the service is stateless: player data and presence
    live in Postgres, tokens are stateless JWTs, and nonces moved to a shared store."""

    def __init__(self, per_minute: int) -> None:
        self._per_minute = per_minute
        self._hits: dict[str, deque[float]] = {}

    def check(self, key: str, now_monotonic: float) -> bool:
        window_start = now_monotonic - 60.0
        hits = self._hits.setdefault(key, deque())
        while hits and hits[0] < window_start:
            hits.popleft()
        if len(hits) >= self._per_minute:
            return False
        hits.append(now_monotonic)
        return True


def get_rate_limiter(request: Request) -> RateLimiter:
    return request.app.state.rate_limiter


def get_chat_limiter(request: Request) -> RateLimiter:
    # Its own bucket: a chat line is the one write a player repeats deliberately, so it wants a
    # far tighter cap than a cosmetic toggle without starving one from the other.
    return request.app.state.chat_limiter


def require_caller(
    uuid: str = Path(...),
    request: Request = None,  # type: ignore[assignment]
    settings: Settings = Depends(settings_dep),
    limiter: RateLimiter = Depends(get_rate_limiter),
) -> str:
    """Authorize a mutating call on {uuid} and return the canonical target UUID.

    With auth off (dev), any caller may act on any UUID and the rate bucket is the target
    UUID. With auth on, the bearer token's subject must equal the path UUID, which is the
    single check that prevents impersonation now that cosmetics are free (architecture 4).
    """
    target = normalize_uuid(uuid)

    if not settings.require_auth:
        _enforce_rate(limiter, target)
        return target

    header = request.headers.get("Authorization", "")
    if not header.startswith("Bearer "):
        raise HTTPException(status_code=401, detail="missing bearer token")
    try:
        caller = tokens.subject(
            header[len("Bearer ") :], secret=settings.jwt_secret, issuer=settings.jwt_issuer
        )
    except tokens.TokenError as exc:
        raise HTTPException(status_code=401, detail=f"invalid token: {exc}") from exc

    if caller != target:
        raise HTTPException(status_code=403, detail="token does not own this uuid")

    _enforce_rate(limiter, caller)
    return target


def _enforce_rate(limiter: RateLimiter, key: str) -> None:
    if not limiter.check(key, time.monotonic()):
        raise HTTPException(status_code=429, detail="rate limit exceeded")


def bearer_subject(request: Request, settings: Settings) -> str | None:
    """The UUID a presented bearer token authenticates, or None when there is no token.

    Unlike require_caller this takes no path UUID, because the chat routes act on the caller
    rather than on a named player. A malformed or expired token is a 401 even with auth off: a
    caller that tried to authenticate and failed should hear about it rather than be silently
    downgraded to anonymous.
    """
    header = request.headers.get("Authorization", "")
    if not header.startswith("Bearer "):
        return None
    try:
        return tokens.subject(
            header[len("Bearer ") :], secret=settings.jwt_secret, issuer=settings.jwt_issuer
        )
    except tokens.TokenError as exc:
        raise HTTPException(status_code=401, detail=f"invalid token: {exc}") from exc


def require_authenticated(
    request: Request, settings: Settings = Depends(settings_dep)
) -> str | None:
    """The authenticated caller for a route that acts on whoever is calling.

    Returns None with auth off, which is the same open posture require_caller takes: the dev
    instance has no identity to offer, so the route falls back to what the request body claims.
    """
    caller = bearer_subject(request, settings)
    if settings.require_auth and caller is None:
        raise HTTPException(status_code=401, detail="missing bearer token")
    return caller


def require_admin(request: Request, settings: Settings = Depends(settings_dep)) -> None:
    """Guard the admin surface, which today is only rank assignment.

    An unset admin token disables the surface outright rather than falling back to a weaker
    check, so a deployment that forgot to configure one cannot have its ranks rewritten.
    """
    if not settings.admin_token:
        raise HTTPException(status_code=403, detail="admin surface is not configured")
    if not secrets.compare_digest(request.headers.get("X-Admin-Token", ""), settings.admin_token):
        raise HTTPException(status_code=403, detail="invalid admin token")


async def require_staff(
    request: Request,
    settings: Settings = Depends(settings_dep),
    repo: Repo = Depends(get_repo),
) -> str:
    """Authorize a chat moderation call and return the acting UUID.

    Two ways in: the admin token, which acts as the empty UUID because it belongs to an operator
    rather than a player, or a bearer token whose player carries a staff rank. With auth off this
    passes for anyone, matching the open dev posture the rest of the service takes.
    """
    if settings.admin_token and secrets.compare_digest(
        request.headers.get("X-Admin-Token", ""), settings.admin_token
    ):
        return ""

    caller = bearer_subject(request, settings)
    if caller is None:
        if not settings.require_auth:
            return ""
        raise HTTPException(status_code=401, detail="missing bearer token")

    player = await repo.get_player(caller)
    if not resolve_rank(player.rank).staff:
        raise HTTPException(status_code=403, detail="staff rank required")
    return caller
