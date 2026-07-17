"""Object storage keys and CDN URL building (architecture 7).

The database stores only the object key. The API returns a CDN URL so the client
downloads bytes directly and this service never proxies them. Keys are content-addressed,
which makes them immutable and cacheable forever: a changed texture is a new key, so cache
invalidation is not a problem this service has.
"""

from __future__ import annotations


def texture_url(cdn_base_url: str, texture_key: str) -> str | None:
    """Full CDN URL for a stored texture key, or None when no key is set.

    The dev instance leaves texture_key empty for the bundled cosmetics (the client loads
    those bytes from its own mod resources), so callers must tolerate None.
    """
    if not texture_key:
        return None
    return f"{cdn_base_url.rstrip('/')}/{texture_key.lstrip('/')}"
