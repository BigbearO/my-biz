"""Programmatic Alembic migration runner."""

from pathlib import Path

from alembic import command
from alembic.config import Config


def run_migrations(database_path: Path) -> None:
    """Upgrade the local SQLite schema to the latest Alembic revision.

    Args:
        database_path: Absolute path to the SQLite database file.
    """
    script_location = Path(__file__).parents[2] / "alembic"
    alembic_config = Config()
    alembic_config.set_main_option("script_location", script_location.as_posix())
    alembic_config.set_main_option("sqlalchemy.url", f"sqlite:///{database_path.as_posix()}")
    alembic_config.attributes["configure_logger"] = False
    command.upgrade(alembic_config, "head")

