"""FastAPI dependency wiring and local request authentication."""

import secrets

from fastapi import Request, status

from xianyu_client.api.local_errors import LocalApiError
from xianyu_client.core.constants import (
    ERROR_CODE_LOCAL_TOKEN_INVALID,
    LOCAL_TOKEN_ERROR,
    LOCAL_TOKEN_HEADER,
)


def get_system_service(request: Request):
    """Return the system service attached to the current application."""
    return request.app.state.system_service


def get_account_service(request: Request):
    """Return the account service attached to the current application.

    Args:
        request: Active FastAPI request carrying application state.

    Returns:
        The account service used by account routes.
    """
    return request.app.state.account_service


def get_item_sync_service(request: Request):
    """Return the product synchronisation service attached to the current application.

    Args:
        request: Active FastAPI request carrying application state.

    Returns:
        The synchronisation service used by product sync routes.
    """
    return request.app.state.item_sync_service


def get_auth_service(request: Request):
    """Return the login state service attached to the current application.

    Args:
        request: Active FastAPI request carrying application state.

    Returns:
        The authorization service used by system routes.
    """
    return request.app.state.auth_service


def validate_local_token(request: Request) -> None:
    """Reject requests unless they carry the process-local random token.

    Raises:
        LocalApiError: When the token header is absent or does not match.
    """
    expected_token = request.app.state.settings.local_token
    actual_token = request.headers.get(LOCAL_TOKEN_HEADER, "")
    if not expected_token or not secrets.compare_digest(actual_token, expected_token):
        raise LocalApiError(
            status.HTTP_401_UNAUTHORIZED,
            ERROR_CODE_LOCAL_TOKEN_INVALID,
            LOCAL_TOKEN_ERROR,
        )

