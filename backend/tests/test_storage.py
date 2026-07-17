"""Unit tests for CDN URL building (architecture 7)."""

from __future__ import annotations

from app.storage.cdn import texture_url


def test_empty_key_is_none():
    assert texture_url("https://cdn.example.com", "") is None


def test_key_joins_cleanly_regardless_of_slashes():
    assert texture_url("https://cdn.example.com/", "/textures/halo.png") == (
        "https://cdn.example.com/textures/halo.png"
    )
    assert texture_url("https://cdn.example.com", "textures/halo.png") == (
        "https://cdn.example.com/textures/halo.png"
    )
