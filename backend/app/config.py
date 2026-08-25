"""Twelve-factor configuration.

Only DATABASE_URL is required in production. Everything else has a default that is safe
for a local run, so `uvicorn app.main:app` works with no environment at all.
"""

from __future__ import annotations

import os
from functools import lru_cache

from pydantic_settings import BaseSettings, SettingsConfigDict

# Docker secrets land here as files named for the env var, e.g. /run/secrets/CRYOSTASIS_JWT_SECRET.
# Pointed at only when it exists so a Swarm deploy reads secrets from files while a plain dev run
# (where the directory is absent) stays silent instead of warning on every Settings build.
_SECRETS_DIR = "/run/secrets"


class Settings(BaseSettings):
    model_config = SettingsConfigDict(
        env_prefix="CRYOSTASIS_",
        env_file=".env",
        extra="ignore",
        secrets_dir=_SECRETS_DIR if os.path.isdir(_SECRETS_DIR) else None,
    )

    version: str = "cryostasis-1"

    # Empty selects repo/memory.py. That is the default because it lets the service boot
    # with no database, which is what the test suite and a quick client smoke test need.
    database_url: str = ""

    # Signs the tokens issued after a session proof. The default is refused at boot when
    # auth is enforced, so a real deployment cannot accidentally ship a known secret.
    jwt_secret: str = "dev-insecure-secret"
    jwt_issuer: str = "cryostasis.ramon.moe"
    token_ttl_seconds: int = 24 * 60 * 60

    # With auth off, any caller may mutate any UUID. This mirrors the Java dev instance's
    # open mode and is the only way to test the client before the session handshake ships
    # on the client side.
    require_auth: bool = False

    # A player counts as online while their last heartbeat is inside this window. Derived
    # rather than stored, so a crashed client goes offline on its own (architecture 6).
    presence_window_seconds: int = 120

    # Object storage: the database holds only a key, the API returns a CDN URL built from
    # this base, and texture bytes never pass through this service (architecture 7).
    cdn_base_url: str = "https://cdn.cryostasis.ramon.moe"

    mojang_has_joined_url: str = "https://sessionserver.mojang.com/session/minecraft/hasJoined"
    mojang_timeout_seconds: float = 5.0

    # Keyed by authenticated UUID, not IP, so players behind one NAT do not share a bucket
    # (architecture 8).
    rate_limit_per_minute: int = 120

    cosmetics_cache_seconds: int = 30

    # Grants the admin surface, which today is only "set a player's rank". Empty disables that
    # surface outright rather than falling back to some weaker check, so a deployment that
    # forgot to set it cannot have its ranks rewritten by anyone.
    admin_token: str = ""

    # Global chat. The message cap is generous enough for a sentence and small enough that a
    # single line cannot push the rest of a player's chat off screen.
    chat_max_length: int = 256

    # Its own bucket, separate from rate_limit_per_minute: posting a chat line is the one
    # write a player repeats deliberately, and it wants a much tighter limit than a cosmetic
    # toggle does.
    chat_rate_per_minute: int = 12

    chat_history_limit: int = 100

    # Long-poll ceiling. A reader asks to be held open for up to this long, which keeps an idle
    # client to roughly one request per this many seconds instead of one per poll interval.
    chat_poll_max_wait_seconds: float = 25.0
    chat_poll_interval_seconds: float = 1.0

    # Messages older than this are swept when a new one is posted. Global chat is a live
    # channel, not an archive, so nothing needs to keep yesterday's lines.
    chat_retention_hours: int = 24


@lru_cache
def get_settings() -> Settings:
    return Settings()
