"""Domain errors shared by local client business layers."""

from xianyu_client.core.constants import (
    ACCOUNT_DUPLICATE_ERROR_MESSAGE,
    ACCOUNT_NOT_FOUND_ERROR_MESSAGE,
    ACCOUNT_STATE_CONFLICT_ERROR_MESSAGE,
    AUTH_TOKEN_DECRYPTION_ERROR_MESSAGE,
    CLOUD_FORBIDDEN_ERROR_MESSAGE,
    CLOUD_ITEM_SYNC_FAILED_ERROR_MESSAGE,
    CLOUD_REPORT_FAILED_ERROR_MESSAGE,
    CLOUD_UNAVAILABLE_ERROR_MESSAGE,
    CLOUD_UNAUTHORIZED_ERROR_MESSAGE,
    CREDENTIAL_UNAVAILABLE_ERROR_MESSAGE,
    ERROR_DETAIL_EMPTY,
    INVALID_XIANYU_COOKIE_ERROR_MESSAGE,
    ITEM_SYNC_IN_PROGRESS_ERROR_MESSAGE,
    NOT_LOGGED_IN_ERROR_MESSAGE,
    XIANYU_COOKIE_MISMATCH_ERROR_MESSAGE,
    XIANYU_NETWORK_ERROR_MESSAGE,
    XIANYU_PROTOCOL_ERROR_MESSAGE,
    XIANYU_RISK_CONTROL_ERROR_MESSAGE,
)


class AccountNotFoundError(Exception):
    """Raised when an account ID does not identify a local account."""

    def __init__(self) -> None:
        """Create the error with its stable external message."""
        super().__init__(ACCOUNT_NOT_FOUND_ERROR_MESSAGE)


class DuplicateGoodfishIdError(Exception):
    """Raised when a Goodfish ID is already owned by another local account."""

    def __init__(self) -> None:
        """Create the error with its stable external message."""
        super().__init__(ACCOUNT_DUPLICATE_ERROR_MESSAGE)


class AccountStateConflictError(Exception):
    """Raised when account listening state blocks a persisted change."""

    def __init__(self) -> None:
        """Create the error with its stable external message."""
        super().__init__(ACCOUNT_STATE_CONFLICT_ERROR_MESSAGE)


class CredentialUnavailableError(Exception):
    """Raised when cookies cannot be encrypted with the local master key."""

    def __init__(self) -> None:
        """Create the error without embedding credential material."""
        super().__init__(CREDENTIAL_UNAVAILABLE_ERROR_MESSAGE)


class InvalidXianyuCookieError(Exception):
    """Raised when a Goodfish cookie cannot pass protocol login validation."""

    def __init__(self) -> None:
        """Create the error without retaining any Cookie material."""
        super().__init__(INVALID_XIANYU_COOKIE_ERROR_MESSAGE)


class XianyuCookieMismatchError(Exception):
    """Raised when a replacement cookie belongs to another Goodfish account."""

    def __init__(self) -> None:
        """Create the error without retaining any Cookie material."""
        super().__init__(XIANYU_COOKIE_MISMATCH_ERROR_MESSAGE)


class XianyuNetworkError(Exception):
    """Raised when Goodfish protocol transport is unavailable."""

    def __init__(self) -> None:
        """Create the error without exposing request details."""
        super().__init__(XIANYU_NETWORK_ERROR_MESSAGE)


class XianyuProtocolError(Exception):
    """Raised when Goodfish returns an unusable protocol response."""

    def __init__(self, detail: str = ERROR_DETAIL_EMPTY) -> None:
        """Create the error with an optional non-secret diagnostic detail.

        Args:
            detail: Technical hint kept for the local log only; never returned to
                the renderer, because the external message stays stable.
        """
        super().__init__(XIANYU_PROTOCOL_ERROR_MESSAGE)
        self.detail = detail


class XianyuRiskControlError(Exception):
    """Raised when Goodfish requires manual risk-control verification."""

    def __init__(self) -> None:
        """Create the error without retaining verification links."""
        super().__init__(XIANYU_RISK_CONTROL_ERROR_MESSAGE)


class NotLoggedInError(Exception):
    """Raised when the local service has no usable cloud login token."""

    def __init__(self) -> None:
        """Create the error with its stable external message."""
        super().__init__(NOT_LOGGED_IN_ERROR_MESSAGE)


class CloudUnauthorizedError(Exception):
    """Raised when the cloud gateway rejects the stored token with HTTP 401."""

    def __init__(self) -> None:
        """Create the error with its stable external message."""
        super().__init__(CLOUD_UNAUTHORIZED_ERROR_MESSAGE)


class CloudForbiddenError(Exception):
    """Raised when the cloud reports that the account authorization expired."""

    def __init__(self) -> None:
        """Create the error with its stable external message."""
        super().__init__(CLOUD_FORBIDDEN_ERROR_MESSAGE)


class CloudReportError(Exception):
    """Raised when an account cannot be reported to the cloud."""

    def __init__(self) -> None:
        """Create the error with its stable external message."""
        super().__init__(CLOUD_REPORT_FAILED_ERROR_MESSAGE)


class CloudUnavailableError(Exception):
    """Raised when the cloud stays unreachable after the retry budget."""

    def __init__(self) -> None:
        """Create the error with its stable external message."""
        super().__init__(CLOUD_UNAVAILABLE_ERROR_MESSAGE)


class AuthTokenUnavailableError(Exception):
    """Raised when the stored login token cannot be encrypted or decrypted."""

    def __init__(self, message: str = AUTH_TOKEN_DECRYPTION_ERROR_MESSAGE) -> None:
        """Create the error with a non-secret message.

        Args:
            message: Stable external message describing the failure.
        """
        super().__init__(message)


class CloudItemSyncError(Exception):
    """Raised when the cloud refuses or fails a product synchronisation."""

    def __init__(self, message: str = CLOUD_ITEM_SYNC_FAILED_ERROR_MESSAGE) -> None:
        """Create the error with a non-secret message.

        Args:
            message: Stable external message describing the rejection.
        """
        super().__init__(message)


class ItemSyncInProgressError(Exception):
    """Raised when a product synchronisation is already running for one account."""

    def __init__(self) -> None:
        """Create the error with its stable external message."""
        super().__init__(ITEM_SYNC_IN_PROGRESS_ERROR_MESSAGE)
