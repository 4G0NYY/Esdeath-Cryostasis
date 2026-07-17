"""Short-lived bearer tokens (architecture 4).

After a caller proves it owns a UUID through the Mojang session handshake, this service
issues its own JWT. The token subject is the canonical UUID; every mutating route checks
that the subject matches the UUID in the path, which is what stops one player from setting
another player's status or appearance.

No credentials from Mojang or Microsoft are stored: the proof is transient and only the
resulting JWT lives on, in the client's memory.
"""

from __future__ import annotations

from datetime import timedelta

import jwt

from app.domain.models import normalize_uuid, now


class TokenError(Exception):
    """Raised when a token is missing, malformed, expired, or wrongly signed."""


def issue(uuid: str, *, secret: str, issuer: str, ttl_seconds: int) -> str:
    issued = now()
    payload = {
        "sub": normalize_uuid(uuid),
        "iss": issuer,
        "iat": issued,
        "exp": issued + timedelta(seconds=ttl_seconds),
    }
    return jwt.encode(payload, secret, algorithm="HS256")


def subject(token: str, *, secret: str, issuer: str) -> str:
    """Return the UUID a valid token authenticates, or raise TokenError."""
    try:
        payload = jwt.decode(
            token,
            secret,
            algorithms=["HS256"],
            issuer=issuer,
            options={"require": ["exp", "iss", "sub"]},
        )
    except jwt.PyJWTError as exc:
        raise TokenError(str(exc)) from exc
    return payload["sub"]
