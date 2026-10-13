import os
from typing import Any

import pytest

os.environ.setdefault("NACOS_SERVER_ADDR", "nacos.example.com:8848")
os.environ.setdefault("NACOS_NAMESPACE_ID", "existing-namespace")
os.environ.setdefault("NACOS_SERVICE_NAME", "custom-service")
os.environ.setdefault("NACOS_DATA_ID", "")

from app.constants import SERVICE_VERSION
from app.main import create_app


class FakeLifespanManager:
    def __init__(self) -> None:
        self.events: list[str] = []
        self.include_config = False

    async def start(self) -> None:
        self.events.append("start")

    @property
    def config_enabled(self) -> bool:
        return self.include_config

    async def get_config(self) -> str:
        return "service:\n  name: remote"

    async def watch_config(self, listener: Any) -> None:
        self.events.append("watch")

    async def register_http_instance(self) -> None:
        self.events.append("register")

    async def deregister_all(self) -> None:
        self.events.append("deregister")

    async def shutdown(self) -> None:
        self.events.append("shutdown")


@pytest.mark.asyncio
async def test_lifespan_registers_after_config_and_deregisters_first(
    monkeypatch: pytest.MonkeyPatch,
) -> None:
    fake_manager = FakeLifespanManager()
    created_managers: list[FakeLifespanManager] = []

    def create_manager(bootstrap: Any, service_ip: str) -> FakeLifespanManager:
        created_managers.append(fake_manager)
        return fake_manager

    monkeypatch.setattr("app.main.load_local_config", lambda path: {"service": {"name": "local"}})
    monkeypatch.setattr("app.main.NacosManager", create_manager)
    monkeypatch.setattr("app.main.resolve_register_ip", lambda override: "192.168.1.10")

    app = create_app()

    async with app.router.lifespan_context(app):
        assert fake_manager.events == ["start", "register"]
        assert app.state.runtime_config_store.config == {"service": {"name": "local"}}
        assert app.state.service_version == SERVICE_VERSION

    assert fake_manager.events == ["start", "register", "deregister", "shutdown"]
    assert created_managers == [fake_manager]
