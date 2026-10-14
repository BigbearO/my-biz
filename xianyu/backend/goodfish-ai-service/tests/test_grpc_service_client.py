from types import SimpleNamespace
from typing import Any

import pytest

from app.grpc.service_client import GrpcServiceClient


class FakeRegistry:
    def __init__(self, stub: Any) -> None:
        self._stub = stub
        self.settings = SimpleNamespace(
            client=SimpleNamespace(default_timeout_seconds=3),
        )

    def stub(self, alias: str, stub_factory: Any) -> Any:
        self.alias = alias
        self.stub_factory = stub_factory
        return self._stub


@pytest.mark.asyncio
async def test_service_client_uses_registry_stub_and_default_timeout() -> None:
    calls: list[tuple[Any, float]] = []

    async def get_model(request: Any, timeout: float) -> Any:
        calls.append((request, timeout))
        return SimpleNamespace(id=request.id)

    stub = SimpleNamespace(Get=get_model)
    registry = FakeRegistry(stub)
    client = GrpcServiceClient(registry, "llm-model", lambda channel: stub)

    response = await client.call(lambda service: service.Get, SimpleNamespace(id=1))

    assert response.id == 1
    assert registry.alias == "llm-model"
    assert calls == [(SimpleNamespace(id=1), 3)]


@pytest.mark.asyncio
async def test_service_client_allows_call_level_timeout() -> None:
    calls: list[float] = []

    async def get_model(request: Any, timeout: float) -> Any:
        calls.append(timeout)
        return object()

    stub = SimpleNamespace(Get=get_model)
    client = GrpcServiceClient(FakeRegistry(stub), "llm-model", lambda channel: stub)

    await client.call(
        lambda service: service.Get,
        SimpleNamespace(id=1),
        timeout=0.5,
    )

    assert calls == [0.5]
