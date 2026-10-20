"""Local HTTP routes for Goodfish account management."""

from typing import NoReturn

from fastapi import APIRouter, Depends, HTTPException, status

from xianyu_client.api.dependencies import get_account_service, validate_local_token
from xianyu_client.core.constants import (
    ACCOUNT_DUPLICATE_ERROR_MESSAGE,
    ACCOUNT_NOT_FOUND_ERROR_MESSAGE,
    ACCOUNT_STATE_CONFLICT_ERROR_MESSAGE,
    CREDENTIAL_UNAVAILABLE_ERROR_MESSAGE,
    INVALID_XIANYU_COOKIE_ERROR_MESSAGE,
    XIANYU_COOKIE_MISMATCH_ERROR_MESSAGE,
    XIANYU_NETWORK_ERROR_MESSAGE,
    XIANYU_PROTOCOL_ERROR_MESSAGE,
    XIANYU_RISK_CONTROL_ERROR_MESSAGE,
)
from xianyu_client.core.errors import (
    AccountNotFoundError,
    AccountStateConflictError,
    CredentialUnavailableError,
    DuplicateGoodfishIdError,
    InvalidXianyuCookieError,
    XianyuCookieMismatchError,
    XianyuNetworkError,
    XianyuProtocolError,
    XianyuRiskControlError,
)
from xianyu_client.schemas.account_schemas import (
    AccountCreateRequest,
    AccountResponse,
    AccountUpdateRequest,
)
from xianyu_client.services.account_service import AccountService

router = APIRouter(prefix="/api/accounts", dependencies=[Depends(validate_local_token)])

_PROTOCOL_ERROR_RESPONSES = (
    (InvalidXianyuCookieError, status.HTTP_400_BAD_REQUEST, INVALID_XIANYU_COOKIE_ERROR_MESSAGE),
    (XianyuCookieMismatchError, status.HTTP_409_CONFLICT, XIANYU_COOKIE_MISMATCH_ERROR_MESSAGE),
    (XianyuRiskControlError, status.HTTP_409_CONFLICT, XIANYU_RISK_CONTROL_ERROR_MESSAGE),
    (XianyuNetworkError, status.HTTP_503_SERVICE_UNAVAILABLE, XIANYU_NETWORK_ERROR_MESSAGE),
    (XianyuProtocolError, status.HTTP_502_BAD_GATEWAY, XIANYU_PROTOCOL_ERROR_MESSAGE),
)
_PROTOCOL_ERROR_TYPES = tuple(item[0] for item in _PROTOCOL_ERROR_RESPONSES)


@router.get("", response_model=list[AccountResponse], response_model_by_alias=True)
async def list_accounts(
    account_service: AccountService = Depends(get_account_service),
) -> list[AccountResponse]:
    """List local accounts without exposing cookie material.

    Args:
        account_service: Service providing account orchestration.

    Returns:
        Non-secret representations of every local account.
    """
    return await account_service.list_accounts()


@router.post("", response_model=AccountResponse, response_model_by_alias=True, status_code=status.HTTP_201_CREATED)
async def create_account(
    request: AccountCreateRequest,
    account_service: AccountService = Depends(get_account_service),
) -> AccountResponse:
    """Create one local account with an encrypted cookie.

    Args:
        request: Validated account creation payload.
        account_service: Service providing account orchestration.

    Returns:
        The persisted non-secret account representation.

    Raises:
        HTTPException: When the Goodfish ID is duplicate or credentials are unavailable.
    """
    try:
        return await account_service.create_account(request)
    except DuplicateGoodfishIdError as error:
        raise HTTPException(
            status_code=status.HTTP_409_CONFLICT,
            detail=ACCOUNT_DUPLICATE_ERROR_MESSAGE,
        ) from error
    except CredentialUnavailableError as error:
        raise HTTPException(
            status_code=status.HTTP_503_SERVICE_UNAVAILABLE,
            detail=CREDENTIAL_UNAVAILABLE_ERROR_MESSAGE,
        ) from error
    except _PROTOCOL_ERROR_TYPES as error:
        _raise_protocol_error(error)


@router.put("/{account_id}", response_model=AccountResponse, response_model_by_alias=True)
async def update_account(
    account_id: int,
    request: AccountUpdateRequest,
    account_service: AccountService = Depends(get_account_service),
) -> AccountResponse:
    """Edit account profile fields and optionally replace its cookie.

    Args:
        account_id: Local database identifier.
        request: Validated profile and optional cookie replacement payload.
        account_service: Service providing account orchestration.

    Returns:
        The updated non-secret account representation.

    Raises:
        HTTPException: When the account is missing, conflicted, or credentials are unavailable.
    """
    try:
        return await account_service.update_account(account_id, request)
    except AccountNotFoundError as error:
        raise HTTPException(
            status_code=status.HTTP_404_NOT_FOUND,
            detail=ACCOUNT_NOT_FOUND_ERROR_MESSAGE,
        ) from error
    except AccountStateConflictError as error:
        raise HTTPException(
            status_code=status.HTTP_409_CONFLICT,
            detail=ACCOUNT_STATE_CONFLICT_ERROR_MESSAGE,
        ) from error
    except CredentialUnavailableError as error:
        raise HTTPException(
            status_code=status.HTTP_503_SERVICE_UNAVAILABLE,
            detail=CREDENTIAL_UNAVAILABLE_ERROR_MESSAGE,
        ) from error
    except _PROTOCOL_ERROR_TYPES as error:
        _raise_protocol_error(error)


@router.post("/{account_id}/verify", response_model=AccountResponse, response_model_by_alias=True)
async def verify_account(
    account_id: int,
    account_service: AccountService = Depends(get_account_service),
) -> AccountResponse:
    """Manually verify one stored cookie with the Goodfish protocol.

    Args:
        account_id: Local database identifier.
        account_service: Service providing account orchestration.

    Returns:
        The updated account representation with its latest validation state.

    Raises:
        HTTPException: When the account is missing, conflicted, undecryptable, or rejected by Goodfish.
    """
    try:
        return await account_service.verify_account(account_id)
    except AccountNotFoundError as error:
        raise HTTPException(
            status_code=status.HTTP_404_NOT_FOUND,
            detail=ACCOUNT_NOT_FOUND_ERROR_MESSAGE,
        ) from error
    except AccountStateConflictError as error:
        raise HTTPException(
            status_code=status.HTTP_409_CONFLICT,
            detail=ACCOUNT_STATE_CONFLICT_ERROR_MESSAGE,
        ) from error
    except CredentialUnavailableError as error:
        raise HTTPException(
            status_code=status.HTTP_503_SERVICE_UNAVAILABLE,
            detail=CREDENTIAL_UNAVAILABLE_ERROR_MESSAGE,
        ) from error
    except _PROTOCOL_ERROR_TYPES as error:
        _raise_protocol_error(error)


@router.delete("/{account_id}", response_model=AccountResponse, response_model_by_alias=True)
async def delete_account(
    account_id: int,
    account_service: AccountService = Depends(get_account_service),
) -> AccountResponse:
    """Delete one idle local account.

    Args:
        account_id: Local database identifier.
        account_service: Service providing account orchestration.

    Returns:
        The deleted non-secret account representation.

    Raises:
        HTTPException: When the account is missing or not idle.
    """
    try:
        return await account_service.delete_account(account_id)
    except AccountNotFoundError as error:
        raise HTTPException(
            status_code=status.HTTP_404_NOT_FOUND,
            detail=ACCOUNT_NOT_FOUND_ERROR_MESSAGE,
        ) from error
    except AccountStateConflictError as error:
        raise HTTPException(
            status_code=status.HTTP_409_CONFLICT,
            detail=ACCOUNT_STATE_CONFLICT_ERROR_MESSAGE,
        ) from error


def _raise_protocol_error(error: Exception) -> NoReturn:
    """Translate one protocol domain error into its stable HTTP response.

    Args:
        error: Protocol error raised by the account service.

    Raises:
        HTTPException: With the status and non-secret detail configured for the error type.
    """
    for error_type, response_status, detail in _PROTOCOL_ERROR_RESPONSES:
        if isinstance(error, error_type):
            raise HTTPException(status_code=response_status, detail=detail) from error
    raise HTTPException(
        status_code=status.HTTP_502_BAD_GATEWAY,
        detail=XIANYU_PROTOCOL_ERROR_MESSAGE,
    ) from error
