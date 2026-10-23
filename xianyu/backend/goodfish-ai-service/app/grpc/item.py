"""Item service gRPC client (item.v1.ItemService.GetItemRuntimeConfig).

gRPC status codes map to domain errors per the reply orchestration design:
NOT_FOUND -> 40401, DEADLINE_EXCEEDED -> retry once then 50301,
UNAVAILABLE -> 50301, INVALID_ARGUMENT -> 40001, anything else -> 50001.
"""

import logging
from collections.abc import Callable
from typing import Any

import grpc
from xxs_nacos_grpc.errors import ServiceUnavailableError

from ..constants import (
    GRPC_ITEM_DEADLINE_RETRY_LIMIT,
    GRPC_SERVICE_ALIAS_ITEM,
    MESSAGE_INVALID_ARGUMENT,
    MESSAGE_ITEM_NOT_FOUND,
    MESSAGE_SERVICE_UNAVAILABLE,
)
from ..errors import (
    InvalidArgumentError,
    ItemNotFoundError,
    UnclassifiedServerError,
    UpstreamUnavailableError,
)
from .service_client import GrpcServiceClient

logger = logging.getLogger(__name__)


def create_item_stub(channel: Any) -> Any:
    from app.pb.item.v1 import item_pb2_grpc

    return item_pb2_grpc.ItemServiceStub(channel)


def load_item_requests() -> Any:
    from app.pb.item.v1 import item_pb2

    return item_pb2


def get_item_runtime_config_method(stub: Any) -> Callable[..., Any]:
    return stub.GetItemRuntimeConfig


class ItemClient:
    """Thin wrapper adding error mapping on top of GrpcServiceClient."""

    def __init__(self, registry: Any, timeout: float | None = None) -> None:
        self._service_client = GrpcServiceClient(
            registry=registry,
            alias=GRPC_SERVICE_ALIAS_ITEM,
            stub_factory=create_item_stub,
        )
        self._timeout = timeout

    async def get_item_runtime_config(
        self,
        account_id: str,
        item_id: str,
    ) -> Any:
        """Fetch item runtime config; DEADLINE_EXCEEDED is retried once."""
        requests = load_item_requests()
        request = requests.GetItemRuntimeConfigRequest(
            account_id=account_id,
            item_id=item_id,
        )
        last_error: grpc.RpcError | None = None
        for attempt in range(GRPC_ITEM_DEADLINE_RETRY_LIMIT + 1):
            try:
                return await self._service_client.call(
                    get_item_runtime_config_method,
                    request,
                    timeout=self._timeout,
                )
            except grpc.aio.AioRpcError as error:
                last_error = error
                if (
                    error.code() == grpc.StatusCode.DEADLINE_EXCEEDED
                    and attempt < GRPC_ITEM_DEADLINE_RETRY_LIMIT
                ):
                    logger.warning(
                        "Item service deadline exceeded (attempt %s), retrying once",
                        attempt + 1,
                    )
                    continue
                raise _map_item_rpc_error(error) from error
            except ServiceUnavailableError as error:
                # No healthy instance discovered for the alias.
                raise UpstreamUnavailableError(MESSAGE_SERVICE_UNAVAILABLE) from error
        raise _map_item_rpc_error(last_error)  # pragma: no cover - loop always returns or raises


def _map_item_rpc_error(error: grpc.RpcError) -> Exception:
    code = error.code()
    if code == grpc.StatusCode.NOT_FOUND:
        return ItemNotFoundError(MESSAGE_ITEM_NOT_FOUND)
    if code in (grpc.StatusCode.UNAVAILABLE, grpc.StatusCode.DEADLINE_EXCEEDED):
        return UpstreamUnavailableError(MESSAGE_SERVICE_UNAVAILABLE)
    if code == grpc.StatusCode.INVALID_ARGUMENT:
        return InvalidArgumentError(MESSAGE_INVALID_ARGUMENT)
    logger.warning("Item service returned unmapped gRPC status: %s", code)
    return UnclassifiedServerError(f"item service status {code}")


class FakeItemClient:
    """Development-only stand-in used when the Java item service is absent.

    Enabled through ``item.fake_enabled`` in configuration; must never be
    turned on outside local development.
    """

    def __init__(self, defaults: dict[str, Any] | None = None) -> None:
        self._defaults = defaults or {}

    async def get_item_runtime_config(
        self,
        account_id: str,
        item_id: str,
    ) -> Any:
        requests = load_item_requests()
        prompt = self._defaults.get(
            "prompt",
            {
                "system_prompt": "你是一位专业的闲鱼客服AI助手。",
                "price_prompt": "",
                "tech_prompt": "",
                "default_prompt": "",
                "max_reply_length": 50,
            },
        )
        return requests.ItemRuntimeConfig(
            item_id=item_id,
            title=self._defaults.get("title", f"测试商品-{item_id}"),
            description=self._defaults.get("description", "开发联调用商品描述"),
            price=self._defaults.get("price", "99.00"),
            stock_status=self._defaults.get("stock_status", "on_sale"),
            image_url=self._defaults.get("image_url", ""),
            prompt=requests.PromptConfig(**prompt),
        )
