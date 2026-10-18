"""Atomic account operations for the local service."""

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
