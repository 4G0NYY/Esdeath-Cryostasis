"""Twelve-factor configuration.

Only DATABASE_URL is required in production. Everything else has a default that is safe
for a local run, so `uvicorn app.main:app` works with no environment at all.
"""

from __future__ import annotations

from functools import lru_cache

from pydantic_settings import BaseSettings, SettingsConfigDict


class Settings(BaseSettings):
    model_config = SettingsConfigDict(env_prefix="CRYOSTASIS_", env_file=".env", extra="ignore")

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


@lru_cache
def get_settings() -> Settings:
    return Settings()
