"""Domain errors shared by local client business layers."""

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

    def __init__(self) -> None:
        """Create the error without exposing response details."""
        super().__init__(XIANYU_PROTOCOL_ERROR_MESSAGE)


class XianyuRiskControlError(Exception):
    """Raised when Goodfish requires manual risk-control verification."""

    def __init__(self) -> None:
        """Create the error without retaining verification links."""
        super().__init__(XIANYU_RISK_CONTROL_ERROR_MESSAGE)
