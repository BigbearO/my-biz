from types import SimpleNamespace
from typing import Any

import pytest

from app.bootstrap import BootstrapSettings
from app.errors import NacosConnectionError, RegisterIpError
from app.nacos.manager import NacosManager, resolve_register_ip


def create_settings(**overrides: Any) -> BootstrapSettings:
    values: dict[str, Any] = {
        "nacos_server_addr": "nacos.example.com:8848",
        "nacos_namespace_id": "existing-namespace",
        "nacos_service_name": "custom-service",
        "nacos_data_id": "custom-config.yaml",
    }
    values.update(overrides)
    return BootstrapSettings(_env_file=None, **values)


class FakeNamingClient:
    def __init__(self) -> None:
        self.registered: list[Any] = []
        self.deregistered: list[Any] = []
        self.shutdown_count = 0

    async def register_instance(self, request: Any) -> None:
        self.registered.append(request)

    async def deregister_instance(self, request: Any) -> None:
        if request.port == 0:
            raise RuntimeError("deregister failed")
        self.deregistered.append(request)

    async def shutdown(self) -> None:
        self.shutdown_count += 1


class FakeConfigClient:
    def __init__(self) -> None:
        self.listeners: list[tuple[str, str, Any]] = []
        self.shutdown_count = 0

    async def get_config(self, request: Any) -> str:
        return "service:\n  name: remote"

    async def add_listener(self, data_id: str, group: str, listener: Any) -> None:
        self.listeners.append((data_id, group, listener))

    async def shutdown(self) -> None:
        self.shutdown_count += 1


async def fake_client_factory(client_config: Any, include_config: bool) -> tuple[Any, Any | None]:
    return FakeNamingClient(), FakeConfigClient() if include_config else None


@pytest.mark.asyncio
async def test_manager_uses_config_client_only_when_data_id_exists() -> None:
    settings = create_settings(nacos_data_id="")
    manager = NacosManager(
        settings,
        service_ip="192.168.1.10",
        client_factory=fake_client_factory,
    )

    await manager.start()

    assert manager.config_enabled is False
    assert isinstance(manager._naming_client, FakeNamingClient)
    assert manager._config_client is None
    await manager.shutdown()


@pytest.mark.asyncio
async def test_manager_loads_config_watches_and_registers() -> None:
    settings = create_settings()
    manager = NacosManager(
        settings,
        service_ip="192.168.1.10",
        client_factory=fake_client_factory,
    )

    await manager.start()
    content = await manager.get_config()
    await manager.watch_config(lambda *args: None)
    await manager.register_http_instance()
    naming_client = manager._naming_client

    assert content == "service:\n  name: remote"
    assert len(naming_client.registered) == 1
    registered = naming_client.registered[0]
    assert registered.service_name == settings.nacos_service_name
    assert registered.group_name == settings.nacos_group
    assert registered.cluster_name == settings.nacos_cluster_name
    assert registered.ip == "192.168.1.10"
    assert registered.port == settings.server_port
    assert registered.ephemeral is True
    assert registered.metadata["protocol"] == "http"

    config_client = manager._config_client
    assert len(config_client.listeners) == 1
    await manager.shutdown()
    assert naming_client.shutdown_count == 1
    assert config_client.shutdown_count == 1


@pytest.mark.asyncio
async def test_manager_deregistration_failure_does_not_block_shutdown() -> None:
    settings = create_settings(nacos_data_id="")
    naming_client = FakeNamingClient()

    async def client_factory(client_config: Any, include_config: bool) -> tuple[Any, Any | None]:
        return naming_client, None

    manager = NacosManager(settings, service_ip="192.168.1.10", client_factory=client_factory)
    await manager.start()
    await manager.register_http_instance()
    naming_client.registered[0].port = 0

    await manager.deregister_all()

    assert naming_client.deregistered == []
    assert manager._registered_params == []


@pytest.mark.asyncio
async def test_manager_retries_nacos_connection(monkeypatch: pytest.MonkeyPatch) -> None:
    attempts = 0

    async def client_factory(client_config: Any, include_config: bool) -> tuple[Any, Any | None]:
        nonlocal attempts
        attempts += 1
        if attempts < 3:
            raise ConnectionError("nacos unavailable")
        return FakeNamingClient(), None

    async def fake_sleep(delay: float) -> None:
        return None

    monkeypatch.setattr("app.nacos.manager.asyncio", SimpleNamespace(sleep=fake_sleep))
    settings = create_settings(nacos_data_id="")
    manager = NacosManager(settings, service_ip="192.168.1.10", client_factory=client_factory)

    await manager.start()

    assert attempts == 3


@pytest.mark.asyncio
async def test_manager_raises_after_connection_retries(monkeypatch: pytest.MonkeyPatch) -> None:
    async def client_factory(client_config: Any, include_config: bool) -> tuple[Any, Any | None]:
        raise ConnectionError("nacos unavailable")

    async def fake_sleep(delay: float) -> None:
        return None

    monkeypatch.setattr("app.nacos.manager.asyncio", SimpleNamespace(sleep=fake_sleep))
    settings = create_settings(nacos_data_id="")
    manager = NacosManager(settings, service_ip="192.168.1.10", client_factory=client_factory)

    with pytest.raises(NacosConnectionError):
        await manager.start()


def test_resolve_register_ip_uses_explicit_ip() -> None:
    assert resolve_register_ip("192.168.1.10 ") == "192.168.1.10"


def test_resolve_register_ip_rejects_unspecified_address() -> None:
    with pytest.raises(RegisterIpError):
        resolve_register_ip("0.0.0.0")
