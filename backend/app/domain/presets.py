"""Module presets: named snapshots of which modules a player has on and how each is set.

The backend stores a preset without understanding it. `modules` is the client's own shape, keyed
by lower-cased module name, and the client is the only reader, so a module added to the client
needs no change here. What this service does own is the envelope: whose a preset is, what it may
be called, and how many and how large they may be.
"""

from __future__ import annotations

import json
from datetime import datetime
from typing import Any

from pydantic import BaseModel, Field, field_validator

from app.domain.models import as_utc

MAX_PRESETS = 32
MAX_NAME_LENGTH = 24
# Every module with every setting serializes to a few kilobytes, so this is a ceiling on abuse,
# not on anything a real client sends.
MAX_MODULES_BYTES = 32 * 1024
# The client's built-in preset. It is a rule rather than a snapshot and is never stored, so a
# stored preset of the same name would show up twice in the menu.
RESERVED_NAMES = {"qol"}


class ModuleState(BaseModel):
    enabled: bool = False
    settings: dict[str, Any] = Field(default_factory=dict)


class Preset(BaseModel):
    name: str
    modules: dict[str, ModuleState]
    updated_at: datetime

    @field_validator("updated_at")
    @classmethod
    def utc(cls, v: datetime) -> datetime:
        return as_utc(v)


class PresetBody(BaseModel):
    modules: dict[str, ModuleState]

    @field_validator("modules", mode="before")
    @classmethod
    def bounded(cls, v: Any) -> Any:
        if len(json.dumps(v, separators=(",", ":"))) > MAX_MODULES_BYTES:
            raise ValueError(f"modules exceed {MAX_MODULES_BYTES} bytes")
        return v


def validate_name(raw: str) -> str:
    """The stored form of a preset name, or ValueError.

    Letters, digits, spaces, dashes and underscores only. The name travels as a path segment, and
    an encoded slash is decoded before routing, so a name holding one could never be deleted.
    """
    name = raw.strip()
    if not name:
        raise ValueError("preset name is empty")
    if len(name) > MAX_NAME_LENGTH:
        raise ValueError(f"preset name is longer than {MAX_NAME_LENGTH} characters")
    if not all(c.isalnum() or c in " -_" for c in name):
        raise ValueError("preset name may only hold letters, digits, spaces, - and _")
    if name.lower() in RESERVED_NAMES:
        raise ValueError(f"{name} is reserved for the built-in preset")
    return name
