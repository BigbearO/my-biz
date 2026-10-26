"""Local HTTP routes for Goodfish account management."""

from typing import NoReturn

from fastapi import APIRouter, Depends, status
from loguru import logger

from xianyu_client.api.dependencies import get_account_service, validate_local_token
from xianyu_client.api.local_errors import LocalApiError
from xianyu_client.core.constants import (
    ACCOUNT_DUPLICATE_ERROR_MESSAGE,
    ACCOUNT_NOT_FOUND_ERROR_MESSAGE,
    ACCOUNT_STATE_CONFLICT_ERROR_MESSAGE,
    CREDENTIAL_UNAVAILABLE_ERROR_MESSAGE,
    ERROR_CODE_ACCOUNT_DUPLICATE,
    ERROR_CODE_ACCOUNT_NOT_FOUND,
    ERROR_CODE_ACCOUNT_STATE_CONFLICT,
    ERROR_CODE_CLOUD_FORBIDDEN,
    ERROR_CODE_CLOUD_REPORT_FAILED,
    ERROR_CODE_CREDENTIAL_UNAVAILABLE,
    ERROR_CODE_NOT_LOGGED_IN,
    ERROR_CODE_XIANYU_COOKIE_INVALID,
    ERROR_CODE_XIANYU_COOKIE_MISMATCH,
    ERROR_CODE_XIANYU_NETWORK,
    ERROR_CODE_XIANYU_PROTOCOL,
    ERROR_CODE_XIANYU_RISK_CONTROL,
    ERROR_DETAIL_EMPTY,
    INVALID_XIANYU_COOKIE_ERROR_MESSAGE,
    PROTOCOL_ERROR_LOG_MESSAGE,
    XIANYU_COOKIE_MISMATCH_ERROR_MESSAGE,
    XIANYU_NETWORK_ERROR_MESSAGE,
    XIANYU_PROTOCOL_ERROR_MESSAGE,
    XIANYU_RISK_CONTROL_ERROR_MESSAGE,
)
from xianyu_client.core.errors import (
    AccountNotFoundError,
    AccountStateConflictError,
    AuthTokenUnavailableError,
    CloudForbiddenError,
    CloudReportError,
    CloudUnauthorizedError,
    CredentialUnavailableError,
    DuplicateGoodfishIdError,
    InvalidXianyuCookieError,
    NotLoggedInError,
    XianyuCookieMismatchError,
    XianyuNetworkError,
    XianyuProtocolError,
    XianyuRiskControlError,
)
from xianyu_client.schemas.account_schemas import (
    AccountCreateRequest,
    AccountDetailResponse,
    AccountResponse,
    AccountUpdateRequest,
)
from xianyu_client.services.account_service import AccountService

router = APIRouter(prefix="/api/accounts", dependencies=[Depends(validate_local_token)])

_CREATE_ACCOUNT_OPERATION = "create_account"
_UPDATE_ACCOUNT_OPERATION = "update_account"
_VERIFY_ACCOUNT_OPERATION = "verify_account"

_PROTOCOL_ERROR_RESPONSES = (
    (
        InvalidXianyuCookieError,
        status.HTTP_400_BAD_REQUEST,
        ERROR_CODE_XIANYU_COOKIE_INVALID,
        INVALID_XIANYU_COOKIE_ERROR_MESSAGE,
    ),
    (
        XianyuCookieMismatchError,
        status.HTTP_409_CONFLICT,
        ERROR_CODE_XIANYU_COOKIE_MISMATCH,
        XIANYU_COOKIE_MISMATCH_ERROR_MESSAGE,
    ),
    (
        XianyuRiskControlError,
        status.HTTP_409_CONFLICT,
        ERROR_CODE_XIANYU_RISK_CONTROL,
        XIANYU_RISK_CONTROL_ERROR_MESSAGE,
    ),
    (
        XianyuNetworkError,
        status.HTTP_503_SERVICE_UNAVAILABLE,
        ERROR_CODE_XIANYU_NETWORK,
        XIANYU_NETWORK_ERROR_MESSAGE,
    ),
    (
        XianyuProtocolError,
        status.HTTP_502_BAD_GATEWAY,
        ERROR_CODE_XIANYU_PROTOCOL,
        XIANYU_PROTOCOL_ERROR_MESSAGE,
    ),
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
        LocalApiError: When the Goodfish ID is duplicate, credentials are unavailable,
            the caller is not logged in, or the cloud rejects the account report.
    """
    try:
        return await account_service.create_account(request)
    except DuplicateGoodfishIdError as error:
        raise LocalApiError(
            status.HTTP_409_CONFLICT,
            ERROR_CODE_ACCOUNT_DUPLICATE,
            ACCOUNT_DUPLICATE_ERROR_MESSAGE,
        ) from error
    except (NotLoggedInError, CloudUnauthorizedError) as error:
        raise LocalApiError(
            status.HTTP_401_UNAUTHORIZED,
            ERROR_CODE_NOT_LOGGED_IN,
            str(error),
        ) from error
    except CloudForbiddenError as error:
        raise LocalApiError(
            status.HTTP_403_FORBIDDEN,
            ERROR_CODE_CLOUD_FORBIDDEN,
            str(error),
        ) from error
    except CloudReportError as error:
        raise LocalApiError(
            status.HTTP_502_BAD_GATEWAY,
            ERROR_CODE_CLOUD_REPORT_FAILED,
            str(error),
        ) from error
    except (CredentialUnavailableError, AuthTokenUnavailableError) as error:
        raise LocalApiError(
            status.HTTP_503_SERVICE_UNAVAILABLE,
            ERROR_CODE_CREDENTIAL_UNAVAILABLE,
            CREDENTIAL_UNAVAILABLE_ERROR_MESSAGE,
        ) from error
    except _PROTOCOL_ERROR_TYPES as error:
        _raise_protocol_error(error, _CREATE_ACCOUNT_OPERATION)


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
        LocalApiError: When the account is missing, conflicted, or credentials are unavailable.
    """
    try:
        return await account_service.update_account(account_id, request)
    except AccountNotFoundError as error:
        raise LocalApiError(
            status.HTTP_404_NOT_FOUND,
            ERROR_CODE_ACCOUNT_NOT_FOUND,
            ACCOUNT_NOT_FOUND_ERROR_MESSAGE,
        ) from error
    except AccountStateConflictError as error:
        raise LocalApiError(
            status.HTTP_409_CONFLICT,
            ERROR_CODE_ACCOUNT_STATE_CONFLICT,
            ACCOUNT_STATE_CONFLICT_ERROR_MESSAGE,
        ) from error
    except CredentialUnavailableError as error:
        raise LocalApiError(
            status.HTTP_503_SERVICE_UNAVAILABLE,
            ERROR_CODE_CREDENTIAL_UNAVAILABLE,
            CREDENTIAL_UNAVAILABLE_ERROR_MESSAGE,
        ) from error
    except _PROTOCOL_ERROR_TYPES as error:
        _raise_protocol_error(error, _UPDATE_ACCOUNT_OPERATION)


@router.get("/{account_id}/detail", response_model=AccountDetailResponse, response_model_by_alias=True)
async def get_account_detail(
    account_id: int,
    account_service: AccountService = Depends(get_account_service),
) -> AccountDetailResponse:
    """Return one account including its decrypted cookie for the detail dialog.

    Args:
        account_id: Local database identifier.
        account_service: Service providing account orchestration.

    Returns:
        The account representation with the plaintext cookie.

    Raises:
        LocalApiError: When the account is missing or the cookie cannot be decrypted.
    """
    try:
        return await account_service.get_account_detail(account_id)
    except AccountNotFoundError as error:
        raise LocalApiError(
            status.HTTP_404_NOT_FOUND,
            ERROR_CODE_ACCOUNT_NOT_FOUND,
            ACCOUNT_NOT_FOUND_ERROR_MESSAGE,
        ) from error
    except CredentialUnavailableError as error:
        raise LocalApiError(
            status.HTTP_503_SERVICE_UNAVAILABLE,
            ERROR_CODE_CREDENTIAL_UNAVAILABLE,
            CREDENTIAL_UNAVAILABLE_ERROR_MESSAGE,
        ) from error


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
        LocalApiError: When the account is missing, conflicted, undecryptable, or rejected by Goodfish.
    """
    try:
        return await account_service.verify_account(account_id)
    except AccountNotFoundError as error:
        raise LocalApiError(
            status.HTTP_404_NOT_FOUND,
            ERROR_CODE_ACCOUNT_NOT_FOUND,
            ACCOUNT_NOT_FOUND_ERROR_MESSAGE,
        ) from error
    except AccountStateConflictError as error:
        raise LocalApiError(
            status.HTTP_409_CONFLICT,
            ERROR_CODE_ACCOUNT_STATE_CONFLICT,
            ACCOUNT_STATE_CONFLICT_ERROR_MESSAGE,
        ) from error
    except CredentialUnavailableError as error:
        raise LocalApiError(
            status.HTTP_503_SERVICE_UNAVAILABLE,
            ERROR_CODE_CREDENTIAL_UNAVAILABLE,
            CREDENTIAL_UNAVAILABLE_ERROR_MESSAGE,
        ) from error
    except _PROTOCOL_ERROR_TYPES as error:
        _raise_protocol_error(error, _VERIFY_ACCOUNT_OPERATION)


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
        LocalApiError: When the account is missing or not idle.
    """
    try:
        return await account_service.delete_account(account_id)
    except AccountNotFoundError as error:
        raise LocalApiError(
            status.HTTP_404_NOT_FOUND,
            ERROR_CODE_ACCOUNT_NOT_FOUND,
            ACCOUNT_NOT_FOUND_ERROR_MESSAGE,
        ) from error
    except AccountStateConflictError as error:
        raise LocalApiError(
            status.HTTP_409_CONFLICT,
            ERROR_CODE_ACCOUNT_STATE_CONFLICT,
            ACCOUNT_STATE_CONFLICT_ERROR_MESSAGE,
        ) from error


def _raise_protocol_error(error: Exception, operation: str) -> NoReturn:
    """Translate one protocol domain error into its stable HTTP response.

    Args:
        error: Protocol error raised by the account service.
        operation: Route name recorded in the diagnostic log.

    Raises:
        LocalApiError: With the status, code, and detail configured for the error type.
    """
    logger.warning(
        PROTOCOL_ERROR_LOG_MESSAGE,
        operation,
        type(error).__name__,
        getattr(error, "detail", ERROR_DETAIL_EMPTY),
    )
    for error_type, response_status, code, detail in _PROTOCOL_ERROR_RESPONSES:
        if isinstance(error, error_type):
            raise LocalApiError(response_status, code, detail) from error
    raise LocalApiError(
        status.HTTP_502_BAD_GATEWAY,
        ERROR_CODE_XIANYU_PROTOCOL,
        XIANYU_PROTOCOL_ERROR_MESSAGE,
    ) from error
