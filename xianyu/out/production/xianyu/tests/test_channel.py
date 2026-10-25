from types import SimpleNamespace

import pytest

from xxs_nacos_grpc import ManagedChannel
from xxs_nacos_grpc.config import GrpcSettings
from xxs_nacos_grpc.errors import ServiceUnavailableError


class FakeChannel:
    def __init__(self, target: str) -> None:
        self.target = target
        self.closed = False

    async def close(self) -> None:
        self.closed = True


def create_channel(monkeypatch: pytest.MonkeyPatch) -> ManagedChannel[FakeChannel]:
    monkeypatch.setattr(
        "xxs_nacos_grpc.channel.grpc.aio.insecure_channel",
        lambda target, options, interceptors: FakeChannel(target),
    )
    settings = GrpcSettings.from_mapping(
        {"grpc": {"discovery": {"services": {"llm": "llm-grpc"}, "debounce_ms": 0}}}
    )
    return ManagedChannel(
        alias="llm",
        service_name="llm-grpc",
        discovery=settings.discovery,
        channel_settings=settings.channel,
    )


def instance(ip: str) -> SimpleNamespace:
    return SimpleNamespace(ip=ip, port=9000, enabled=True, healthy=True, weight=1)


async def test_channel_builds_round_robin_target_and_stub(monkeypatch: pytest.MonkeyPatch) -> None:
    managed_channel = create_channel(monkeypatch)

    await managed_channel.refresh_instances([instance("10.0.0.2"), instance("10.0.0.1")])
    stub = managed_channel.stub(lambda channel: SimpleNamespace(channel=channel))

    assert managed_channel.channel().target == "ipv4:10.0.0.1:9000,10.0.0.2:9000"
    assert stub.channel is managed_channel.channel()

    await managed_channel.close()
    assert managed_channel.channel().closed is True


async def test_empty_instance_list_keeps_previous_channel(monkeypatch: pytest.MonkeyPatch) -> None:
    managed_channel = create_channel(monkeypatch)

    await managed_channel.refresh_instances([instance("10.0.0.1")])
    previous_channel = managed_channel.channel()
    await managed_channel.refresh_instances([])

    assert managed_channel.channel() is previous_channel
    await managed_channel.close()


async def test_channel_without_instances_rejects_stub(monkeypatch: pytest.MonkeyPatch) -> None:
    managed_channel = create_channel(monkeypatch)

    with pytest.raises(ServiceUnavailableError):
        managed_channel.channel()
