"""Local HTTP routes for product synchronisation."""

from typing import NoReturn

from fastapi import APIRouter, Depends, status
from loguru import logger

from xianyu_client.api.dependencies import get_item_sync_service, validate_local_token
from xianyu_client.api.local_errors import LocalApiError
from xianyu_client.core.constants import (
    ACCOUNT_NOT_FOUND_ERROR_MESSAGE,
    CREDENTIAL_UNAVAILABLE_ERROR_MESSAGE,
    ERROR_CODE_ACCOUNT_NOT_FOUND,
    ERROR_CODE_CLOUD_FORBIDDEN,
    ERROR_CODE_CLOUD_ITEM_SYNC_FAILED,
    ERROR_CODE_CREDENTIAL_UNAVAILABLE,
    ERROR_CODE_ITEM_SYNC_IN_PROGRESS,
    ERROR_CODE_NOT_LOGGED_IN,
    ERROR_CODE_XIANYU_COOKIE_INVALID,
    ERROR_CODE_XIANYU_NETWORK,
    ERROR_CODE_XIANYU_PROTOCOL,
    ERROR_CODE_XIANYU_RISK_CONTROL,
    ERROR_DETAIL_EMPTY,
    INVALID_XIANYU_COOKIE_ERROR_MESSAGE,
    ITEM_SYNC_IN_PROGRESS_ERROR_MESSAGE,
    PROTOCOL_ERROR_LOG_MESSAGE,
    XIANYU_NETWORK_ERROR_MESSAGE,
    XIANYU_PROTOCOL_ERROR_MESSAGE,
    XIANYU_RISK_CONTROL_ERROR_MESSAGE,
)
from xianyu_client.core.errors import (
    AccountNotFoundError,
    AuthTokenUnavailableError,
    CloudForbiddenError,
    CloudItemSyncError,
    CloudUnauthorizedError,
    CredentialUnavailableError,
    ItemSyncInProgressError,
    InvalidXianyuCookieError,
    NotLoggedInError,
    XianyuNetworkError,
    XianyuProtocolError,
    XianyuRiskControlError,
)
from xianyu_client.schemas.item_schemas import ItemSyncStatusResponse
from xianyu_client.services.item_sync_service import ItemSyncService

router = APIRouter(prefix="/api/sync/items", dependencies=[Depends(validate_local_token)])

_START_ITEM_SYNC_OPERATION = "start_item_sync"

_PROTOCOL_ERROR_RESPONSES = (
    (
        InvalidXianyuCookieError,
        status.HTTP_400_BAD_REQUEST,
        ERROR_CODE_XIANYU_COOKIE_INVALID,
        INVALID_XIANYU_COOKIE_ERROR_MESSAGE,
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


@router.post(
    "/{account_id}",
    response_model=ItemSyncStatusResponse,
    response_model_by_alias=True,
    status_code=status.HTTP_202_ACCEPTED,
)
async def start_item_sync(
    account_id: int,
    item_sync_service: ItemSyncService = Depends(get_item_sync_service),
) -> ItemSyncStatusResponse:
    """Queue one product synchronisation for an account.

    Args:
        account_id: Local database identifier.
        item_sync_service: Service orchestrating the synchronisation.

    Returns:
        The initial `running` state; the renderer polls the status route afterwards.

    Raises:
        LocalApiError: When the account is missing, already syncing, undecryptable,
            not logged in, or rejected by Goodfish.
    """
    try:
        return await item_sync_service.start(account_id)
    except AccountNotFoundError as error:
        raise LocalApiError(
            status.HTTP_404_NOT_FOUND,
            ERROR_CODE_ACCOUNT_NOT_FOUND,
            ACCOUNT_NOT_FOUND_ERROR_MESSAGE,
        ) from error
    except ItemSyncInProgressError as error:
        raise LocalApiError(
            status.HTTP_409_CONFLICT,
            ERROR_CODE_ITEM_SYNC_IN_PROGRESS,
            ITEM_SYNC_IN_PROGRESS_ERROR_MESSAGE,
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
    except CloudItemSyncError as error:
        raise LocalApiError(
            status.HTTP_502_BAD_GATEWAY,
            ERROR_CODE_CLOUD_ITEM_SYNC_FAILED,
            str(error),
        ) from error
    except (CredentialUnavailableError, AuthTokenUnavailableError) as error:
        raise LocalApiError(
            status.HTTP_503_SERVICE_UNAVAILABLE,
            ERROR_CODE_CREDENTIAL_UNAVAILABLE,
            CREDENTIAL_UNAVAILABLE_ERROR_MESSAGE,
        ) from error
    except _PROTOCOL_ERROR_TYPES as error:
        _raise_protocol_error(error, _START_ITEM_SYNC_OPERATION)


@router.get(
    "/{account_id}",
    response_model=ItemSyncStatusResponse,
    response_model_by_alias=True,
)
async def get_item_sync_status(
    account_id: int,
    item_sync_service: ItemSyncService = Depends(get_item_sync_service),
) -> ItemSyncStatusResponse:
    """Return the latest synchronisation state of one account.

    Args:
        account_id: Local database identifier.
        item_sync_service: Service holding the in-memory states.

    Returns:
        The stored state, or an idle state when this account never synced.
    """
    return await item_sync_service.get_status(account_id)


def _raise_protocol_error(error: Exception, operation: str) -> NoReturn:
    """Translate one protocol domain error into its stable HTTP response.

    Args:
        error: Protocol error surfaced by the synchronisation service.
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
