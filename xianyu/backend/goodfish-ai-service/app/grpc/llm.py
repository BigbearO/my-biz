"""Model service gRPC client (llm.v1.LlmService.GetEnabledModel).

NOT_FOUND means no enabled model exists (50002, never retried by clients).
The response contains a plaintext api_key: it must never appear in logs,
exceptions or tracing attributes — only the provider and model name may be
logged.
"""

import logging
from collections.abc import Callable
from typing import Any

import grpc
from xxs_nacos_grpc.errors import ServiceUnavailableError

from ..constants import (
    GRPC_SERVICE_ALIAS_LLM,
    MESSAGE_NO_ENABLED_MODEL,
    MESSAGE_SERVICE_UNAVAILABLE,
)
from ..errors import (
    NoEnabledModelError,
    UnclassifiedServerError,
    UpstreamUnavailableError,
)
from .service_client import GrpcServiceClient

logger = logging.getLogger(__name__)


def create_llm_stub(channel: Any) -> Any:
    from app.pb.llm.v1 import llm_pb2_grpc

    return llm_pb2_grpc.LlmServiceStub(channel)


def load_llm_requests() -> Any:
    from app.pb.llm.v1 import llm_pb2

    return llm_pb2


def get_enabled_model_method(stub: Any) -> Callable[..., Any]:
    return stub.GetEnabledModel


class ModelClient:
    """Fetches the currently enabled model configuration."""

    def __init__(self, registry: Any, timeout: float | None = None) -> None:
        self._service_client = GrpcServiceClient(
            registry=registry,
            alias=GRPC_SERVICE_ALIAS_LLM,
            stub_factory=create_llm_stub,
        )
        self._timeout = timeout

    async def get_enabled_model(self) -> Any:
        requests = load_llm_requests()
        try:
            return await self._service_client.call(
                get_enabled_model_method,
                requests.GetEnabledModelRequest(),
                timeout=self._timeout,
            )
        except grpc.aio.AioRpcError as error:
            raise _map_llm_rpc_error(error) from error
        except ServiceUnavailableError as error:
            raise UpstreamUnavailableError(MESSAGE_SERVICE_UNAVAILABLE) from error


def _map_llm_rpc_error(error: grpc.RpcError) -> Exception:
    code = error.code()
    if code == grpc.StatusCode.NOT_FOUND:
        return NoEnabledModelError(MESSAGE_NO_ENABLED_MODEL)
    if code in (grpc.StatusCode.UNAVAILABLE, grpc.StatusCode.DEADLINE_EXCEEDED):
        return UpstreamUnavailableError(MESSAGE_SERVICE_UNAVAILABLE)
    # Never include error.details() here: transport errors may embed request
    # context. Log the status code only.
    logger.warning("Model service returned unmapped gRPC status: %s", code)
    return UnclassifiedServerError(f"model service status {code}")
