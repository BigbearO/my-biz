from collections.abc import Callable
from typing import Any

import grpc

from .context import current_user_context
from .errors import InvalidUserContextError

METADATA_USER_ID = "x-user-id"
METADATA_USER_INFO = "x-user-info"
METADATA_AUTHORIZATION = "authorization"
METADATA_CLIENT_ID = "x-client-id"


class UserContextClientInterceptor(grpc.aio.UnaryUnaryClientInterceptor):
    def __init__(self, client_id: str = "") -> None:
        self._client_id = client_id

    async def intercept_unary_unary(
        self,
        continuation: Callable[..., Any],
        client_call_details: grpc.aio.ClientCallDetails,
        request: Any,
    ) -> grpc.aio.Call:
        metadata = list(client_call_details.metadata or ())
        context = current_user_context.get()
        if context is not None:
            metadata.extend(_context_metadata(context))
        if self._client_id:
            metadata.append((METADATA_CLIENT_ID, self._client_id))
        next_call_details = client_call_details._replace(metadata=metadata)
        return await continuation(next_call_details, request)


def _context_metadata(context: Any) -> list[tuple[str, str]]:
    values = {
        METADATA_USER_ID: context.user_id,
        METADATA_USER_INFO: context.user_info,
        METADATA_AUTHORIZATION: context.authorization,
    }
    metadata: list[tuple[str, str]] = []
    for key, value in values.items():
        if not value:
            continue
        if not value.isascii():
            raise InvalidUserContextError(f"{key} metadata value must be ASCII")
        metadata.append((key, value))
    return metadata
