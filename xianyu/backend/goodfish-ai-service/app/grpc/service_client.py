from collections.abc import Awaitable, Callable
from typing import Any, Generic, TypeVar

from xxs_nacos_grpc import GrpcChannelRegistry

TStub = TypeVar("TStub")
TResponse = TypeVar("TResponse")

GrpcMethod = Callable[[TStub], Callable[..., Awaitable[TResponse]]]


class GrpcServiceClient(Generic[TStub]):
    def __init__(
        self,
        registry: GrpcChannelRegistry,
        alias: str,
        stub_factory: Callable[[Any], TStub],
    ) -> None:
        self._registry = registry
        self._alias = alias
        self._stub_factory = stub_factory

    async def call(
        self,
        method_factory: GrpcMethod[TStub, Any],
        request: Any,
        timeout: float | None = None,
    ) -> Any:
        effective_timeout = timeout or self._registry.settings.client.default_timeout_seconds
        stub = self._registry.stub(self._alias, self._stub_factory)
        method = method_factory(stub)
        return await method(request, timeout=effective_timeout)
