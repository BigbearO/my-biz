"""Master-key lifecycle management for local cookie encryption."""

import asyncio
import os
import secrets
from pathlib import Path

from xianyu_client.core.constants import (
    MASTER_KEY_INITIALIZATION_ERROR_MESSAGE,
    MASTER_KEY_DECRYPTION_ERROR_MESSAGE,
    MASTER_KEY_FILE_MODE,
    MASTER_KEY_LENGTH_BYTES,
)
from xianyu_client.core.crypto import CookieCryptoError, validate_master_key
from xianyu_client.core.dpapi import DpapiError, WindowsDpapiKeyProtector


class MasterKeyDecryptionError(Exception):
    """Raised when an existing master-key file cannot be restored."""


class MasterKeyInitializationError(Exception):
    """Raised when a new master key cannot be protected for storage."""


class MasterKeyManager:
    """Loads or atomically creates the DPAPI-protected local master key."""

    def __init__(self, key_path: Path, protector=None) -> None:
        """Create a manager for one application master-key file.

        Args:
            key_path: Absolute path to `master.key`.
            protector: Optional byte protector used by tests or alternate platforms.
        """
        self._key_path = key_path
        self._protector = protector or WindowsDpapiKeyProtector()
        self._lock = asyncio.Lock()
        self._key: bytes | None = None

    async def load_or_create(self) -> bytes:
        """Return the cached master key, creating it once when necessary.

        Returns:
            The raw 32-byte application master key.

        Raises:
            MasterKeyDecryptionError: If an existing key cannot be restored.
            MasterKeyInitializationError: If a new key cannot be protected.
        """
        async with self._lock:
            if self._key is not None:
                return self._key
            self._key = await asyncio.to_thread(self._load_or_create_sync)
            return self._key

    def _load_or_create_sync(self) -> bytes:
        """Synchronously load an existing key or create a protected replacement.

        Returns:
            The raw application master key.
        """
        if self._key_path.exists():
            return self._load_existing_key_sync()
        return self._create_new_key_sync()

    def _load_existing_key_sync(self) -> bytes:
        """Unprotect and validate an existing master-key file without rewriting it.

        Returns:
            The restored raw application master key.

        Raises:
            MasterKeyDecryptionError: If protected content or key length is invalid.
        """
        protected_key = self._key_path.read_bytes()
        try:
            key = self._protector.unprotect(protected_key)
            validate_master_key(key)
            return key
        except (DpapiError, CookieCryptoError, ValueError) as error:
            raise MasterKeyDecryptionError(MASTER_KEY_DECRYPTION_ERROR_MESSAGE) from error

    def _create_new_key_sync(self) -> bytes:
        """Generate, protect, and atomically persist a new master key.

        Returns:
            The newly generated raw application master key.

        Raises:
            MasterKeyInitializationError: If the protector rejects the new key.
        """
        key = secrets.token_bytes(MASTER_KEY_LENGTH_BYTES)
        try:
            protected_key = self._protector.protect(key)
        except DpapiError as error:
            raise MasterKeyInitializationError(MASTER_KEY_INITIALIZATION_ERROR_MESSAGE) from error
        self._key_path.parent.mkdir(parents=True, exist_ok=True)
        temporary_path = self._key_path.with_name(f".{self._key_path.name}.{os.getpid()}.tmp")
        descriptor = os.open(temporary_path, os.O_WRONLY | os.O_CREAT | os.O_EXCL, MASTER_KEY_FILE_MODE)
        try:
            with os.fdopen(descriptor, "wb") as key_file:
                key_file.write(protected_key)
                key_file.flush()
                os.fsync(key_file.fileno())
            os.chmod(temporary_path, MASTER_KEY_FILE_MODE)
            os.replace(temporary_path, self._key_path)
        finally:
            if temporary_path.exists():
                temporary_path.unlink()
        return key
