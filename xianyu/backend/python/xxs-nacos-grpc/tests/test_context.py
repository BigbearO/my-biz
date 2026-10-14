from collections.abc import Awaitable, Callable
from typing import Any

import grpc

from xxs_nacos_grpc.context import (
    UserContext,
    UserContextMiddleware,
    current_user_context,
    reset_user_context,
    set_user_context,
)
from xxs_nacos_grpc.errors import InvalidUserContextError
from xxs_nacos_grpc.interceptor import UserContextClientInterceptor


async def test_middleware_moves_headers_to_context() -> None:
    observed_contexts: list[UserContext | None] = []

    async def app(
        scope: dict[str, Any],
        receive: Callable[[], Awaitable[dict[str, Any]]],
        send: Callable[[dict[str, Any]], Awaitable[None]],
    ) -> None:
        observed_contexts.append(current_user_context.get())

    middleware = UserContextMiddleware(app)
    scope = {
        "type": "http",
        "headers": [
            (b"x-user-id", b"10001"),
            (b"X-User-Info", b"alice"),
            (b"Authorization", b"Bearer token"),
        ],
    }

    await middleware(scope, lambda: {}, lambda message: None)

    assert observed_contexts == [
        UserContext(user_id="10001", user_info="alice", authorization="Bearer token")
    ]
    assert current_user_context.get() is None


async def test_interceptor_adds_context_and_client_id() -> None:
    calls: list[tuple[Any, Any]] = []

    async def continuation(call_details: Any, request: Any) -> object:
        calls.append((call_details, request))
        return object()

    async def intercept(request: Any) -> object:
        token = set_user_context(UserContext(user_id="10001", user_info="alice"))
        try:
            return await UserContextClientInterceptor("ai-service").intercept_unary_unary(
                continuation,
                grpc.aio.ClientCallDetails(
                    method=b"/example.v1.ExampleService/Get",
                    timeout=3,
                    metadata=None,
                    credentials=None,
                    wait_for_ready=None,
                ),
                request,
            )
        finally:
            reset_user_context(token)

    await intercept(object())
    call_details, _ = calls[0]
    assert dict(call_details.metadata) == {
        "x-user-id": "10001",
        "x-user-info": "alice",
        "x-client-id": "ai-service",
    }


async def test_interceptor_rejects_non_ascii_user_info() -> None:
    async def continuation(call_details: Any, request: Any) -> object:
        return object()

    token = set_user_context(UserContext(user_info="中文"))
    try:
        try:
            await UserContextClientInterceptor().intercept_unary_unary(
                continuation,
                grpc.aio.ClientCallDetails(
                    method=b"/example.v1.ExampleService/Get",
                    timeout=3,
                    metadata=None,
                    credentials=None,
                    wait_for_ready=None,
                ),
                object(),
            )
        except InvalidUserContextError:
            return
        raise AssertionError("expected InvalidUserContextError")
    finally:
        reset_user_context(token)
