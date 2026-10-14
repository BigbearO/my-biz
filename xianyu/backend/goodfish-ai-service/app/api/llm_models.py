"""Debug endpoints that trigger real gRPC calls to the LLM manage service."""

import logging
from typing import Any

import grpc
from fastapi import HTTPException, Request, status
from google.protobuf.json_format import MessageToDict
from xxs_nacos_grpc.errors import ServiceUnavailableError

from ..constants import (
    DEFAULT_PAGE,
    DEFAULT_PAGE_SIZE,
    GRPC_CALL_FAILED_DETAIL,
    GRPC_SERVICE_UNAVAILABLE_DETAIL,
)
from ..grpc.llm_model import LlmModelClient

logger = logging.getLogger(__name__)

_RPC_ERROR_HTTP_STATUS = {
    grpc.StatusCode.INVALID_ARGUMENT: status.HTTP_400_BAD_REQUEST,
    grpc.StatusCode.NOT_FOUND: status.HTTP_404_NOT_FOUND,
    grpc.StatusCode.UNAVAILABLE: status.HTTP_503_SERVICE_UNAVAILABLE,
    grpc.StatusCode.DEADLINE_EXCEEDED: status.HTTP_504_GATEWAY_TIMEOUT,
}
_DEFAULT_RPC_ERROR_HTTP_STATUS = status.HTTP_502_BAD_GATEWAY


def _rpc_error_member(exc: grpc.RpcError, name: str) -> Any:
    """Read a grpc RpcError attribute that may be a value (grpc) or a callable (grpc.aio)."""
    value = getattr(exc, name)
    return value() if callable(value) else value


def _rpc_error_to_http(exc: grpc.RpcError) -> HTTPException:
    code = _rpc_error_member(exc, "code")
    http_status = _RPC_ERROR_HTTP_STATUS.get(code, _DEFAULT_RPC_ERROR_HTTP_STATUS)
    detail = f"{GRPC_CALL_FAILED_DETAIL}: {code} {_rpc_error_member(exc, 'details')}"
    logger.warning("%s", detail)
    return HTTPException(status_code=http_status, detail=detail)


def _service_unavailable_to_http(exc: ServiceUnavailableError) -> HTTPException:
    detail = f"{GRPC_SERVICE_UNAVAILABLE_DETAIL}: {exc}"
    logger.warning("%s", detail)
    return HTTPException(status_code=status.HTTP_503_SERVICE_UNAVAILABLE, detail=detail)


def _response_to_dict(message: Any) -> dict[str, Any]:
    return MessageToDict(message, preserving_proto_field_name=True)


async def list_llm_models(
    request: Request,
    page: int = DEFAULT_PAGE,
    page_size: int = DEFAULT_PAGE_SIZE,
) -> dict[str, Any]:
    client = LlmModelClient(request.app.state.grpc_registry)
    try:
        reply = await client.list_models(page=page, page_size=page_size)
    except grpc.RpcError as exc:
        raise _rpc_error_to_http(exc) from exc
    except ServiceUnavailableError as exc:
        raise _service_unavailable_to_http(exc) from exc
    return _response_to_dict(reply)


async def get_llm_model(request: Request, model_id: int) -> dict[str, Any]:
    client = LlmModelClient(request.app.state.grpc_registry)
    try:
        reply = await client.get(model_id)
    except grpc.RpcError as exc:
        raise _rpc_error_to_http(exc) from exc
    except ServiceUnavailableError as exc:
        raise _service_unavailable_to_http(exc) from exc
    return _response_to_dict(reply)
