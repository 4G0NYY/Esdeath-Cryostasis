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


def as_utc(value: datetime) -> datetime:
    """Attach UTC to a naive timestamp.

    Everything this service stores is written as UTC, but not every driver hands it back that
    way: sqlite has no timezone-aware storage at all, so a DateTime(timezone=True) column reads
    back naive. Comparing that against `now()` raises, so timestamps that are compared in Python
    rather than in SQL pass through here first.
    """
    return value if value.tzinfo is not None else value.replace(tzinfo=timezone.utc)


# Ranks. A display tag, not an entitlement: cosmetics stay free for every linked account, so
# a rank buys colour in chat and, for the two staff tiers, the moderation calls in api/v1/chat.py.
# The stored value is the display name, which is what the pre-rank rows already hold ("Default"),
# so adding this registry needed no data migration.
class Rank(BaseModel):
    name: str
    color: str  # hex, handed to the client so the tag colour lives in one place
    staff: bool = False


RANKS: dict[str, Rank] = {
    r.name.lower(): r
    for r in [
        Rank(name="Default", color="#9AA7B8"),
        Rank(name="Premium", color="#5A8FC7"),
        Rank(name="Epic", color="#B45AC7"),
        Rank(name="Chef", color="#E8B14C"),
        Rank(name="Mod", color="#4CC77A", staff=True),
        Rank(name="Admin", color="#C75A5A", staff=True),
    ]
}

DEFAULT_RANK = RANKS["default"]


def resolve_rank(raw: str) -> Rank:
    """The registry entry for a stored rank string, falling back to Default.

    Case-insensitive because the rank arrives from an admin call typed by hand, and unknown
    values fall back rather than raise so a row written before a rank was retired still reads.
    """
    return RANKS.get(raw.strip().lower(), DEFAULT_RANK)


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


# Presence states. Derived from two timestamps rather than stored, for the reason architecture 6
# gives for presence generally: a stored state is a state something has to clear, and nothing
# clears it when a client crashes. ONLINE and AFK both mean the client is connected and beating;
# they differ only in whether the player has touched anything recently.
STATE_ONLINE = "online"
STATE_AFK = "afk"
STATE_OFFLINE = "offline"


class Player(BaseModel):
    """All per-player state the protocol exposes. Mirrors Store.Player from the Java dev
    instance, with last_seen replacing the stored `online` boolean (architecture 6).

    username is recorded during the session proof, never taken from a caller's request body, so
    the name global chat renders is one Mojang authenticated rather than one a client chose.

    last_active is the second half of presence: last_seen answers "is this client still there",
    last_active answers "is the player doing anything". A client heartbeats either way, so an
    idle player stays visible instead of blinking out the way a stored online flag would."""

    uuid: str
    username: str = ""
    rank: str = "Default"
    status: str = ""
    server: str = ""
    cape: str = ""
    cosmetics: set[str] = Field(default_factory=set)
    last_seen: datetime | None = None
    last_active: datetime | None = None

    def is_online(self, window_seconds: int) -> bool:
        if self.last_seen is None:
            return False
        return (now() - as_utc(self.last_seen)).total_seconds() < window_seconds

    def presence_state(self, window_seconds: int, afk_after_seconds: int) -> str:
        """One of the three states above.

        Offline dominates: a client that stopped heartbeating is gone whatever it was doing
        last. A player who has never been active reads as AFK rather than online, since a fresh
        record has nothing to say they were ever at the keyboard.
        """
        if not self.is_online(window_seconds):
            return STATE_OFFLINE
        if self.last_active is None:
            return STATE_AFK
        idle = (now() - as_utc(self.last_active)).total_seconds()
        return STATE_AFK if idle >= afk_after_seconds else STATE_ONLINE


class PresenceView(BaseModel):
    """A player as the presence endpoints report them: the derived state plus everything a
    client needs to render a roster line without a second lookup.

    `status` is the free-text line a player sets for themselves and is deliberately separate
    from `state`, which nobody sets: one is a mood, the other is a fact about their client.
    """

    uuid: str
    username: str = ""
    rank: str = "Default"
    color: str = DEFAULT_RANK.color
    state: str = STATE_OFFLINE
    status: str = ""
    server: str = ""
    last_seen: datetime | None = None
    last_active: datetime | None = None


def presence_view(player: Player, window_seconds: int, afk_after_seconds: int) -> PresenceView:
    entry = resolve_rank(player.rank)
    return PresenceView(
        uuid=player.uuid,
        username=player.username,
        rank=entry.name,
        color=entry.color,
        state=player.presence_state(window_seconds, afk_after_seconds),
        status=player.status,
        server=player.server,
        last_seen=player.last_seen,
        last_active=player.last_active,
    )


# Request bodies. Each matches the JSON the Java server read via Gson, field for field.
class ServerBody(BaseModel):
    server: str


class StatusBody(BaseModel):
    status: str


class PresenceBody(BaseModel):
    """The heartbeat body. Optional in full: the recovered addMe carried nothing, and a client
    written against that contract still beats correctly and simply never leaves AFK.

    `active` is the client saying the player did something in world since its last beat, which is
    the only thing that can distinguish idle from gone; a server that saw only heartbeats could
    not tell them apart. `server` piggybacks so joining a server is one call rather than two.
    """

    active: bool = False
    server: str | None = None


class PresenceBatchBody(BaseModel):
    """Presence for a named set of players, for the surfaces that ask about who they can see
    rather than about who is around: nametags and chat lines."""

    uuids: list[str]


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


class RankBody(BaseModel):
    """Admin-only rank assignment. Validated against the registry here rather than in the
    router, so an unknown rank can never reach the store."""

    rank: str

    @field_validator("rank")
    @classmethod
    def known(cls, v: str) -> str:
        entry = RANKS.get(v.strip().lower())
        if entry is None:
            raise ValueError(f"unknown rank: {v}")
        # Store the registry's spelling, so case typed by hand does not become the stored value.
        return entry.name


# Global chat. Messages carry the sender's rank and its colour as a snapshot taken at post time:
# the client then renders a line with no further lookups, and a later promotion does not rewrite
# what the log says the sender was.
class ChatMessage(BaseModel):
    id: int
    uuid: str
    username: str
    rank: str
    color: str
    message: str
    at: datetime
    # The sender's presence state as it stood when the line landed, snapshotted for the same
    # reason rank and colour are. It says whether their character was parked while they typed,
    # which on a channel spanning many game servers is the difference between someone playing
    # and someone watching the chat from a menu.
    state: str = STATE_ONLINE

    @field_validator("at")
    @classmethod
    def utc(cls, v: datetime) -> datetime:
        return as_utc(v)


class ChatBody(BaseModel):
    message: str

    # Only honoured with auth off, where there is no token to take an identity from. With auth
    # on both are ignored in favour of the token subject and the stored username, so a caller
    # cannot post under someone else's name.
    uuid: str | None = None
    username: str | None = None

    @field_validator("message")
    @classmethod
    def trimmed(cls, v: str) -> str:
        # Control characters would let a message forge the section-sign formatting or the line
        # breaks the client renders, so they are stripped rather than escaped downstream.
        cleaned = "".join(c for c in v if c.isprintable()).strip()
        if not cleaned:
            raise ValueError("message is empty")
        return cleaned


class Mute(BaseModel):
    uuid: str
    username: str = ""
    until: datetime
    reason: str = ""
    by: str = ""

    @field_validator("until")
    @classmethod
    def utc(cls, v: datetime) -> datetime:
        return as_utc(v)

    def is_active(self) -> bool:
        return self.until > now()


class MuteBody(BaseModel):
    """A staff mute. `player` takes a UUID or a username, since a moderator in game reads names
    off chat lines and never sees a UUID."""

    player: str
    minutes: int = 10
    reason: str = ""

    @field_validator("minutes")
    @classmethod
    def positive(cls, v: int) -> int:
        if v < 1:
            raise ValueError("minutes must be at least 1")
        # A day is the ceiling because chat history is swept daily anyway, and a longer mute is
        # a ban, which is a decision this service deliberately does not model.
        return min(v, 24 * 60)
