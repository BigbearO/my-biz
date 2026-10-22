"""Runtime settings loaded from the process environment."""

from dataclasses import dataclass
from os import environ
from pathlib import Path
from typing import Mapping, Optional

from xianyu_client.core.constants import (
    CLOUD_BACKEND_BASE_URL_DEFAULT,
    DATA_DIRECTORY_NAME,
    DEFAULT_APP_DATA_DIR,
    DEFAULT_LOG_LEVEL,
    DEFAULT_RUNTIME_ENV,
    DATABASE_FILE_NAME,
    ENV_CLOUD_BASE_URL,
    ENV_DATA_DIR,
    ENV_LOCAL_TOKEN,
    ENV_LOG_LEVEL,
    ENV_RUNTIME_ENV,
    LOG_DIRECTORY_NAME,
)


@dataclass(frozen=True)
class RuntimeSettings:
    """Immutable runtime configuration for the local service."""

    data_dir: Path
    local_token: str
    log_level: str
    runtime_env: str
    cloud_base_url: str = CLOUD_BACKEND_BASE_URL_DEFAULT

    @property
    def database_path(self) -> Path:
        """Return the absolute SQLite database path."""
        return self.data_dir / DATA_DIRECTORY_NAME / DATABASE_FILE_NAME

    @property
    def log_dir(self) -> Path:
        """Return the absolute local log directory."""
        return self.data_dir / LOG_DIRECTORY_NAME


def load_settings(process_env: Optional[Mapping[str, str]] = None) -> RuntimeSettings:
    """Load settings from the environment and create local runtime directories.

    Args:
        process_env: An optional environment mapping used by tests and callers.

    Returns:
        The populated immutable runtime settings.
    """
    env = process_env if process_env is not None else environ
    configured_data_dir = env.get(ENV_DATA_DIR)
    data_dir = Path(configured_data_dir).expanduser().resolve() if configured_data_dir else DEFAULT_APP_DATA_DIR
    log_dir = data_dir / LOG_DIRECTORY_NAME
    database_dir = data_dir / DATA_DIRECTORY_NAME
    database_dir.mkdir(parents=True, exist_ok=True)
    log_dir.mkdir(parents=True, exist_ok=True)

    return RuntimeSettings(
        data_dir=data_dir,
        local_token=env.get(ENV_LOCAL_TOKEN, ""),
        log_level=env.get(ENV_LOG_LEVEL, DEFAULT_LOG_LEVEL).upper(),
        runtime_env=env.get(ENV_RUNTIME_ENV, DEFAULT_RUNTIME_ENV),
        cloud_base_url=env.get(ENV_CLOUD_BASE_URL, CLOUD_BACKEND_BASE_URL_DEFAULT).rstrip("/"),
    )

