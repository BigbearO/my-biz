import os
from pathlib import Path

from pydantic import Field, field_validator
from pydantic_settings import BaseSettings, SettingsConfigDict

from .constants import (
    APP_ENV_DEFAULT,
    CLUSTER_NAME_DEFAULT,
    CONFIG_FORMAT_PROPERTIES,
    CONFIG_FORMAT_YAML,
    NACOS_DEFAULT_GROUP,
    NACOS_LOG_LEVEL_WARNING,
)


class BootstrapSettings(BaseSettings):
    model_config = SettingsConfigDict(
        env_file=(".env", f".env.{os.getenv('APP_ENV', APP_ENV_DEFAULT)}"),
        env_file_encoding="utf-8",
        extra="ignore",
    )

    app_env: str = APP_ENV_DEFAULT
    server_host: str = "0.0.0.0"
    server_port: int = Field(default=8000, ge=1, le=65535)

    nacos_server_addr: str = Field(min_length=1)
    nacos_namespace_id: str = Field(min_length=1)
    nacos_service_name: str = Field(min_length=1)
    nacos_data_id: str = ""
    nacos_group: str = NACOS_DEFAULT_GROUP
    nacos_cluster_name: str = CLUSTER_NAME_DEFAULT
    nacos_username: str | None = None
    nacos_password: str | None = None
    nacos_context_path: str = ""
    nacos_config_format: str = CONFIG_FORMAT_YAML
    nacos_register_ip: str = ""
    nacos_cache_dir: str = ".nacos/cache"
    nacos_log_dir: str = ".nacos/log"
    nacos_log_level: str = NACOS_LOG_LEVEL_WARNING

    local_config_path: Path = Path("config/application.yaml")

    @field_validator(
        "nacos_group",
        "nacos_cluster_name",
        "nacos_config_format",
        "nacos_data_id",
        "nacos_context_path",
        "nacos_register_ip",
    )
    @classmethod
    def strip_optional_text(cls, value: str) -> str:
        return value.strip()

    @field_validator("nacos_config_format")
    @classmethod
    def validate_config_format(cls, value: str) -> str:
        normalized = value.lower()
        if normalized not in {CONFIG_FORMAT_YAML, CONFIG_FORMAT_PROPERTIES}:
            raise ValueError("nacos_config_format must be yaml or properties")
        return normalized
