"""Async SQLAlchemy engine and session factory for the conversation tables."""

from sqlalchemy.ext.asyncio import (
    AsyncEngine,
    AsyncSession,
    async_sessionmaker,
    create_async_engine,
)

from .settings import DatabaseSettings


def create_database_engine(settings: DatabaseSettings) -> AsyncEngine:
    """Create the application-wide async engine.

    The engine is created once at startup; URL changes pushed through Nacos
    only take effect after a restart (see ``warn_restart_required_sections``).
    """
    return create_async_engine(
        settings.url,
        pool_size=settings.pool_size,
        max_overflow=settings.max_overflow,
        echo=settings.echo,
        pool_pre_ping=True,
    )


def create_session_maker(engine: AsyncEngine) -> async_sessionmaker[AsyncSession]:
    """Create the session factory used by repositories and managers."""
    return async_sessionmaker(
        engine,
        class_=AsyncSession,
        expire_on_commit=False,
        autoflush=False,
    )
