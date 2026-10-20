"""Atomic account operations for the local service."""

from datetime import datetime
from typing import Optional

from sqlalchemy.exc import IntegrityError

from xianyu_client.core.errors import AccountNotFoundError, DuplicateGoodfishIdError
from xianyu_client.db.models import GoodfishAccountModel
from xianyu_client.repositories.account_repository import AccountRepository


class AccountManager:
    """Provides reusable account reads for business services."""

    def __init__(self, session_factory) -> None:
        """Create an account manager with its persistence session factory.

        Args:
            session_factory: Async session factory used for each atomic read.
        """
        self._session_factory = session_factory

    async def list_all(self) -> list[GoodfishAccountModel]:
        """Return all local accounts in stable creation order.

        Returns:
            All account entities in deterministic creation order.
        """
        async with self._session_factory() as session:
            return await AccountRepository(session).list_all()

    async def find_by_id(self, account_id: int) -> Optional[GoodfishAccountModel]:
        """Return one account by local ID without changing persistence state.

        Args:
            account_id: Local database identifier.

        Returns:
            The matching account, or `None` when it does not exist.
        """
        async with self._session_factory() as session:
            return await AccountRepository(session).find_by_id(account_id)

    async def find_by_goodfish_id(self, goodfish_id: str) -> Optional[GoodfishAccountModel]:
        """Return one account by Goodfish ID without changing persistence state.

        Args:
            goodfish_id: User-visible Goodfish account identifier.

        Returns:
            The matching account, or `None` when it does not exist.
        """
        async with self._session_factory() as session:
            return await AccountRepository(session).find_by_goodfish_id(goodfish_id)

    async def create(self, account: GoodfishAccountModel) -> GoodfishAccountModel:
        """Persist one new account atomically.

        Args:
            account: Unsaved account entity.

        Returns:
            The persisted account with database-generated values.

        Raises:
            DuplicateGoodfishIdError: When another account owns the Goodfish ID.
        """
        async with self._session_factory() as session:
            repository = AccountRepository(session)
            await repository.add(account)
            try:
                await session.commit()
            except IntegrityError as error:
                await session.rollback()
                raise DuplicateGoodfishIdError() from error
            return account

    async def update_profile(
        self,
        account_id: int,
        goodfish_nickname: str,
        remark: str,
    ) -> GoodfishAccountModel:
        """Atomically update editable profile fields.

        Args:
            account_id: Local database identifier.
            goodfish_nickname: New display nickname.
            remark: New operator note.

        Returns:
            The updated account entity.

        Raises:
            AccountNotFoundError: When the account ID does not exist.
        """
        async with self._session_factory() as session:
            repository = AccountRepository(session)
            account = await self._load_account(repository, account_id)
            account.update_profile(goodfish_nickname, remark)
            await session.commit()
            return account

    async def replace_cookie(self, account_id: int, encrypted_cookie: str) -> GoodfishAccountModel:
        """Atomically replace encrypted cookie material.

        Args:
            account_id: Local database identifier.
            encrypted_cookie: AES-GCM ciphertext produced by the credential manager.

        Returns:
            The updated account entity.

        Raises:
            AccountNotFoundError: When the account ID does not exist.
            AccountStateConflictError: When the account is not idle.
        """
        async with self._session_factory() as session:
            repository = AccountRepository(session)
            account = await self._load_account(repository, account_id)
            account.replace_cookie(encrypted_cookie)
            await session.commit()
            return account

    async def replace_verified_cookie(
        self,
        account_id: int,
        encrypted_cookie: str,
        nickname: str,
    ) -> GoodfishAccountModel:
        """Atomically replace a cookie that has passed Goodfish verification.

        Args:
            account_id: Local database identifier.
            encrypted_cookie: AES-GCM ciphertext produced by the credential manager.
            nickname: Protocol-verified nickname to refresh with the credential.

        Returns:
            The updated account entity.

        Raises:
            AccountNotFoundError: When the account ID does not exist.
            AccountStateConflictError: When the account is not idle.
        """
        async with self._session_factory() as session:
            repository = AccountRepository(session)
            account = await self._load_account(repository, account_id)
            account.replace_verified_cookie(encrypted_cookie, nickname)
            await session.commit()
            return account

    async def mark_cookie_validation(
        self,
        account_id: int,
        valid: bool,
        checked_at: datetime | None = None,
    ) -> GoodfishAccountModel:
        """Atomically record the latest cookie validation outcome.

        Args:
            account_id: Local database identifier.
            valid: Whether protocol verification succeeded.
            checked_at: Optional validation timestamp; defaults to current UTC time.

        Returns:
            The updated account entity.

        Raises:
            AccountNotFoundError: When the account ID does not exist.
        """
        async with self._session_factory() as session:
            repository = AccountRepository(session)
            account = await self._load_account(repository, account_id)
            account.mark_cookie_validation(valid, checked_at)
            await session.commit()
            return account

    async def delete(self, account_id: int) -> GoodfishAccountModel:
        """Atomically delete one idle account.

        Args:
            account_id: Local database identifier.

        Returns:
            The detached account entity that was deleted.

        Raises:
            AccountNotFoundError: When the account ID does not exist.
            AccountStateConflictError: When the account is not idle.
        """
        async with self._session_factory() as session:
            repository = AccountRepository(session)
            account = await self._load_account(repository, account_id)
            account.ensure_deletable()
            await repository.remove(account)
            await session.commit()
            return account

    @staticmethod
    async def _load_account(repository: AccountRepository, account_id: int) -> GoodfishAccountModel:
        """Load an account or raise the stable not-found domain error.

        Args:
            repository: Repository bound to the current transaction.
            account_id: Local database identifier.

        Returns:
            The loaded account entity.

        Raises:
            AccountNotFoundError: When the account ID does not exist.
        """
        account = await repository.find_by_id(account_id)
        if account is None:
            raise AccountNotFoundError()
        return account
