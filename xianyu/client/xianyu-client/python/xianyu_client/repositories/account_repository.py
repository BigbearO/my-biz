"""Single-table repository operations for local Goodfish accounts."""

from typing import Optional

from sqlalchemy import select
from sqlalchemy.ext.asyncio import AsyncSession

from xianyu_client.db.models import GoodfishAccountModel


class AccountRepository:
    """Repository for reading and writing `goodfish_account` rows."""

    def __init__(self, session: AsyncSession) -> None:
        """Create a repository bound to one unit of work."""
        self._session = session

    async def list_all(self) -> list[GoodfishAccountModel]:
        """Return every local account ordered by creation time.

        Returns:
            All account entities in deterministic creation order.
        """
        result = await self._session.execute(
            select(GoodfishAccountModel).order_by(GoodfishAccountModel.created_at, GoodfishAccountModel.id)
        )
        return list(result.scalars().all())

    async def find_by_id(self, account_id: int) -> Optional[GoodfishAccountModel]:
        """Return one account by its local integer ID.

        Args:
            account_id: Local database identifier.

        Returns:
            The matching account, or `None` when it does not exist.
        """
        return await self._session.get(GoodfishAccountModel, account_id)

    async def find_by_goodfish_id(self, goodfish_id: str) -> Optional[GoodfishAccountModel]:
        """Return one account by its unique Goodfish ID.

        Args:
            goodfish_id: User-visible Goodfish account identifier.

        Returns:
            The matching account, or `None` when it does not exist.
        """
        result = await self._session.execute(
            select(GoodfishAccountModel).where(GoodfishAccountModel.goodfish_id == goodfish_id)
        )
        return result.scalar_one_or_none()

    async def add(self, account: GoodfishAccountModel) -> None:
        """Stage one new account for persistence.

        Args:
            account: Unsaved account entity.
        """
        self._session.add(account)

    async def remove(self, account: GoodfishAccountModel) -> None:
        """Stage one existing account for deletion.

        Args:
            account: Account entity loaded in the current session.
        """
        await self._session.delete(account)
