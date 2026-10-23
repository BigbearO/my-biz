"""Business configuration models for the conversation service.

Each settings section mirrors one top-level key of the Nacos Data ID
``goodfish-ai-service.yaml`` (with ``config/application.yaml`` as the local
fallback). Parsing follows the same pattern as
``xxs_nacos_grpc.GrpcSettings.from_mapping``: strict models, explicit
validation errors, no magic defaults hidden in call sites.
"""

from typing import Any, Mapping

from pydantic import BaseModel, ConfigDict, Field, field_validator

from .constants import (
    CONFIG_KEY_DATABASE,
    CONFIG_KEY_IMAGE,
    CONFIG_KEY_REDIS,
    GRPC_SERVICE_NAME_ITEM_DEFAULT,
    INTENT_KEYWORD_KEYS,
)


class BusinessSettingsError(ValueError):
    """Raised when a business configuration value is invalid."""


class DatabaseSettings(BaseModel):
    """PostgreSQL connection for the three conversation tables."""

    model_config = ConfigDict(extra="forbid")

    url: str = Field(default="postgresql+asyncpg://goodfish:goodfish@127.0.0.1:5432/goodfish_ai", min_length=1)
    pool_size: int = Field(default=5, ge=1)
    max_overflow: int = Field(default=10, ge=0)
    echo: bool = False


class RedisSettings(BaseModel):
    """Redis connection used only for idempotency keys."""

    model_config = ConfigDict(extra="forbid")

    url: str = Field(default="redis://127.0.0.1:6379/0", min_length=1)


class ConversationSettings(BaseModel):
    """Conversation context assembly parameters (hot reloadable)."""

    model_config = ConfigDict(extra="forbid")

    max_history_messages: int = Field(default=20, ge=1)


class IdempotencySettings(BaseModel):
    """Redis idempotency TTLs (hot reloadable).

    ``processing_ttl_seconds`` must cover the worst-case reply path (65s)
    plus a buffer, otherwise concurrent retries can pass through the
    placeholder and duplicate generation.
    """

    model_config = ConfigDict(extra="forbid")

    ttl_seconds: int = Field(default=600, ge=1)
    processing_ttl_seconds: int = Field(default=100, ge=1)


class LlmSettings(BaseModel):
    """OpenAI-compatible HTTP call parameters (hot reloadable)."""

    model_config = ConfigDict(extra="forbid")

    timeout_seconds: float = Field(default=30.0, gt=0)
    max_retries: int = Field(default=2, ge=0)
    retry_delay_seconds: float = Field(default=2.0, ge=0)
    total_budget_seconds: float = Field(default=65.0, gt=0)


class ImageSettings(BaseModel):
    """Local-disk image storage parameters."""

    model_config = ConfigDict(extra="forbid")

    storage_dir: str = Field(default="/data/goodfish/images", min_length=1)
    public_base_url: str = Field(
        default="http://127.0.0.1:8000/api/ai/image/view",
        min_length=1,
    )
    max_size_mb: int = Field(default=10, ge=1)


class IntentSettings(BaseModel):
    """Keyword tables for intent logging (hot reloadable, log-only)."""

    model_config = ConfigDict(extra="forbid")

    keywords: dict[str, list[str]] = Field(
        default_factory=lambda: {
            "price": ["多少钱", "价格", "便宜", "优惠", "砍价", "包邮"],
            "tech": ["怎么用", "功能", "型号", "参数", "支持"],
            "chitchat": ["哈哈", "在吗", "你好", "谢谢"],
        }
    )

    @field_validator("keywords")
    @classmethod
    def validate_keywords(cls, value: dict[str, list[str]]) -> dict[str, list[str]]:
        unknown = set(value) - set(INTENT_KEYWORD_KEYS)
        if unknown:
            raise BusinessSettingsError(
                f"intent.keywords contains unknown intent keys: {sorted(unknown)}"
            )
        for intent, words in value.items():
            if not isinstance(words, list) or not all(isinstance(w, str) for w in words):
                raise BusinessSettingsError(
                    f"intent.keywords.{intent} must be a list of strings"
                )
        return value


class ItemSettings(BaseModel):
    """Item downstream tuning (fake client is dev-only)."""

    model_config = ConfigDict(extra="forbid")

    service_name: str = Field(default=GRPC_SERVICE_NAME_ITEM_DEFAULT, min_length=1)
    fake_enabled: bool = False


class QuerySettings(BaseModel):
    """History query pagination defaults (used by later batches)."""

    model_config = ConfigDict(extra="forbid")

    default_page_size: int = Field(default=20, ge=1)
    max_page_size: int = Field(default=100, ge=1)


class BusinessSettings(BaseModel):
    """Aggregate of all business configuration sections."""

    model_config = ConfigDict(extra="forbid")

    database: DatabaseSettings = Field(default_factory=DatabaseSettings)
    redis: RedisSettings = Field(default_factory=RedisSettings)
    conversation: ConversationSettings = Field(default_factory=ConversationSettings)
    idempotency: IdempotencySettings = Field(default_factory=IdempotencySettings)
    llm: LlmSettings = Field(default_factory=LlmSettings)
    image: ImageSettings = Field(default_factory=ImageSettings)
    intent: IntentSettings = Field(default_factory=IntentSettings)
    item: ItemSettings = Field(default_factory=ItemSettings)
    query: QuerySettings = Field(default_factory=QuerySettings)

    @classmethod
    def from_mapping(cls, value: Mapping[str, Any] | None) -> "BusinessSettings":
        """Parse only the business sections out of the whole service config.

        The service config also carries ``service``/``grpc``/``opentelemetry``
        sections owned by other modules; they are ignored here so strict
        validation stays meaningful for the sections we do own.
        """
        config = dict(value or {})
        business_only = {
            key: config[key] for key in cls.model_fields if key in config
        }
        try:
            return cls.model_validate(business_only)
        except BusinessSettingsError:
            raise
        except Exception as exc:
            raise BusinessSettingsError("Invalid business configuration") from exc


def warn_restart_required_sections(previous: "BusinessSettings", current: "BusinessSettings") -> list[str]:
    """Return names of sections that only take effect after a restart.

    Hot-reloadable parameters (conversation history size, idempotency TTLs,
    llm timeouts, intent keywords) are read per request from the current
    settings. Connection-level values (database/redis URLs, image storage
    directory) are captured at startup and must not be reconnected at runtime.
    """
    changed: list[str] = []
    if previous.database != current.database:
        changed.append(CONFIG_KEY_DATABASE)
    if previous.redis != current.redis:
        changed.append(CONFIG_KEY_REDIS)
    if previous.image.storage_dir != current.image.storage_dir:
        changed.append(CONFIG_KEY_IMAGE)
    return changed



