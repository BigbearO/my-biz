"""Single-table repository operations for local Goodfish accounts."""

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
