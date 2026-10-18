"""Async database engine and session construction."""

from pathlib import Path

from sqlalchemy.ext.asyncio import AsyncEngine, async_sessionmaker, create_async_engine
from sqlalchemy.pool import StaticPool

from xianyu_client.core.constants import DATABASE_TIMEOUT_SECONDS


def create_database_engine(database_path: Path) -> AsyncEngine:
    """Create the single-connection async SQLite engine.

    Args:
        database_path: Absolute path to the local SQLite database.

    Returns:
        An async engine backed by a single serialized connection.
    """
    return create_async_engine(
        f"sqlite+aiosqlite:///{database_path.as_posix()}",
        poolclass=StaticPool,
        connect_args={
            "timeout": DATABASE_TIMEOUT_SECONDS,
            "check_same_thread": False,
        },
    )


def create_session_maker(engine: AsyncEngine) -> async_sessionmaker:
    """Create a session factory bound to the supplied async engine."""
    return async_sessionmaker(engine, expire_on_commit=False)

