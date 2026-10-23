"""Alembic environment: resolve the URL from application configuration.

Precedence: ``DATABASE_URL`` environment variable, then the asyncpg URL
from ``config/application.yaml`` (with ``${VAR}`` placeholders expanded).
Migrations run against the same database the service uses; the service
itself never creates tables at runtime.
"""

import asyncio
import os
from logging.config import fileConfig

from alembic import context
from sqlalchemy import pool
from sqlalchemy.engine import Connection
from sqlalchemy.ext.asyncio import async_engine_from_config

from app.models import Base
from app.runtime_config import load_local_config
from app.settings import BusinessSettings
from app.utils.env_placeholder import expand_placeholders

config = context.config

if config.config_file_name is not None:
    fileConfig(config.config_file_name)

target_metadata = Base.metadata


def _resolve_url() -> str:
    env_url = os.environ.get("DATABASE_URL")
    if env_url:
        return env_url
    bootstrap_local_path = os.environ.get("ALEMBIC_LOCAL_CONFIG", "config/application.yaml")
    local_config = expand_placeholders(load_local_config(_to_path(bootstrap_local_path)))
    return BusinessSettings.from_mapping(local_config).database.url


def _to_path(value: str):
    from pathlib import Path

    return Path(value)


def run_migrations_offline() -> None:
    context.configure(
        url=_resolve_url(),
        target_metadata=target_metadata,
        literal_binds=True,
        dialect_opts={"paramstyle": "named"},
    )
    with context.begin_transaction():
        context.run_migrations()


def do_run_migrations(connection: Connection) -> None:
    context.configure(connection=connection, target_metadata=target_metadata)
    with context.begin_transaction():
        context.run_migrations()


async def run_async_migrations() -> None:
    configuration = config.get_section(config.config_ini_section, {})
    configuration["sqlalchemy.url"] = _resolve_url()
    connectable = async_engine_from_config(
        configuration,
        prefix="sqlalchemy.",
        poolclass=pool.NullPool,
    )
    async with connectable.connect() as connection:
        await connection.run_sync(do_run_migrations)
    await connectable.dispose()


def run_migrations_online() -> None:
    asyncio.run(run_async_migrations())


if context.is_offline_mode():
    run_migrations_offline()
else:
    run_migrations_online()
