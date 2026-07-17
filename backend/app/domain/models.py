"""Domain schemas and rules.

Pure logic: no FastAPI, no SQLAlchemy. If a rule needs a request or a session to express
itself, it belongs in api/ or repo/, not here (architecture 3).

The wire shapes mirror docs/backend-api.md exactly, since the shipped client parses them.
"""

from __future__ import annotations

from datetime import datetime, timezone

from pydantic import BaseModel, Field, field_validator


def normalize_uuid(raw: str) -> str:
    """Canonical lower-case hyphenated UUID.

    Minecraft sends both hyphenated and undashed forms depending on the call site, so the
    store keys must not depend on which one arrived. A 32-hex-char string is hyphenated
    into 8-4-4-4-12; anything already hyphenated is just lower-cased.
    """
    cleaned = raw.strip().lower()
    hex_only = cleaned.replace("-", "")
    if len(hex_only) == 32 and all(c in "0123456789abcdef" for c in hex_only):
        return f"{hex_only[0:8]}-{hex_only[8:12]}-{hex_only[12:16]}-{hex_only[16:20]}-{hex_only[20:32]}"
    return cleaned


def now() -> datetime:
    return datetime.now(timezone.utc)


# Catalogue. Cosmetics are free for every linked account (architecture 1), so this is the
# whole ownership story: membership here is the only "does it exist" check. The client's
# ModelPart cosmetics key off these slugs (see cosmetics/render/*Cosmetic.java).
class CosmeticEntry(BaseModel):
    slug: str
    kind: str  # head, back, aura, ...
    rarity: str = "Default"
    texture_key: str = ""
    model: str = ""


# Seeded from a file and read-only at runtime (architecture 5). The three built cosmetics
# (halo, bandana, tophat) plus the ones the roadmap still lists for parity, so a client can
# select any of them the moment its renderer lands.
CATALOGUE: dict[str, CosmeticEntry] = {
    e.slug: e
    for e in [
        CosmeticEntry(slug="halo", kind="aura", rarity="Epic", model="ring"),
        CosmeticEntry(slug="bandana", kind="head", rarity="Default", model="head"),
        CosmeticEntry(slug="tophat", kind="head", rarity="Premium", model="head"),
        CosmeticEntry(slug="wings", kind="back", rarity="Epic", model="wings"),
        CosmeticEntry(slug="tail", kind="back", rarity="Default", model="tail"),
        CosmeticEntry(slug="rabbitears", kind="head", rarity="Default", model="head"),
        CosmeticEntry(slug="reifen", kind="aura", rarity="Epic", model="ring"),
        CosmeticEntry(slug="susanoo", kind="aura", rarity="Chef", model="aura"),
        CosmeticEntry(slug="stripes", kind="body", rarity="Default", model="body"),
    ]
}


class Player(BaseModel):
    """All per-player state the protocol exposes. Mirrors Store.Player from the Java dev
    instance, with last_seen replacing the stored `online` boolean (architecture 6)."""

    uuid: str
    rank: str = "Default"
    status: str = ""
    server: str = ""
    cape: str = ""
    cosmetics: set[str] = Field(default_factory=set)
    last_seen: datetime | None = None

    def is_online(self, window_seconds: int) -> bool:
        if self.last_seen is None:
            return False
        return (now() - self.last_seen).total_seconds() < window_seconds


# Request bodies. Each matches the JSON the Java server read via Gson, field for field.
class ServerBody(BaseModel):
    server: str


class StatusBody(BaseModel):
    status: str


class CapeBody(BaseModel):
    cape: str


class CosmeticBody(BaseModel):
    cosmetic: str

    @field_validator("cosmetic")
    @classmethod
    def lower(cls, v: str) -> str:
        # The Java store lower-cased on add/remove/has; the client also lower-cases on
        # parse, so the slug is canonical everywhere.
        return v.strip().lower()


class SessionProofBody(BaseModel):
    """The client's side of the Mojang session handshake (architecture 4)."""

    uuid: str
    username: str
    server_id: str  # the nonce the client just fed to Mojang's joinServer
