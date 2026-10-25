from collections.abc import Awaitable, Callable
from contextvars import ContextVar, Token
from dataclasses import dataclass
from typing import Any


HEADER_USER_ID = "x-user-id"
HEADER_USER_INFO = "x-user-info"
HEADER_AUTHORIZATION = "authorization"


@dataclass(frozen=True)
class UserContext:
    user_id: str = ""
    user_info: str = ""
    authorization: str = ""


current_user_context: ContextVar[UserContext | None] = ContextVar(
    "current_user_context",
    default=None,
)


def set_user_context(context: UserContext) -> Token[UserContext | None]:
    return current_user_context.set(context)


def reset_user_context(token: Token[UserContext | None]) -> None:
    current_user_context.reset(token)


class UserContextMiddleware:
    def __init__(self, app: Callable[..., Awaitable[None]]) -> None:
        self.app = app

    async def __call__(
        self,
        scope: dict[str, Any],
        receive: Callable[[], Awaitable[dict[str, Any]]],
        send: Callable[[dict[str, Any]], Awaitable[None]],
    ) -> None:
        if scope.get("type") != "http":
            await self.app(scope, receive, send)
            return

        headers = {
            key.decode("latin-1").lower(): value.decode("latin-1")
            for key, value in scope.get("headers", [])
        }
        context = UserContext(
            user_id=headers.get(HEADER_USER_ID, ""),
            user_info=headers.get(HEADER_USER_INFO, ""),
            authorization=headers.get(HEADER_AUTHORIZATION, ""),
        )
        token = set_user_context(context)
        try:
            await self.app(scope, receive, send)
        finally:
            reset_user_context(token)
