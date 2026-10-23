"""POST /api/ai/reply — the core reply endpoint.

Business errors are returned as HTTP 200 with ``{code, message, data}``;
only transport-level problems (validation, unhandled exceptions) use HTTP
status codes, mirroring the gateway contract the client already expects.
"""

import logging
from typing import Any

from fastapi import Request
from fastapi.exceptions import RequestValidationError
from fastapi.responses import JSONResponse

from ..constants import (
    API_AI_REPLY_ROUTE,
    BUSINESS_CODE_INVALID_ARGUMENT,
    BUSINESS_CODE_SUCCESS,
    MESSAGE_INVALID_ARGUMENT,
    MESSAGE_SUCCESS,
)
from ..errors import ReplyBusinessError
from ..schemas.reply import ReplyRequest, ReplyResponse
from ..services.reply_service import ReplyService

logger = logging.getLogger(__name__)

HTTP_STATUS_VALIDATION_ERROR = 422


async def reply(request: Request, payload: ReplyRequest) -> JSONResponse:
    service: ReplyService = request.app.state.reply_service
    try:
        data = await service.reply(
            request_id=payload.request_id,
            goodfish_item_id=payload.goodfish_item_id,
            buyer_id=payload.buyer_id,
            goodfish_account_id=payload.goodfish_account_id,
            message=payload.message,
            image_urls=payload.image_urls,
        )
    except ReplyBusinessError as error:
        logger.warning(
            "Reply rejected: request_id=%s code=%s",
            payload.request_id,
            error.code,
        )
        return JSONResponse(
            status_code=200,
            content={"code": error.code, "message": error.message, "data": None},
        )
    validated = ReplyResponse(
        code=BUSINESS_CODE_SUCCESS,
        message=MESSAGE_SUCCESS,
        data=data,
    )
    return JSONResponse(status_code=200, content=_validated_content(validated))


def _validated_content(validated: ReplyResponse) -> dict[str, Any]:
    return validated.model_dump()


async def reply_validation_error_handler(
    request: Request,
    exception: RequestValidationError,
) -> JSONResponse:
    """Reply payload errors map to business code 40001 (a client bug).

    Other routes keep FastAPI's standard 422 response so the existing
    /api/llm-models debug endpoints behave exactly as before.
    """
    if request.url.path != API_AI_REPLY_ROUTE:
        # Keep FastAPI's standard 422 shape; only serialize primitives because
        # validator contexts may hold non-serializable objects.
        detail = [
            {"loc": list(error.get("loc", ())), "msg": error.get("msg", ""), "type": error.get("type", "")}
            for error in exception.errors()
        ]
        return JSONResponse(
            status_code=HTTP_STATUS_VALIDATION_ERROR,
            content={"detail": detail},
        )
    return JSONResponse(
        status_code=200,
        content={
            "code": BUSINESS_CODE_INVALID_ARGUMENT,
            "message": MESSAGE_INVALID_ARGUMENT,
            "data": None,
        },
    )
