import pytest

from xxs_nacos_grpc import GrpcSettings
from xxs_nacos_grpc.errors import GrpcConfigError


def test_settings_from_mapping_reads_all_sections() -> None:
    settings = GrpcSettings.from_mapping(
        {
            "grpc": {
                "client": {"client_id": "ai-service", "default_timeout_ms": 2000},
                "discovery": {
                    "services": {"example": "example-service-grpc"},
                    "debounce_ms": 20,
                },
                "channel": {"lb_policy": "pick_first", "max_message_mb": 8},
            }
        }
    )

    assert settings.client.client_id == "ai-service"
    assert settings.client.default_timeout_seconds == 2
    assert settings.discovery.services == {"example": "example-service-grpc"}
    assert settings.discovery.debounce_ms == 20
    assert settings.channel.lb_policy == "pick_first"
    assert settings.channel.max_message_mb == 8


def test_settings_support_missing_grpc_config() -> None:
    settings = GrpcSettings.from_mapping({})

    assert settings.discovery.services == {}
    assert settings.channel.lb_policy == "round_robin"


def test_settings_reject_invalid_lb_policy() -> None:
    with pytest.raises(GrpcConfigError):
        GrpcSettings.from_mapping({"grpc": {"channel": {"lb_policy": "random"}}})
