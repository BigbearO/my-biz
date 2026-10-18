"""Startup integrity orchestration for stored cookie credentials."""

from loguru import logger

from xianyu_client.core.constants import (
    COOKIE_UNDECRYPTABLE_LOG_MESSAGE,
    CREDENTIAL_STATE_READY,
    CREDENTIAL_STATE_UNDECRYPTABLE,
    MASTER_KEY_UNDECRYPTABLE_LOG_MESSAGE,
)
from xianyu_client.core.crypto import CookieCryptoError
from xianyu_client.managers.account_manager import AccountManager
from xianyu_client.managers.cookie_credential_manager import CookieCredentialManager
from xianyu_client.managers.master_key_manager import (
    MasterKeyDecryptionError,
    MasterKeyInitializationError,
)


class CredentialService:
    """Verifies master-key and stored-cookie decryptability without mutating data."""

    def __init__(self, credential_manager: CookieCredentialManager, account_manager: AccountManager) -> None:
        """Create a credential service with credential and account collaborators.

        Args:
            credential_manager: Manager that loads and verifies cookie ciphertext.
            account_manager: Manager that reads local account records.
        """
        self._credential_manager = credential_manager
        self._account_manager = account_manager
        self._state = CREDENTIAL_STATE_READY
        self._failed_account_ids: tuple[int, ...] = ()

    @property
    def state(self) -> str:
        """Return the current credential decryptability state."""
        return self._state

    @property
    def failed_account_ids(self) -> tuple[int, ...]:
        """Return account IDs whose stored cookie could not be decrypted."""
        return self._failed_account_ids

    async def initialize(self) -> str:
        """Initialize the master key and verify every stored cookie ciphertext.

        Returns:
            `ready` when all credentials verify, otherwise `undecryptable`.
        """
        try:
            await self._credential_manager.load_master_key()
        except (MasterKeyDecryptionError, MasterKeyInitializationError):
            self._state = CREDENTIAL_STATE_UNDECRYPTABLE
            self._failed_account_ids = ()
            logger.warning(MASTER_KEY_UNDECRYPTABLE_LOG_MESSAGE)
            return self._state

        failed_ids: list[int] = []
        accounts = await self._account_manager.list_all()
        for account in accounts:
            try:
                await self._credential_manager.decrypt(account.cookie)
            except CookieCryptoError:
                failed_ids.append(account.id)

        self._failed_account_ids = tuple(failed_ids)
        self._state = CREDENTIAL_STATE_READY if not failed_ids else CREDENTIAL_STATE_UNDECRYPTABLE
        if failed_ids:
            logger.warning(COOKIE_UNDECRYPTABLE_LOG_MESSAGE, failed_ids)
        return self._state
