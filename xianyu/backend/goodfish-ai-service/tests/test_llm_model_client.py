from types import SimpleNamespace
from typing import Any

import pytest

from app.grpc import llm_model
from app.grpc.llm_model import LlmModelClient


class FakeRequestType:
    def __init__(self, **values: Any) -> None:
        self.values = values


class FakeRequests:
    GetLlmModelRequest = FakeRequestType
    ListLlmModelRequest = FakeRequestType
    CreateLlmModelRequest = FakeRequestType
    UpdateLlmModelRequest = FakeRequestType
    DeleteLlmModelRequest = FakeRequestType
    EnableLlmModelRequest = FakeRequestType


class FakeServiceClient:
    def __init__(self) -> None:
        self.calls: list[tuple[Any, Any]] = []

    async def call(
        self,
        method_factory: Any,
        request: Any,
        timeout: float | None = None,
    ) -> Any:
        stub_names = SimpleNamespace(
            Get="get",
            List="list",
            Create="create",
            Update="update",
            Delete="delete",
            Enable="enable",
        )
        self.calls.append((method_factory(stub_names), request))
        return SimpleNamespace(request=request, timeout=timeout)


@pytest.fixture()
def fake_requests(monkeypatch: pytest.MonkeyPatch) -> FakeRequests:
    requests = FakeRequests()
    monkeypatch.setattr(llm_model, "load_llm_model_requests", lambda: requests)
    return requests


@pytest.mark.asyncio
async def test_llm_model_client_get_builds_request(
    monkeypatch: pytest.MonkeyPatch,
    fake_requests: FakeRequests,
) -> None:
    service_client = FakeServiceClient()
    monkeypatch.setattr(
        llm_model,
        "GrpcServiceClient",
        lambda registry, alias, stub_factory: service_client,
    )
    client = LlmModelClient(registry=object())

    response = await client.get(1, timeout=0.5)

    assert response.request.values == {"id": 1}
    assert response.timeout == 0.5
    assert service_client.calls[0][0] == "get"


@pytest.mark.asyncio
async def test_llm_model_client_list_builds_request(
    monkeypatch: pytest.MonkeyPatch,
    fake_requests: FakeRequests,
) -> None:
    service_client = FakeServiceClient()
    monkeypatch.setattr(
        llm_model,
        "GrpcServiceClient",
        lambda registry, alias, stub_factory: service_client,
    )
    client = LlmModelClient(registry=object())

    response = await client.list_models(
        page=1,
        page_size=20,
        provider_id=2,
        display_name="qwen",
        status=1,
    )

    assert response.request.values == {
        "page": 1,
        "page_size": 20,
        "provider_id": 2,
        "display_name": "qwen",
        "status": 1,
    }
    assert service_client.calls[0][0] == "list"
