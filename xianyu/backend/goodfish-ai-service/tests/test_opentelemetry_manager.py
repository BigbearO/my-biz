import pytest

from app.observability import OpenTelemetrySettings, TelemetryManager


def create_manager(**overrides: object) -> TelemetryManager:
    settings = OpenTelemetrySettings.from_mapping(
        {"opentelemetry": {"enabled": True, **overrides}}
    )
    return TelemetryManager(
        settings,
        environment="test",
        default_service_name="default-service",
    )


def test_disabled_manager_does_not_start_or_create_interceptors() -> None:
    settings = OpenTelemetrySettings.from_mapping({})
    manager = TelemetryManager(settings, "test", "default-service")

    manager.start()

    assert manager.started is False
    assert manager.grpc_client_interceptors() == []


def test_manager_uses_aio_grpc_interceptors(
    monkeypatch: pytest.MonkeyPatch,
) -> None:
    interceptor = object()
    monkeypatch.setattr(
        "app.observability.manager.aio_client_interceptors",
        lambda: [interceptor],
    )
    manager = create_manager()
    manager._started = True

    assert manager.grpc_client_interceptors() == [interceptor]


def test_manager_does_not_create_grpc_interceptors_when_traces_disabled() -> None:
    manager = create_manager(
        {
            "exporter": {"protocol": "grpc"},
            "traces": {"enabled": False},
        }
    )
    manager._started = True

    assert manager.grpc_client_interceptors() == []


def test_http_endpoint_receives_signal_path() -> None:
    manager = create_manager(
        {
            "exporter": {
                "protocol": "http",
                "endpoint": "http://collector:4318",
            }
        }
    )

    trace_endpoint = manager._signal_endpoint("", "/v1/traces")
    log_endpoint = manager._signal_endpoint("", "/v1/logs")
    custom_endpoint = manager._signal_endpoint(
        "https://collector/v1/logs",
        "/v1/logs",
    )

    assert trace_endpoint == "http://collector:4318/v1/traces"
    assert log_endpoint == "http://collector:4318/v1/logs"
    assert custom_endpoint == "https://collector/v1/logs"


def test_grpc_endpoint_keeps_base_address() -> None:
    manager = create_manager(
        {"exporter": {"protocol": "grpc", "endpoint": "http://collector:4317"}}
    )

    endpoint = manager._signal_endpoint("", "/v1/traces")

    assert endpoint == "http://collector:4317"


def test_resource_uses_configured_service_name() -> None:
    manager = create_manager({"service_name": "custom-service"})

    resource = manager._create_resource()

    assert resource.attributes["service.name"] == "custom-service"
    assert resource.attributes["service.version"]
    assert resource.attributes["deployment.environment.name"] == "test"


def test_resource_falls_back_to_nacos_service_name() -> None:
    manager = create_manager()

    resource = manager._create_resource()

    assert resource.attributes["service.name"] == "default-service"

