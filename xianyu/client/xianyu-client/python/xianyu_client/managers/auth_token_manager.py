"""Encrypted storage for the cloud login token and its paired client id."""

import asyncio
import base64
import binascii
import os

from cryptography.exceptions import InvalidTag
from cryptography.hazmat.primitives.ciphers.aead import AESGCM

from xianyu_client.core.constants import (
    AUTH_TOKEN_ASSOCIATED_DATA,
    AUTH_TOKEN_DECRYPTION_ERROR_MESSAGE,
    AUTH_TOKEN_ENCRYPTION_ERROR_MESSAGE,
    CONFIG_AUTH_TOKEN,
    CONFIG_AUTH_TOKEN_DESCRIPTION,
    CONFIG_CLIENT_ID,
    CONFIG_CLIENT_ID_DESCRIPTION,
    CONFIG_EMPTY_VALUE,
    COOKIE_NONCE_LENGTH_BYTES,
)
from xianyu_client.core.crypto import CookieCryptoError, validate_master_key
from xianyu_client.core.errors import AuthTokenUnavailableError
from xianyu_client.managers.config_manager import ConfigManager
from xianyu_client.managers.master_key_manager import (
    MasterKeyDecryptionError,
    MasterKeyInitializationError,
    MasterKeyManager,
)


class AuthTokenManager:
    """Persists the login token pair as AES-256-GCM ciphertext."""

    def __init__(self, master_key_manager: MasterKeyManager, config_manager: ConfigManager) -> None:
        """Create a token manager bound to the master key and config rows.

        Args:
            master_key_manager: Manager that owns master-key lifecycle.
            config_manager: Manager that reads and writes config rows.
        """
        self._master_key_manager = master_key_manager
        self._config_manager = config_manager

    async def save(self, token: str, client_id: str) -> None:
        """Encrypt and persist the token pair.

        Args:
            token: Cloud access token issued by the management backend.
            client_id: Client identifier paired with the token.

        Raises:
            AuthTokenUnavailableError: When the token cannot be encrypted.
        """
        encrypted_token = await self._encrypt(token)
        encrypted_client_id = await self._encrypt(client_id)
        await self._config_manager.set_value(
            CONFIG_AUTH_TOKEN,
            encrypted_token,
            CONFIG_AUTH_TOKEN_DESCRIPTION,
        )
        await self._config_manager.set_value(
            CONFIG_CLIENT_ID,
            encrypted_client_id,
            CONFIG_CLIENT_ID_DESCRIPTION,
        )

    async def load(self) -> tuple[str, str]:
        """Decrypt and return the stored token pair.

        Returns:
            A `(token, client_id)` tuple; both values are empty when logged out.

        Raises:
            AuthTokenUnavailableError: When stored ciphertext cannot be decrypted.
        """
        encrypted_token = await self._config_manager.get_value(CONFIG_AUTH_TOKEN, CONFIG_EMPTY_VALUE)
        encrypted_client_id = await self._config_manager.get_value(CONFIG_CLIENT_ID, CONFIG_EMPTY_VALUE)
        if encrypted_token == CONFIG_EMPTY_VALUE:
            return CONFIG_EMPTY_VALUE, CONFIG_EMPTY_VALUE
        token = await self._decrypt(encrypted_token)
        if encrypted_client_id == CONFIG_EMPTY_VALUE:
            return token, CONFIG_EMPTY_VALUE
        client_id = await self._decrypt(encrypted_client_id)
        return token, client_id

    async def clear(self) -> None:
        """Remove the stored token pair without touching other config values."""
        await self._config_manager.set_value(
            CONFIG_AUTH_TOKEN,
            CONFIG_EMPTY_VALUE,
            CONFIG_AUTH_TOKEN_DESCRIPTION,
        )
        await self._config_manager.set_value(
            CONFIG_CLIENT_ID,
            CONFIG_EMPTY_VALUE,
            CONFIG_CLIENT_ID_DESCRIPTION,
        )

    async def _encrypt(self, plaintext: str) -> str:
        """Encrypt one secret value for config storage.

        Args:
            plaintext: Value that exists only for this call.

        Returns:
            Base64 `nonce + ciphertext + tag`.

        Raises:
            AuthTokenUnavailableError: When the key is unavailable or encryption fails.
        """
        if plaintext == CONFIG_EMPTY_VALUE:
            return CONFIG_EMPTY_VALUE
        try:
            master_key = await self._master_key_manager.load_or_create()
            validate_master_key(master_key)
        except (MasterKeyDecryptionError, MasterKeyInitializationError, CookieCryptoError) as error:
            raise AuthTokenUnavailableError(AUTH_TOKEN_ENCRYPTION_ERROR_MESSAGE) from error
        nonce = os.urandom(COOKIE_NONCE_LENGTH_BYTES)
        try:
            ciphertext = await asyncio.to_thread(
                AESGCM(master_key).encrypt,
                nonce,
                plaintext.encode("utf-8"),
                AUTH_TOKEN_ASSOCIATED_DATA,
            )
        except Exception as error:
            raise AuthTokenUnavailableError(AUTH_TOKEN_ENCRYPTION_ERROR_MESSAGE) from error
        return base64.b64encode(nonce + ciphertext).decode("ascii")

    async def _decrypt(self, ciphertext: str) -> str:
        """Decrypt one stored secret value.

        Args:
            ciphertext: Base64 `nonce + ciphertext + tag` from the config row.

        Returns:
            The plaintext token or client id.

        Raises:
            AuthTokenUnavailableError: When ciphertext is malformed or tampered.
        """
        try:
            master_key = await self._master_key_manager.load_or_create()
            validate_master_key(master_key)
        except (MasterKeyDecryptionError, MasterKeyInitializationError, CookieCryptoError) as error:
            raise AuthTokenUnavailableError() from error
        try:
            payload = base64.b64decode(ciphertext.encode("ascii"), validate=True)
        except (binascii.Error, UnicodeError, ValueError) as error:
            raise AuthTokenUnavailableError() from error
        if len(payload) <= COOKIE_NONCE_LENGTH_BYTES:
            raise AuthTokenUnavailableError()
        nonce = payload[:COOKIE_NONCE_LENGTH_BYTES]
        body = payload[COOKIE_NONCE_LENGTH_BYTES:]
        try:
            plaintext = await asyncio.to_thread(
                AESGCM(master_key).decrypt,
                nonce,
                body,
                AUTH_TOKEN_ASSOCIATED_DATA,
            )
        except InvalidTag as error:
            raise AuthTokenUnavailableError(AUTH_TOKEN_DECRYPTION_ERROR_MESSAGE) from error
        except Exception as error:
            raise AuthTokenUnavailableError(AUTH_TOKEN_DECRYPTION_ERROR_MESSAGE) from error
        return plaintext.decode("utf-8")
