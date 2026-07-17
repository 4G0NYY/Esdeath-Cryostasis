"""Session-proof auth (architecture 4).

Two endpoints: request a nonce, then prove ownership through the Mojang handshake and
receive a short-lived JWT. This is the only security-relevant surface in the service, since
cosmetics are free; without it anyone could set anyone else's status or appearance.
"""

from __future__ import annotations

from fastapi import APIRouter, Depends, HTTPException

from app.api.deps import get_nonces, settings_dep
from app.auth import session as session_proof
from app.auth import tokens
from app.auth.session import NonceStore
from app.config import Settings
from app.domain.models import SessionProofBody, normalize_uuid

router = APIRouter()


@router.post("/auth/nonce")
async def issue_nonce(nonces: NonceStore = Depends(get_nonces)) -> dict:
    # The client feeds this to Mojang's joinServer before posting the proof below.
    return {"server_id": nonces.issue()}


@router.post("/auth/session")
async def prove_session(
    body: SessionProofBody,
    nonces: NonceStore = Depends(get_nonces),
    settings: Settings = Depends(settings_dep),
) -> dict:
    if not nonces.consume(body.server_id):
        raise HTTPException(status_code=400, detail="unknown or expired nonce")

    try:
        mojang_uuid = await session_proof.verify_with_mojang(
            body.username,
            body.server_id,
            has_joined_url=settings.mojang_has_joined_url,
            timeout_seconds=settings.mojang_timeout_seconds,
        )
    except session_proof.ProofError as exc:
        raise HTTPException(status_code=401, detail=str(exc)) from exc

    # The account Mojang authenticated must be the one the caller claims to be acting as.
    if mojang_uuid != normalize_uuid(body.uuid):
        raise HTTPException(status_code=401, detail="session does not match claimed uuid")

    token = tokens.issue(
        mojang_uuid,
        secret=settings.jwt_secret,
        issuer=settings.jwt_issuer,
        ttl_seconds=settings.token_ttl_seconds,
    )
    return {"token": token, "token_type": "Bearer", "expires_in": settings.token_ttl_seconds}
