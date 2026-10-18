"""Windows DPAPI adapter for protecting the local master key."""

import ctypes
import sys
from ctypes import POINTER, Structure, wintypes

from xianyu_client.core.constants import (
    DPAPI_PROTECT_ERROR_MESSAGE,
    DPAPI_UI_FORBIDDEN_FLAG,
    DPAPI_UNPROTECT_ERROR_MESSAGE,
    DPAPI_UNSUPPORTED_PLATFORM_MESSAGE,
    WINDOWS_PLATFORM_NAME,
)


class DpapiError(Exception):
    """Raised when Windows DPAPI cannot protect or unprotect a key."""


class DATA_BLOB(Structure):
    """Windows DATA_BLOB structure used by CryptProtectData."""

    _fields_ = [
        ("cbData", wintypes.DWORD),
        ("pbData", POINTER(wintypes.BYTE)),
    ]


_crypt32 = ctypes.WinDLL("crypt32", use_last_error=True)
_crypt32.CryptProtectData.argtypes = [
    POINTER(DATA_BLOB),
    wintypes.LPCWSTR,
    POINTER(DATA_BLOB),
    ctypes.c_void_p,
    ctypes.c_void_p,
    wintypes.DWORD,
    POINTER(DATA_BLOB),
]
_crypt32.CryptProtectData.restype = wintypes.BOOL
_crypt32.CryptUnprotectData.argtypes = [
    POINTER(DATA_BLOB),
    POINTER(wintypes.LPCWSTR),
    POINTER(DATA_BLOB),
    ctypes.c_void_p,
    ctypes.c_void_p,
    wintypes.DWORD,
    POINTER(DATA_BLOB),
]
_crypt32.CryptUnprotectData.restype = wintypes.BOOL


def _require_windows() -> None:
    """Reject DPAPI calls on unsupported platforms.

    Raises:
        DpapiError: If the current operating system is not Windows.
    """
    if sys.platform != WINDOWS_PLATFORM_NAME:
        raise DpapiError(DPAPI_UNSUPPORTED_PLATFORM_MESSAGE)


def _new_blob(value: bytes) -> DATA_BLOB:
    """Create a DATA_BLOB that owns a copied byte buffer.

    Args:
        value: Bytes to expose to the Windows API.

    Returns:
        A DATA_BLOB pointing at a buffer retained until the call completes.
    """
    buffer = ctypes.create_string_buffer(value, len(value))
    return DATA_BLOB(len(value), ctypes.cast(buffer, POINTER(wintypes.BYTE)))


def _read_blob(blob: DATA_BLOB) -> bytes:
    """Read and copy bytes from a DATA_BLOB returned by Windows.

    Args:
        blob: Output blob allocated by the Windows API.

    Returns:
        An owned copy of the blob contents.
    """
    return ctypes.string_at(blob.pbData, blob.cbData)


class WindowsDpapiKeyProtector:
    """Protects master keys with the current Windows user's DPAPI scope."""

    def protect(self, key: bytes) -> bytes:
        """Protect a raw master key for the current user.

        Args:
            key: Raw 32-byte master key.

        Returns:
            The opaque DPAPI protected blob.

        Raises:
            DpapiError: If CryptProtectData fails.
        """
        _require_windows()
        input_blob = _new_blob(key)
        output_blob = DATA_BLOB()
        succeeded = _crypt32.CryptProtectData(
            ctypes.byref(input_blob),
            None,
            None,
            None,
            None,
            DPAPI_UI_FORBIDDEN_FLAG,
            ctypes.byref(output_blob),
        )
        if not succeeded:
            raise DpapiError(DPAPI_PROTECT_ERROR_MESSAGE.format(ctypes.get_last_error()))
        try:
            return _read_blob(output_blob)
        finally:
            ctypes.windll.kernel32.LocalFree(ctypes.cast(output_blob.pbData, ctypes.c_void_p))

    def unprotect(self, protected_key: bytes) -> bytes:
        """Unprotect a DPAPI master-key blob.

        Args:
            protected_key: Opaque bytes previously returned by `protect`.

        Returns:
            The raw master key.

        Raises:
            DpapiError: If CryptUnprotectData fails.
        """
        _require_windows()
        input_blob = _new_blob(protected_key)
        output_blob = DATA_BLOB()
        succeeded = _crypt32.CryptUnprotectData(
            ctypes.byref(input_blob),
            None,
            None,
            None,
            None,
            DPAPI_UI_FORBIDDEN_FLAG,
            ctypes.byref(output_blob),
        )
        if not succeeded:
            raise DpapiError(DPAPI_UNPROTECT_ERROR_MESSAGE.format(ctypes.get_last_error()))
        try:
            return _read_blob(output_blob)
        finally:
            ctypes.windll.kernel32.LocalFree(ctypes.cast(output_blob.pbData, ctypes.c_void_p))
