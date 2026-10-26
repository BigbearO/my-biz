"""Structured local API errors carrying a stable machine-readable code."""

from fastapi import HTTPException

from xianyu_client.core.constants import ERROR_CODE_DEFAULT


class LocalApiError(HTTPException):
    """HTTP error whose response body carries both a code and a safe message."""

    def __init__(self, status_code: int, code: str, message: str) -> None:
        """Create one structured local API error.

        Args:
            status_code: HTTP status returned to the Electron proxy.
            code: Stable error code mapped to a localized hint by the renderer.
            message: Non-secret message kept for logs and fallback display.
        """
        super().__init__(status_code=status_code, detail=message)
        self.code = code


def resolve_error_code(error: Exception) -> str:
    """Read the stable code of one HTTP error.

    Args:
        error: Any HTTP error raised inside the local API.

    Returns:
        The declared code, or the generic fallback for plain HTTP exceptions.
    """
    return getattr(error, "code", ERROR_CODE_DEFAULT)


__all__ = ["LocalApiError", "resolve_error_code"]
