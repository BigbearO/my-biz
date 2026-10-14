from typing import Any, Mapping

from pydantic import BaseModel, ConfigDict, Field, field_validator

from .errors import GrpcConfigError

GRPC_CONFIG_KEY = "grpc"
DEFAULT_DEBOUNCE_MS = 1500
DEFAULT_CLOSE_GRACE_SECONDS = 30
DEFAULT_RECONCILE_INTERVAL_SECONDS = 60
DEFAULT_KEEPALIVE_TIME_MS = 30000
DEFAULT_KEEPALIVE_TIMEOUT_MS = 10000
DEFAULT_KEEPALIVE_PERMIT_WITHOUT_CALLS = 1
DEFAULT_MAX_MESSAGE_MB = 16
DEFAULT_TIMEOUT_MS = 3000


class GrpcClientSettings(BaseModel):
    model_config = ConfigDict(extra="forbid")

    client_id: str = ""
    default_timeout_ms: int = Field(default=DEFAULT_TIMEOUT_MS, ge=1)

    @property
    def default_timeout_seconds(self) -> float:
        return self.default_timeout_ms / 1000


class GrpcChannelSettings(BaseModel):
    model_config = ConfigDict(extra="forbid")

    lb_policy: str = "round_robin"
    keepalive_time_ms: int = Field(default=DEFAULT_KEEPALIVE_TIME_MS, ge=1)
    keepalive_timeout_ms: int = Field(
        default=DEFAULT_KEEPALIVE_TIMEOUT_MS,
        ge=1,
    )
    keepalive_permit_without_calls: int = Field(
        default=DEFAULT_KEEPALIVE_PERMIT_WITHOUT_CALLS,
        ge=0,
        le=1,
    )
    max_message_mb: int = Field(default=DEFAULT_MAX_MESSAGE_MB, ge=1)
    health_check: bool = False

    @field_validator("lb_policy")
    @classmethod
    def validate_lb_policy(cls, value: str) -> str:
        normalized_value = value.strip().lower()
        if normalized_value not in {"round_robin", "pick_first"}:
            raise GrpcConfigError("grpc.channel.lb_policy must be round_robin or pick_first")
        return normalized_value


class GrpcDiscoverySettings(BaseModel):
    model_config = ConfigDict(extra="forbid")

    services: dict[str, str] = Field(default_factory=dict)
    debounce_ms: int = Field(default=DEFAULT_DEBOUNCE_MS, ge=0)
    close_grace_seconds: int = Field(default=DEFAULT_CLOSE_GRACE_SECONDS, ge=0)
    reconcile_interval_seconds: int = Field(
        default=DEFAULT_RECONCILE_INTERVAL_SECONDS,
        ge=0,
    )

    @field_validator("services")
    @classmethod
    def validate_services(cls, value: dict[str, str]) -> dict[str, str]:
        normalized_services: dict[str, str] = {}
        for alias, service_name in value.items():
            normalized_alias = alias.strip()
            normalized_service_name = service_name.strip()
            if not normalized_alias or not normalized_service_name:
                raise GrpcConfigError(
                    "grpc.discovery.services aliases and service names cannot be empty"
                )
            if normalized_alias in normalized_services:
                raise GrpcConfigError(f"Duplicate gRPC service alias: {normalized_alias}")
            normalized_services[normalized_alias] = normalized_service_name
        return normalized_services


class GrpcSettings(BaseModel):
    model_config = ConfigDict(extra="forbid")

    client: GrpcClientSettings = Field(default_factory=GrpcClientSettings)
    discovery: GrpcDiscoverySettings = Field(default_factory=GrpcDiscoverySettings)
    channel: GrpcChannelSettings = Field(default_factory=GrpcChannelSettings)

    @classmethod
    def from_mapping(cls, value: Mapping[str, Any] | None) -> "GrpcSettings":
        try:
            grpc_config = (value or {}).get(GRPC_CONFIG_KEY)
            if grpc_config is None:
                return cls()
            if not isinstance(grpc_config, Mapping):
                raise GrpcConfigError("grpc configuration must be a mapping")
            return cls.model_validate(dict(grpc_config))
        except GrpcConfigError:
            raise
        except Exception as exc:
            raise GrpcConfigError("Invalid grpc configuration") from exc
