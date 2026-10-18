"""Asynchronous cookie encryption operations backed by the local master key."""

import asyncio

from xianyu_client.core.crypto import (
    decrypt_cookie,
    encrypt_cookie,
)
from xianyu_client.managers.master_key_manager import MasterKeyManager


class CookieCredentialManager:
    """Encrypts and decrypts cookies without retaining plaintext in memory."""

    def __init__(self, master_key_manager: MasterKeyManager) -> None:
        """Create a cookie manager bound to the application master key.

        Args:
            master_key_manager: Manager that owns master-key lifecycle.
        """
        self._master_key_manager = master_key_manager

    async def load_master_key(self) -> bytes:
        """Load the master key through the locked manager lifecycle."""
        return await self._master_key_manager.load_or_create()

    async def encrypt(self, cookie: str) -> str:
        """Encrypt a plaintext cookie off the event loop.

        Args:
            cookie: Cookie value that exists only for this call.

        Returns:
            Base64 ciphertext suitable for `goodfish_account.cookie`.
        """
        master_key = await self.load_master_key()
        return await asyncio.to_thread(encrypt_cookie, master_key, cookie)

    async def decrypt(self, encrypted_cookie: str) -> str:
        """Decrypt one stored cookie off the event loop.

        Args:
            encrypted_cookie: Base64 value from `goodfish_account.cookie`.

        Returns:
            The plaintext cookie for protocol-layer use.
        """
        master_key = await self.load_master_key()
        return await asyncio.to_thread(decrypt_cookie, master_key, encrypted_cookie)
