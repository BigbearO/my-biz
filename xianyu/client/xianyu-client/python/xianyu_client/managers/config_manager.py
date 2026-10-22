"""Atomic configuration operations for the local service."""

from sqlalchemy.ext.asyncio import AsyncSession

from xianyu_client.core.constants import (
    CONFIG_AUTH_TOKEN,
    CONFIG_AUTH_TOKEN_DESCRIPTION,
    CONFIG_CLIENT_ID,
    CONFIG_CLIENT_ID_DESCRIPTION,
    CONFIG_EMPTY_VALUE,
    CONFIG_LOG_LEVEL,
    CONFIG_LOG_LEVEL_DEFAULT,
    CONFIG_LOG_LEVEL_DESCRIPTION,
    CONFIG_MAX_LISTEN_COUNT,
    CONFIG_MAX_LISTEN_COUNT_DEFAULT,
    CONFIG_MAX_LISTEN_COUNT_DESCRIPTION,
)
from xianyu_client.db.models import ConfigModel
from xianyu_client.repositories.config_repository import ConfigRepository


class ConfigManager:
    """Manages default config initialization and reads over the repository."""

    def __init__(self, session_factory) -> None:
        """Create a manager with its own session factory."""
        self._session_factory = session_factory

    async def initialize_defaults(self) -> None:
        """Initialize documented defaults without overwriting user values."""
        defaults = (
            (CONFIG_MAX_LISTEN_COUNT, CONFIG_MAX_LISTEN_COUNT_DEFAULT, CONFIG_MAX_LISTEN_COUNT_DESCRIPTION),
            (CONFIG_LOG_LEVEL, CONFIG_LOG_LEVEL_DEFAULT, CONFIG_LOG_LEVEL_DESCRIPTION),
            (CONFIG_AUTH_TOKEN, CONFIG_EMPTY_VALUE, CONFIG_AUTH_TOKEN_DESCRIPTION),
            (CONFIG_CLIENT_ID, CONFIG_EMPTY_VALUE, CONFIG_CLIENT_ID_DESCRIPTION),
        )
        async with self._session_factory() as session:
            repository = ConfigRepository(session)
            for key, value, description in defaults:
                await repository.insert_default(key, value, description)
            await session.commit()

    async def get_value(self, key: str, default: str = CONFIG_EMPTY_VALUE) -> str:
        """Return one config value or the supplied default."""
        async with self._session_factory() as session:
            repository = ConfigRepository(session)
            config: ConfigModel | None = await repository.find_by_key(key)
            return config.value if config is not None else default

    async def set_value(self, key: str, value: str, description: str) -> None:
        """Persist one configuration value atomically.

        Args:
            key: Unique configuration key.
            value: New configuration value.
            description: Description used when the row does not exist yet.
        """
        async with self._session_factory() as session:
            repository = ConfigRepository(session)
            await repository.upsert_value(key, value, description)
            await session.commit()

