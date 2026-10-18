from sqlalchemy import select

from xianyu_client.core.constants import (
    CONFIG_LOG_LEVEL,
    CONFIG_LOG_LEVEL_DEFAULT,
    CONFIG_MAX_LISTEN_COUNT,
)
from xianyu_client.db.migrations import run_migrations
from xianyu_client.db.models import ConfigModel
from xianyu_client.db.session import create_database_engine, create_session_maker
from xianyu_client.managers.config_manager import ConfigManager


async def test_initialize_defaults_is_idempotent(tmp_path):
    """Default initialization preserves values already selected by the user."""
    database_path = tmp_path / "goodfish.db"
    run_migrations(database_path)
    engine = create_database_engine(database_path)
    session_factory = create_session_maker(engine)
    manager = ConfigManager(session_factory)

    await manager.initialize_defaults()
    assert await manager.get_value(CONFIG_LOG_LEVEL) == CONFIG_LOG_LEVEL_DEFAULT
    async with session_factory() as session:
        config = (await session.execute(select(ConfigModel).where(ConfigModel.key == CONFIG_MAX_LISTEN_COUNT))).scalar_one()
        config.value = "8"
        await session.commit()
    await manager.initialize_defaults()

    assert await manager.get_value(CONFIG_MAX_LISTEN_COUNT) == "8"
    await engine.dispose()

