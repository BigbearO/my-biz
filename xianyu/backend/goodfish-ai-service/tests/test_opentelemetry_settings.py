import pytest

from app.errors import OpenTelemetryConfigError
from app.observability import OpenTelemetrySettings


def test_settings_defaults_to_disabled() -> None:
    settings = OpenTelemetrySettings.from_mapping({})

    assert settings.enabled is False
    assert settings.traces.enabled is True
    assert settings.logs.enabled is True
    assert settings.exporter.protocol == ""


def test_settings_parses_yaml_configuration() -> None:
    settings = OpenTelemetrySettings.from_mapping(
        {
            "opentelemetry": {
                "enabled": True,
                "service_name": "custom-ai-service",
                "exporter": {
                    "protocol": "http/protobuf",
                    "endpoint": " http://collector:4318 ",
                    "headers": {"api-key": "secret"},
                },
                "traces": {"sampling_ratio": 0.2},
            }
        }
    )

    assert settings.enabled is True
    assert settings.service_name == "custom-ai-service"
    assert settings.exporter.protocol == "http"
    assert settings.exporter.endpoint == "http://collector:4318"
    assert settings.exporter.headers == {"api-key": "secret"}
    assert settings.traces.sampling_ratio == 0.2


def test_settings_uses_environment_defaults(
    monkeypatch: pytest.MonkeyPatch,
) -> None:
    monkeypatch.setenv("OTEL_EXPORTER_OTLP_PROTOCOL", "http/protobuf")
    monkeypatch.setenv("OTEL_EXPORTER_OTLP_ENDPOINT", "http://collector:4318")
    monkeypatch.setenv("OTEL_EXPORTER_OTLP_HEADERS", "api-key=secret,key2=value2")

    settings = OpenTelemetrySettings.from_mapping(
        {"opentelemetry": {"enabled": True}}
    )

    assert settings.exporter.protocol == "http"
    assert settings.exporter.endpoint == "http://collector:4318"
    assert settings.exporter.headers == {"api-key": "secret", "key2": "value2"}


def test_settings_rejects_unknown_protocol() -> None:
    with pytest.raises(OpenTelemetryConfigError):
        OpenTelemetrySettings.from_mapping(
            {
                "opentelemetry": {
                    "enabled": True,
                    "exporter": {"protocol": "tcp"},
                }
            }
        )


def test_settings_rejects_unknown_field() -> None:
    with pytest.raises(OpenTelemetryConfigError):
        OpenTelemetrySettings.from_mapping(
            {"opentelemetry": {"enabled": True, "metrics": {}}}
        )
