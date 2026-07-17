"""Dependency injection: repo, settings, the authenticated caller, and the rate limiter.

Shared singletons (the repo, the nonce store, the rate limiter) live on app.state, set up
in the lifespan handler in main.py. These functions just read them off the request, so the
same wiring serves tests (which inject a MemoryRepo) and production (Postgres).
"""

from __future__ import annotations

import time
from collections import deque

from fastapi import Depends, HTTPException, Path, Request

from app.auth import tokens
from app.auth.session import NonceStore
from app.config import Settings
from app.domain.models import normalize_uuid
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
