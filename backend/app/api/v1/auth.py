"""Session-proof auth (architecture 4).

Two endpoints: request a nonce, then prove ownership through the Mojang handshake and
receive a short-lived JWT. This is the security-relevant surface the rest of the service rests
on: cosmetics are free, so without it anyone could set anyone else's status or appearance, and
global chat would be back to the original relay's anyone-can-be-anyone model.
"""

from __future__ import annotations

from fastapi import APIRouter, Depends, HTTPException

from app.api.deps import get_nonces, get_repo, settings_dep
from app.auth import session as session_proof
from app.auth import tokens
from app.auth.session import NonceStore
from app.config import Settings
from app.domain.models import SessionProofBody, normalize_uuid
from app.repo.base import Repo

router = APIRouter()


@router.post("/auth/nonce")
async def issue_nonce(nonces: NonceStore = Depends(get_nonces)) -> dict:
    # The client feeds this to Mojang's joinServer before posting the proof below.
    return {"server_id": await nonces.issue()}


@router.post("/auth/session")
async def prove_session(
    body: SessionProofBody,
    nonces: NonceStore = Depends(get_nonces),
    settings: Settings = Depends(settings_dep),
    repo: Repo = Depends(get_repo),
) -> dict:
    if not await nonces.consume(body.server_id):
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

    # The handshake is the only moment this service learns a name from a source it trusts, so
    # it is where the name global chat renders gets recorded. Taking it from a request body
    # instead would let anyone post under any name.
    await repo.set_username(mojang_uuid, body.username)

    token = tokens.issue(
        mojang_uuid,
        secret=settings.jwt_secret,
        issuer=settings.jwt_issuer,
        ttl_seconds=settings.token_ttl_seconds,
    )
    return {"token": token, "token_type": "Bearer", "expires_in": settings.token_ttl_seconds}
