"""Business orchestration for local Goodfish account management."""

from xianyu import XianyuProtocol
from xianyu_client.core.crypto import CookieCryptoError
from xianyu_client.core.errors import (
    AccountNotFoundError,
    CredentialUnavailableError,
    DuplicateGoodfishIdError,
    InvalidXianyuCookieError,
    XianyuCookieMismatchError,
    XianyuRiskControlError,
)
from xianyu_client.db.models import GoodfishAccountModel
from xianyu_client.managers.account_manager import AccountManager
from xianyu_client.managers.cookie_credential_manager import CookieCredentialManager
from xianyu_client.managers.master_key_manager import (
    MasterKeyDecryptionError,
    MasterKeyInitializationError,
)
from xianyu_client.schemas.account_converters import AccountConvert
from xianyu_client.schemas.account_schemas import (
    AccountCreateRequest,
    AccountDetailResponse,
    AccountResponse,
    AccountUpdateRequest,
)
from xianyu_client.services.auth_service import AuthService
from xianyu_client.services.cloud_client import CloudClient
from xianyu_client.services.credential_service import CredentialService


class AccountService:
    """Coordinates account persistence, cookie protection, and credential state."""

    def __init__(
        self,
        account_manager: AccountManager,
        cookie_credential_manager: CookieCredentialManager,
        credential_service: CredentialService,
        xianyu_protocol: XianyuProtocol,
        auth_service: AuthService,
        cloud_client: CloudClient,
    ) -> None:
        """Create an account service with its local collaborators.

        Args:
            account_manager: Manager providing atomic account persistence.
            cookie_credential_manager: Manager encrypting cookie plaintext.
            credential_service: Service tracking master-key and ciphertext health.
            xianyu_protocol: Protocol boundary used to verify Goodfish cookies.
            auth_service: Service proving the caller is logged in.
            cloud_client: Outbound client used to report account metadata.
        """
        self._account_manager = account_manager
        self._cookie_credential_manager = cookie_credential_manager
        self._credential_service = credential_service
        self._xianyu_protocol = xianyu_protocol
        self._auth_service = auth_service
        self._cloud_client = cloud_client

    async def list_accounts(self) -> list[AccountResponse]:
        """List local accounts without returning cookie material.

        Returns:
            All accounts in deterministic creation order.
        """
        accounts = await self._account_manager.list_all()
        return [AccountConvert.to_response(account) for account in accounts]

    async def create_account(self, request: AccountCreateRequest) -> AccountResponse:
        """Create one local account after the cloud accepted its metadata.

        Args:
            request: Validated account creation payload.

        Returns:
            The persisted non-secret account representation.

        Raises:
            DuplicateGoodfishIdError: If the Goodfish ID already exists.
            CredentialUnavailableError: If cookie encryption is unavailable.
            InvalidXianyuCookieError: If the Cookie cannot pass Goodfish verification.
            XianyuNetworkError: If Goodfish cannot be reached.
            XianyuProtocolError: If Goodfish returns an unexpected response.
            XianyuRiskControlError: If Goodfish requires manual verification.
            NotLoggedInError: If no cloud token is stored locally.
            CloudUnauthorizedError: If the gateway rejects the token.
            CloudForbiddenError: If the account authorization expired.
            CloudReportError: If the cloud cannot accept the account report.
        """
        await self._auth_service.ensure_logged_in()
        account_info = (await self._xianyu_protocol.verify_cookie(request.cookie)).bounded()
        if await self._account_manager.find_by_goodfish_id(account_info.goodfish_id) is not None:
            raise DuplicateGoodfishIdError()
        await self._cloud_client.report_account(
            goodfish_id=account_info.goodfish_id,
            nickname=account_info.nickname or account_info.goodfish_id,
            remark=request.remark or "",
        )
        encrypted_cookie = await self._encrypt_cookie(request.cookie)
        account = GoodfishAccountModel.create_local(
            goodfish_id=account_info.goodfish_id,
            goodfish_nickname=account_info.nickname,
            encrypted_cookie=encrypted_cookie,
            remark=request.remark,
        )
        account.mark_cookie_validation(True)
        created_account = await self._account_manager.create(account)
        await self._credential_service.initialize()
        return AccountConvert.to_response(created_account)

    async def get_account_detail(self, account_id: int) -> AccountDetailResponse:
        """Return one account together with its decrypted cookie.

        Args:
            account_id: Local database identifier.

        Returns:
            The account representation including the plaintext cookie.

        Raises:
            AccountNotFoundError: If the account does not exist.
            CredentialUnavailableError: If the stored cookie cannot be decrypted.
        """
        account = await self._account_manager.find_by_id(account_id)
        if account is None:
            raise AccountNotFoundError()
        cookie = await self._decrypt_cookie(account.cookie)
        return AccountConvert.to_detail_response(account, cookie)

    async def update_account(self, account_id: int, request: AccountUpdateRequest) -> AccountResponse:
        """Edit profile fields and optionally replace the stored cookie.

        Args:
            account_id: Local database identifier.
            request: Validated profile and optional cookie replacement payload.

        Returns:
            The updated non-secret account representation.

        Raises:
            AccountNotFoundError: If the account does not exist.
            AccountStateConflictError: If cookie replacement is not allowed.
            CredentialUnavailableError: If cookie encryption is unavailable.
            InvalidXianyuCookieError: If the replacement Cookie is invalid.
            XianyuCookieMismatchError: If the replacement Cookie belongs to another account.
            XianyuNetworkError: If Goodfish cannot be reached.
            XianyuProtocolError: If Goodfish returns an unexpected response.
            XianyuRiskControlError: If Goodfish requires manual verification.
        """
        if request.cookie is None:
            account = await self._account_manager.update_profile(
                account_id=account_id,
                goodfish_nickname=request.goodfish_nickname,
                remark=request.remark,
            )
            return AccountConvert.to_response(account)

        existing_account = await self._account_manager.find_by_id(account_id)
        if existing_account is None:
            raise AccountNotFoundError()
        existing_account.ensure_cookie_change_allowed()
        account_info = (await self._xianyu_protocol.verify_cookie(request.cookie)).bounded()
        if not account_info.belongs_to(existing_account.goodfish_id):
            raise XianyuCookieMismatchError()
        encrypted_cookie = await self._encrypt_cookie(request.cookie)
        account = await self._account_manager.replace_verified_cookie(
            account_id=account_id,
            encrypted_cookie=encrypted_cookie,
            nickname=account_info.nickname,
        )
        await self._credential_service.initialize()
        return AccountConvert.to_response(account)

    async def verify_account(self, account_id: int) -> AccountResponse:
        """Manually verify one stored cookie and record the outcome.

        Args:
            account_id: Local database identifier.

        Returns:
            The updated non-secret account representation.

        Raises:
            AccountNotFoundError: If the account does not exist.
            AccountStateConflictError: If the account is not idle.
            CredentialUnavailableError: If the stored cookie cannot be decrypted.
            InvalidXianyuCookieError: If the stored Cookie is invalid or expired.
            XianyuCookieMismatchError: If the stored Cookie no longer matches the account.
            XianyuNetworkError: If Goodfish cannot be reached.
            XianyuProtocolError: If Goodfish returns an unexpected response.
            XianyuRiskControlError: If Goodfish requires manual verification.
        """
        existing_account = await self._account_manager.find_by_id(account_id)
        if existing_account is None:
            raise AccountNotFoundError()
        existing_account.ensure_cookie_change_allowed()
        cookie = await self._decrypt_cookie(existing_account.cookie)
        try:
            account_info = (await self._xianyu_protocol.verify_cookie(cookie)).bounded()
        except (InvalidXianyuCookieError, XianyuRiskControlError) as error:
            await self._account_manager.mark_cookie_validation(account_id, False)
            raise error
        if not account_info.belongs_to(existing_account.goodfish_id):
            await self._account_manager.mark_cookie_validation(account_id, False)
            raise XianyuCookieMismatchError()
        account = await self._account_manager.mark_cookie_validation(account_id, True)
        return AccountConvert.to_response(account)

    async def delete_account(self, account_id: int) -> AccountResponse:
        """Delete one idle local account and refresh credential health.

        Args:
            account_id: Local database identifier.

        Returns:
            The deleted non-secret account representation.

        Raises:
            AccountNotFoundError: If the account does not exist.
            AccountStateConflictError: If the account is not idle.
        """
        account = await self._account_manager.delete(account_id)
        await self._credential_service.initialize()
        return AccountConvert.to_response(account)

    async def _encrypt_cookie(self, cookie: str) -> str:
        """Encrypt cookie plaintext while mapping failures to a safe error.

        Args:
            cookie: Plaintext cookie value that exists only in this call.

        Returns:
            Base64 AES-GCM ciphertext for persistence.

        Raises:
            CredentialUnavailableError: If the key or encryption operation fails.
        """
        try:
            await self._credential_service.ensure_cookie_writable()
            return await self._cookie_credential_manager.encrypt(cookie)
        except (
            CookieCryptoError,
            MasterKeyDecryptionError,
            MasterKeyInitializationError,
        ) as error:
            raise CredentialUnavailableError() from error

    async def _decrypt_cookie(self, encrypted_cookie: str) -> str:
        """Decrypt cookie ciphertext while mapping failures to a safe error.

        Args:
            encrypted_cookie: Base64 AES-GCM value from `goodfish_account.cookie`.

        Returns:
            Cookie plaintext that exists only in this orchestration call.

        Raises:
            CredentialUnavailableError: If the key or ciphertext cannot be used.
        """
        try:
            return await self._cookie_credential_manager.decrypt(encrypted_cookie)
        except (
            CookieCryptoError,
            MasterKeyDecryptionError,
            MasterKeyInitializationError,
        ) as error:
            raise CredentialUnavailableError() from error
