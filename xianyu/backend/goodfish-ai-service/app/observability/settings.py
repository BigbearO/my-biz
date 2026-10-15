import os
from typing import Any, Mapping

from pydantic import BaseModel, ConfigDict, Field, field_validator

from ..constants import (
    OPENTELEMETRY_CONFIG_KEY,
    OPENTELEMETRY_DEFAULT_LOG_EXPORT_TIMEOUT_MS,
    OPENTELEMETRY_DEFAULT_LOG_SCHEDULE_DELAY_MS,
    OPENTELEMETRY_DEFAULT_SAMPLING_RATIO,
    OPENTELEMETRY_DEFAULT_TRACE_EXPORT_TIMEOUT_MS,
    OPENTELEMETRY_DEFAULT_TRACE_SCHEDULE_DELAY_MS,
    OPENTELEMETRY_ENDPOINT_ENV,
    OPENTELEMETRY_EXPORT_PROTOCOL_GRPC,
    OPENTELEMETRY_EXPORT_PROTOCOL_HTTP,
    OPENTELEMETRY_HEADERS_ENV,
    OPENTELEMETRY_HTTP_PROTOCOL_ENDPOINT_ENV,
    OPENTELEMETRY_LOGS_ENDPOINT_ENV,
    OPENTELEMETRY_PROTOCOL_HTTP_PROTOBUF,
    OPENTELEMETRY_TRACES_ENDPOINT_ENV,
)
from ..errors import OpenTelemetryConfigError


class OpenTelemetryExporterSettings(BaseModel):
    model_config = ConfigDict(extra="forbid")

    endpoint: str = ""
    trace_endpoint: str = ""
    log_endpoint: str = ""
    protocol: str = ""
    insecure: bool | None = None
    headers: dict[str, str] = Field(default_factory=dict)

    @field_validator("endpoint", "trace_endpoint", "log_endpoint")
    @classmethod
    def strip_endpoint(cls, value: str) -> str:
        return value.strip()

    @field_validator("protocol")
    @classmethod
    def normalize_protocol(cls, value: str) -> str:
        normalized_value = value.strip().lower()
        if normalized_value == OPENTELEMETRY_PROTOCOL_HTTP_PROTOBUF:
            return OPENTELEMETRY_EXPORT_PROTOCOL_HTTP
        return normalized_value


class OpenTelemetryTraceSettings(BaseModel):
    model_config = ConfigDict(extra="forbid")

    enabled: bool = True
    sampling_ratio: float = Field(
        default=OPENTELEMETRY_DEFAULT_SAMPLING_RATIO,
        ge=0,
        le=1,
    )
    schedule_delay_millis: int = Field(
        default=OPENTELEMETRY_DEFAULT_TRACE_SCHEDULE_DELAY_MS,
        ge=1,
    )
    export_timeout_millis: int = Field(
        default=OPENTELEMETRY_DEFAULT_TRACE_EXPORT_TIMEOUT_MS,
        ge=1,
    )


class OpenTelemetryLogSettings(BaseModel):
    model_config = ConfigDict(extra="forbid")

    enabled: bool = True
    schedule_delay_millis: int = Field(
        default=OPENTELEMETRY_DEFAULT_LOG_SCHEDULE_DELAY_MS,
        ge=1,
    )
    export_timeout_millis: int = Field(
        default=OPENTELEMETRY_DEFAULT_LOG_EXPORT_TIMEOUT_MS,
        ge=1,
    )


class OpenTelemetrySettings(BaseModel):
    model_config = ConfigDict(extra="forbid")

    enabled: bool = False
    service_name: str = ""
    exporter: OpenTelemetryExporterSettings = Field(
        default_factory=OpenTelemetryExporterSettings,
    )
    traces: OpenTelemetryTraceSettings = Field(
        default_factory=OpenTelemetryTraceSettings,
    )
    logs: OpenTelemetryLogSettings = Field(
        default_factory=OpenTelemetryLogSettings,
    )

    @classmethod
    def from_mapping(cls, value: Mapping[str, Any]) -> "OpenTelemetrySettings":
        try:
            telemetry_config = value.get(OPENTELEMETRY_CONFIG_KEY)
            if telemetry_config is None:
                settings = cls()
            elif not isinstance(telemetry_config, Mapping):
                raise OpenTelemetryConfigError(
                    "opentelemetry configuration must be a mapping"
                )
            else:
                settings = cls.model_validate(dict(telemetry_config))
            if settings.exporter.protocol not in {
                "",
                OPENTELEMETRY_EXPORT_PROTOCOL_GRPC,
                OPENTELEMETRY_EXPORT_PROTOCOL_HTTP,
            }:
                raise OpenTelemetryConfigError(
                    "opentelemetry.exporter.protocol must be grpc or http"
                )
            return settings._with_environment_defaults()
        except OpenTelemetryConfigError:
            raise
        except Exception as exc:
            raise OpenTelemetryConfigError(
                "Invalid opentelemetry configuration"
            ) from exc

    def _with_environment_defaults(self) -> "OpenTelemetrySettings":
        exporter = self.exporter.model_copy()
        exporter.endpoint = exporter.endpoint or os.getenv(
            OPENTELEMETRY_ENDPOINT_ENV,
            "",
        ).strip()
        exporter.trace_endpoint = exporter.trace_endpoint or os.getenv(
            OPENTELEMETRY_TRACES_ENDPOINT_ENV,
            "",
        ).strip()
        exporter.log_endpoint = exporter.log_endpoint or os.getenv(
            OPENTELEMETRY_LOGS_ENDPOINT_ENV,
            "",
        ).strip()
        exporter.protocol = exporter.protocol or os.getenv(
            OPENTELEMETRY_HTTP_PROTOCOL_ENDPOINT_ENV,
            "",
        ).strip().lower()
        if exporter.protocol == OPENTELEMETRY_PROTOCOL_HTTP_PROTOBUF:
            exporter.protocol = OPENTELEMETRY_EXPORT_PROTOCOL_HTTP
        if not exporter.headers:
            exporter.headers = _parse_headers_environment()
        return self.model_copy(update={"exporter": exporter})


def _parse_headers_environment() -> dict[str, str]:
    raw_headers = os.getenv(OPENTELEMETRY_HEADERS_ENV, "").strip()
    if not raw_headers:
        return {}
    headers: dict[str, str] = {}
    for raw_header in raw_headers.split(","):
        if not raw_header.strip():
            continue
        key, separator, value = raw_header.partition("=")
        if not separator or not key.strip():
            raise OpenTelemetryConfigError(
                "OTEL_EXPORTER_OTLP_HEADERS must use key=value pairs"
            )
        headers[key.strip()] = value.strip()
    return headers
