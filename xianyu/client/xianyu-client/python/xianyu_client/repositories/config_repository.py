"""Single-table repository operations for local config rows."""

from typing import Optional

from sqlalchemy import select
from sqlalchemy.dialects.sqlite import insert
from sqlalchemy.ext.asyncio import AsyncSession

from xianyu_client.db.models import ConfigModel


class ConfigRepository:
    """Repository for reading and initializing `config` rows."""

    def __init__(self, session: AsyncSession) -> None:
        """Create a repository bound to one unit of work."""
        self._session = session

    async def find_by_key(self, key: str) -> Optional[ConfigModel]:
        """Return one config row by its unique key."""
        result = await self._session.execute(select(ConfigModel).where(ConfigModel.key == key))
        return result.scalar_one_or_none()

    async def insert_default(self, key: str, value: str, description: str) -> None:
        """Insert a default config row without replacing an existing value."""
        statement = insert(ConfigModel).values(
            key=key,
            value=value,
            description=description,
        ).on_conflict_do_nothing(index_elements=[ConfigModel.key])
        await self._session.execute(statement)

