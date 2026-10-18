"""AES-256-GCM primitives for local cookie encryption."""

import base64
import binascii
import os

from cryptography.exceptions import InvalidTag
from cryptography.hazmat.primitives.ciphers.aead import AESGCM

from xianyu_client.core.constants import (
    COOKIE_ASSOCIATED_DATA,
    COOKIE_AUTHENTICATION_ERROR_MESSAGE,
    COOKIE_DECODE_ERROR_MESSAGE,
    COOKIE_EMPTY_DECRYPTION_ERROR_MESSAGE,
    COOKIE_EMPTY_ENCRYPTION_ERROR_MESSAGE,
    COOKIE_DECRYPTION_ERROR_MESSAGE,
    COOKIE_ENCRYPTION_ERROR_MESSAGE,
    COOKIE_EMPTY_VALUE,
    COOKIE_NONCE_LENGTH_BYTES,
    COOKIE_TRUNCATED_ERROR_MESSAGE,
    MASTER_KEY_LENGTH_BYTES,
    MASTER_KEY_LENGTH_ERROR_MESSAGE,
)


class CookieCryptoError(Exception):
    """Base exception for local cookie cryptography failures."""


class InvalidCookieKeyError(CookieCryptoError):
    """Raised when the supplied master key does not have the required length."""


class CookieEncryptionError(CookieCryptoError):
    """Raised when a cookie cannot be encrypted."""


class CookieDecryptionError(CookieCryptoError):
    """Raised when stored cookie ciphertext cannot be authenticated or decoded."""


def validate_master_key(key: bytes) -> None:
    """Validate that the master key can be used by AES-256-GCM.

    Args:
        key: Raw master key bytes.

    Raises:
        InvalidCookieKeyError: If the key is absent or has the wrong length.
    """
    if len(key) != MASTER_KEY_LENGTH_BYTES:
        raise InvalidCookieKeyError(MASTER_KEY_LENGTH_ERROR_MESSAGE)


def encrypt_cookie(master_key: bytes, cookie: str) -> str:
    """Encrypt one cookie into the local base64 storage format.

    Args:
        master_key: DPAPI-protected key after unwrapping.
        cookie: Plaintext cookie value.

    Returns:
        Base64 text containing `nonce + ciphertext + tag`.

    Raises:
        InvalidCookieKeyError: If the master key length is invalid.
        CookieEncryptionError: If the cookie is empty or encryption fails.
    """
    validate_master_key(master_key)
    if cookie == COOKIE_EMPTY_VALUE:
        raise CookieEncryptionError(COOKIE_EMPTY_ENCRYPTION_ERROR_MESSAGE)
    nonce = os.urandom(COOKIE_NONCE_LENGTH_BYTES)
    try:
        ciphertext = AESGCM(master_key).encrypt(
            nonce,
            cookie.encode("utf-8"),
            COOKIE_ASSOCIATED_DATA,
        )
    except Exception as error:
        raise CookieEncryptionError(COOKIE_ENCRYPTION_ERROR_MESSAGE) from error
    return base64.b64encode(nonce + ciphertext).decode("ascii")


def decrypt_cookie(master_key: bytes, encrypted_cookie: str) -> str:
    """Decrypt and authenticate one locally stored cookie.

    Args:
        master_key: DPAPI-protected key after unwrapping.
        encrypted_cookie: Base64 `nonce + ciphertext + tag`.

    Returns:
        The plaintext cookie.

    Raises:
        InvalidCookieKeyError: If the master key length is invalid.
        CookieDecryptionError: If ciphertext is malformed, tampered, or undecodable.
    """
    validate_master_key(master_key)
    if encrypted_cookie == COOKIE_EMPTY_VALUE:
        raise CookieDecryptionError(COOKIE_EMPTY_DECRYPTION_ERROR_MESSAGE)
    try:
        payload = base64.b64decode(encrypted_cookie.encode("ascii"), validate=True)
    except (binascii.Error, UnicodeError, ValueError) as error:
        raise CookieDecryptionError(COOKIE_DECODE_ERROR_MESSAGE) from error
    if len(payload) <= COOKIE_NONCE_LENGTH_BYTES:
        raise CookieDecryptionError(COOKIE_TRUNCATED_ERROR_MESSAGE)

    nonce = payload[:COOKIE_NONCE_LENGTH_BYTES]
    ciphertext = payload[COOKIE_NONCE_LENGTH_BYTES:]
    try:
        plaintext = AESGCM(master_key).decrypt(nonce, ciphertext, COOKIE_ASSOCIATED_DATA)
        return plaintext.decode("utf-8")
    except InvalidTag as error:
        raise CookieDecryptionError(COOKIE_AUTHENTICATION_ERROR_MESSAGE) from error
    except Exception as error:
        raise CookieDecryptionError(COOKIE_DECRYPTION_ERROR_MESSAGE) from error
