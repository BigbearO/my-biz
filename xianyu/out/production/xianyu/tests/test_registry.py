from collections.abc import Sequence
from types import SimpleNamespace
from typing import Any

import pytest

from xxs_nacos_grpc import GrpcChannelRegistry, GrpcSettings


class FakeInstanceSource:
    def __init__(self, instances: Sequence[Any]) -> None:
        self.instances = instances
        self.subscriptions: dict[str, Any] = {}

    async def subscribe_instances(self, service_name: str, callback: Any) -> None:
        self.subscriptions[service_name] = callback

    async def list_healthy_instances(self, service_name: str) -> Sequence[Any]:
        return self.instances


def create_registry(service_name: str = "llm-grpc") -> tuple[GrpcChannelRegistry, FakeInstanceSource]:
    settings = GrpcSettings.from_mapping(
        {
            "grpc": {
                "discovery": {
                    "services": {"example": service_name},
                    "debounce_ms": 0,
                    "close_grace_seconds": 0,
                    "reconcile_interval_seconds": 0,
                }
            }
        }
    )
    source = FakeInstanceSource([])
    return GrpcChannelRegistry(settings, source), source


async def test_registry_starts_and_changes_channel_on_reload(monkeypatch: pytest.MonkeyPatch) -> None:
    created_channels: list[Any] = []

    class FakeChannel:
        def __init__(self, target: str) -> None:
            self.target = target
            self.closed = False

        async def close(self) -> None:
            self.closed = True

    def create_fake_channel(target: str, options: Any, interceptors: Any) -> FakeChannel:
        channel = FakeChannel(target)
        created_channels.append(channel)
        return channel

    monkeypatch.setattr(
        "xxs_nacos_grpc.channel.grpc.aio.insecure_channel",
        create_fake_channel,
    )
    registry, source = create_registry()
    source.instances = [
        SimpleNamespace(ip="10.0.0.1", port=9000, enabled=True, healthy=True, weight=1)
    ]

    await registry.start()
    assert "llm-grpc" in source.subscriptions
    assert registry.snapshot()["example"] == ("10.0.0.1:9000",)

    source.instances = [
        SimpleNamespace(ip="10.0.0.2", port=9000, enabled=True, healthy=True, weight=1)
    ]
    await registry.reload(registry.settings)
    assert registry.snapshot()["example"] == ("10.0.0.2:9000",)

    await registry.close()
    assert created_channels[-1].closed is True
    assert created_channels[0].closed is True


async def test_registry_reload_recreates_alias_when_service_name_changes(
    monkeypatch: pytest.MonkeyPatch,
) -> None:
    class FakeChannel:
        def __init__(self, target: str) -> None:
            self.target = target
            self.closed = False

        async def close(self) -> None:
            self.closed = True

    monkeypatch.setattr(
        "xxs_nacos_grpc.channel.grpc.aio.insecure_channel",
        lambda target, options, interceptors: FakeChannel(target),
    )
    registry, source = create_registry("old-llm-grpc")
    source.instances = [
        SimpleNamespace(ip="10.0.0.1", port=9000, enabled=True, healthy=True, weight=1)
    ]
    await registry.start()

    next_settings = GrpcSettings.from_mapping(
        {
            "grpc": {
                "discovery": {
                    "services": {"example": "new-llm-grpc"},
                    "close_grace_seconds": 0,
                    "reconcile_interval_seconds": 0,
                }
            }
        }
    )
    await registry.reload(next_settings)

    assert "new-llm-grpc" in source.subscriptions
    assert registry.settings.discovery.services["example"] == "new-llm-grpc"
    await registry.close()
