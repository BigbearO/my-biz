from collections.abc import Callable
from typing import Any

from ..constants import GRPC_SERVICE_ALIAS_LLM_MODEL
from .service_client import GrpcServiceClient


def create_llm_model_stub(channel: Any) -> Any:
    from admin.service.v1 import llm_model_pb2_grpc

    return llm_model_pb2_grpc.LlmModelStub(channel)


def load_llm_model_requests() -> Any:
    from admin.service.v1 import llm_model_pb2

    return llm_model_pb2


def get_llm_model_method(stub: Any) -> Callable[..., Any]:
    return stub.Get


def list_llm_models_method(stub: Any) -> Callable[..., Any]:
    return stub.List


def create_llm_model_method(stub: Any) -> Callable[..., Any]:
    return stub.Create


def update_llm_model_method(stub: Any) -> Callable[..., Any]:
    return stub.Update


def delete_llm_model_method(stub: Any) -> Callable[..., Any]:
    return stub.Delete


def enable_llm_model_method(stub: Any) -> Callable[..., Any]:
    return stub.Enable


class LlmModelClient:
    def __init__(self, registry: Any) -> None:
        self._service_client = GrpcServiceClient(
            registry=registry,
            alias=GRPC_SERVICE_ALIAS_LLM_MODEL,
            stub_factory=create_llm_model_stub,
        )

    async def get(self, model_id: int, timeout: float | None = None) -> Any:
        requests = load_llm_model_requests()
        return await self._service_client.call(
            get_llm_model_method,
            requests.GetLlmModelRequest(id=model_id),
            timeout=timeout,
        )

    async def list_models(
        self,
        page: int,
        page_size: int,
        provider_id: int = 0,
        display_name: str = "",
        status: int = 0,
        timeout: float | None = None,
    ) -> Any:
        requests = load_llm_model_requests()
        return await self._service_client.call(
            list_llm_models_method,
            requests.ListLlmModelRequest(
                page=page,
                page_size=page_size,
                provider_id=provider_id,
                display_name=display_name,
                status=status,
            ),
            timeout=timeout,
        )

    async def create(
        self,
        provider_id: int,
        display_name: str,
        model_name: str,
        temperature: float,
        max_tokens: int,
        status: int,
        timeout: float | None = None,
    ) -> Any:
        requests = load_llm_model_requests()
        return await self._service_client.call(
            create_llm_model_method,
            requests.CreateLlmModelRequest(
                provider_id=provider_id,
                display_name=display_name,
                model_name=model_name,
                temperature=temperature,
                max_tokens=max_tokens,
                status=status,
            ),
            timeout=timeout,
        )

    async def update(
        self,
        model_id: int,
        provider_id: int,
        display_name: str,
        model_name: str,
        temperature: float,
        max_tokens: int,
        status: int,
        timeout: float | None = None,
    ) -> Any:
        requests = load_llm_model_requests()
        return await self._service_client.call(
            update_llm_model_method,
            requests.UpdateLlmModelRequest(
                id=model_id,
                provider_id=provider_id,
                display_name=display_name,
                model_name=model_name,
                temperature=temperature,
                max_tokens=max_tokens,
                status=status,
            ),
            timeout=timeout,
        )

    async def delete(self, model_id: int, timeout: float | None = None) -> Any:
        requests = load_llm_model_requests()
        return await self._service_client.call(
            delete_llm_model_method,
            requests.DeleteLlmModelRequest(id=model_id),
            timeout=timeout,
        )

    async def enable(self, model_id: int, timeout: float | None = None) -> Any:
        requests = load_llm_model_requests()
        return await self._service_client.call(
            enable_llm_model_method,
            requests.EnableLlmModelRequest(id=model_id),
            timeout=timeout,
        )
