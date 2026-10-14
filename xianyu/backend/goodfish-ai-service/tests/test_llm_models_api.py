from types import SimpleNamespace
from typing import Any

import grpc
import pytest
from fastapi import HTTPException
from xxs_nacos_grpc.errors import ServiceUnavailableError

from admin.service.v1 import llm_model_pb2
from app.api import llm_models


class FakeRpcError(grpc.RpcError):
    def __init__(self, code: grpc.StatusCode, details: str) -> None:
        self._code = code
        self._details = details

    def code(self) -> grpc.StatusCode:
        return self._code

    def details(self) -> str:
        return self._details


class FakeLlmModelClient:
    def __init__(self, registry: Any) -> None:
        self.registry = registry
        self.reply: Any = None
        self.error: Exception | None = None
        self.calls: list[tuple[str, tuple[Any, ...]]] = []

    async def list_models(self, page: int, page_size: int) -> Any:
        self.calls.append(("list", (page, page_size)))
        return self._next_result()

    async def get(self, model_id: int) -> Any:
        self.calls.append(("get", (model_id,)))
        return self._next_result()

    def _next_result(self) -> Any:
        if self.error is not None:
            raise self.error
        return self.reply


def make_fake_client(monkeypatch: pytest.MonkeyPatch, **kwargs: Any) -> FakeLlmModelClient:
    fake_client = FakeLlmModelClient(registry=object())
    fake_client.reply = kwargs.get("reply")
    fake_client.error = kwargs.get("error")
    monkeypatch.setattr(llm_models, "LlmModelClient", lambda registry: fake_client)
    return fake_client


def make_fake_request() -> SimpleNamespace:
    return SimpleNamespace(app=SimpleNamespace(state=SimpleNamespace(grpc_registry=object())))


@pytest.mark.asyncio
async def test_list_llm_models_returns_proto_dict(
    monkeypatch: pytest.MonkeyPatch,
) -> None:
    fake_client = make_fake_client(
        monkeypatch,
        reply=llm_model_pb2.ListLlmModelReply(
            list=[
                llm_model_pb2.LlmModelInfo(
                    id=1,
                    provider_id=2,
                    provider_name="openai",
                    display_name="GPT",
                    model_name="gpt-4",
                    temperature=0.7,
                    max_tokens=2048,
                    status=1,
                )
            ],
            total=1,
        ),
    )

    result = await llm_models.list_llm_models(make_fake_request(), page=2, page_size=5)

    assert result == {
        "list": [
            {
                "id": "1",
                "provider_id": "2",
                "provider_name": "openai",
                "display_name": "GPT",
                "model_name": "gpt-4",
                "temperature": 0.7,
                "max_tokens": 2048,
                "status": 1,
            }
        ],
        "total": "1",
    }
    assert fake_client.calls == [("list", (2, 5))]


@pytest.mark.asyncio
async def test_get_llm_model_returns_proto_dict(
    monkeypatch: pytest.MonkeyPatch,
) -> None:
    fake_client = make_fake_client(
        monkeypatch,
        reply=llm_model_pb2.LlmModelInfo(id=7, display_name="GPT", model_name="gpt-4"),
    )

    result = await llm_models.get_llm_model(make_fake_request(), 7)

    # protobuf 5.x MessageToDict omits fields at their default values.
    assert result == {
        "id": "7",
        "display_name": "GPT",
        "model_name": "gpt-4",
    }
    assert fake_client.calls == [("get", (7,))]


@pytest.mark.asyncio
async def test_list_llm_models_maps_not_found(monkeypatch: pytest.MonkeyPatch) -> None:
    make_fake_client(
        monkeypatch,
        error=FakeRpcError(grpc.StatusCode.NOT_FOUND, "模型不存在"),
    )

    with pytest.raises(HTTPException) as exc_info:
        await llm_models.get_llm_model(make_fake_request(), 99)

    assert exc_info.value.status_code == 404
    assert "模型不存在" in exc_info.value.detail


@pytest.mark.asyncio
async def test_list_llm_models_maps_unavailable(monkeypatch: pytest.MonkeyPatch) -> None:
    make_fake_client(
        monkeypatch,
        error=FakeRpcError(grpc.StatusCode.UNAVAILABLE, "no instances"),
    )

    with pytest.raises(HTTPException) as exc_info:
        await llm_models.list_llm_models(make_fake_request())

    assert exc_info.value.status_code == 503


@pytest.mark.asyncio
async def test_list_llm_models_maps_unknown_rpc_error(monkeypatch: pytest.MonkeyPatch) -> None:
    make_fake_client(
        monkeypatch,
        error=FakeRpcError(grpc.StatusCode.INTERNAL, "boom"),
    )

    with pytest.raises(HTTPException) as exc_info:
        await llm_models.list_llm_models(make_fake_request())

    assert exc_info.value.status_code == 502


@pytest.mark.asyncio
async def test_list_llm_models_maps_service_unavailable(
    monkeypatch: pytest.MonkeyPatch,
) -> None:
    make_fake_client(
        monkeypatch,
        error=ServiceUnavailableError("Unknown gRPC service alias: llm-model"),
    )

    with pytest.raises(HTTPException) as exc_info:
        await llm_models.list_llm_models(make_fake_request())

    assert exc_info.value.status_code == 503
