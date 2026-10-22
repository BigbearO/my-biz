"""Local HTTP routes for system state and login handover."""

from fastapi import APIRouter, Depends, HTTPException, status

from xianyu_client.api.dependencies import get_auth_service, get_system_service, validate_local_token
from xianyu_client.core.errors import AuthTokenUnavailableError
from xianyu_client.schemas.auth_schemas import AuthStateResponse, LoginRequest, TokenResponse
from xianyu_client.schemas.system_schemas import SystemStatusResponse
from xianyu_client.services.auth_service import AuthService
from xianyu_client.services.system_service import SystemService

router = APIRouter(prefix="/api/system", dependencies=[Depends(validate_local_token)])


@router.get("/status", response_model=SystemStatusResponse)
async def get_system_status(
    system_service: SystemService = Depends(get_system_service),
) -> SystemStatusResponse:
    """Return local service status for the renderer status panel."""
    return await system_service.get_status()


@router.post("/login", response_model=AuthStateResponse, response_model_by_alias=True)
async def hand_over_login(
    request: LoginRequest,
    auth_service: AuthService = Depends(get_auth_service),
) -> AuthStateResponse:
    """Store the token pair handed over by the Electron login flow.

    Args:
        request: Access token and its paired client id.
        auth_service: Service owning the encrypted token storage.

    Returns:
        The authorization state after the startup access check.

    Raises:
        HTTPException: When the token pair cannot be encrypted for storage.
    """
    try:
        auth_state = await auth_service.login(request.token, request.client_id)
    except AuthTokenUnavailableError as error:
        raise HTTPException(
            status_code=status.HTTP_503_SERVICE_UNAVAILABLE,
            detail=str(error),
        ) from error
    return AuthStateResponse(auth_state=auth_state)


@router.get("/token", response_model=TokenResponse, response_model_by_alias=True)
async def get_login_token(
    auth_service: AuthService = Depends(get_auth_service),
) -> TokenResponse:
    """Return the stored token pair to the trusted Electron process.

    Raises:
        HTTPException: When the stored token cannot be decrypted.
    """
    try:
        token, client_id = await auth_service.get_credentials()
    except AuthTokenUnavailableError as error:
        raise HTTPException(
            status_code=status.HTTP_503_SERVICE_UNAVAILABLE,
            detail=str(error),
        ) from error
    return TokenResponse(token=token, client_id=client_id)


@router.post("/logout", response_model=AuthStateResponse, response_model_by_alias=True)
async def logout(
    auth_service: AuthService = Depends(get_auth_service),
) -> AuthStateResponse:
    """Clear the stored token pair and return the resulting state."""
    auth_state = await auth_service.logout()
    return AuthStateResponse(auth_state=auth_state)
