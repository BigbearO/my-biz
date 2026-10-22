"""HTTP client for the cloud gateway used by the local service."""

import asyncio
import random
from typing import Awaitable, Callable, Optional

import aiohttp
from loguru import logger

from xianyu_client.core.constants import (
    CLOUD_API_PATH_ACCOUNT_REPORT,
    CLOUD_API_PATH_AUTH_ACCESS,
    CLOUD_AUTHORIZATION_HEADER,
    CLOUD_AUTHORIZATION_PREFIX,
    CLOUD_CLIENT_ID_HEADER,
    CLOUD_HTTP_STATUS_FORBIDDEN,
    CLOUD_HTTP_STATUS_SERVER_ERROR_MIN,
    CLOUD_HTTP_STATUS_UNAUTHORIZED,
    CLOUD_REPORT_LOG_MESSAGE,
    CLOUD_REQUEST_TIMEOUT_SECONDS,
    CLOUD_RETRY_INTERVAL_MAX_SECONDS,
    CLOUD_RETRY_INTERVAL_MIN_SECONDS,
    CLOUD_RETRY_LIMIT,
    CLOUD_SUCCESS_CODE,
    CONTENT_TYPE_JSON_HEADER,
)
from xianyu_client.core.errors import (
    CloudForbiddenError,
    CloudReportError,
    CloudUnauthorizedError,
    CloudUnavailableError,
    NotLoggedInError,
)

TokenProvider = Callable[[], Awaitable[tuple[str, str]]]


class CloudClient:
    """Single outbound boundary for every cloud call of the local service."""

    def __init__(self, base_url: str, token_provider: TokenProvider) -> None:
        """Create a client bound to one gateway base URL.

        Args:
            base_url: Gateway origin such as `http://localhost:8080`.
            token_provider: Async callable returning the stored `(token, clientid)`.
        """
        self._base_url = base_url.rstrip("/")
        self._token_provider = token_provider

    async def report_account(self, goodfish_id: str, nickname: str, remark: str) -> None:
        """Report one verified account to the cloud without its cookie.

        Args:
            goodfish_id: Goodfish account identifier.
            nickname: Nickname resolved from the verified cookie.
            remark: Operator note stored by the cloud.

        Raises:
            NotLoggedInError: When no token is stored locally.
            CloudUnauthorizedError: When the gateway rejects the token.
            CloudForbiddenError: When the account authorization expired.
            CloudReportError: When the cloud cannot accept the report.
        """
        payload = {
            "goodfishId": goodfish_id,
            "goodfishNickname": nickname,
            "remark": remark,
        }
        try:
            status, body = await self._request("POST", CLOUD_API_PATH_ACCOUNT_REPORT, payload)
        except CloudUnavailableError as error:
            raise CloudReportError() from error
        if status != CLOUD_SUCCESS_HTTP_STATUS:
            logger.warning(CLOUD_REPORT_LOG_MESSAGE, goodfish_id, status)
            raise CloudReportError()
        if body.get("code") != CLOUD_SUCCESS_CODE:
            logger.warning(CLOUD_REPORT_LOG_MESSAGE, goodfish_id, status)
            raise CloudReportError()

    async def check_access(self) -> bool:
        """Ask the cloud whether the current account may keep running.

        Returns:
            True when the authorization is valid, False when it expired.

        Raises:
            NotLoggedInError: When no token is stored locally.
            CloudUnauthorizedError: When the gateway rejects the token.
            CloudForbiddenError: When the cloud reports 403.
            CloudUnavailableError: When the cloud stays unreachable.
        """
        status, body = await self._request("GET", CLOUD_API_PATH_AUTH_ACCESS)
        if status == CLOUD_HTTP_STATUS_UNAUTHORIZED:
            raise CloudUnauthorizedError()
        if status == CLOUD_HTTP_STATUS_FORBIDDEN:
            raise CloudForbiddenError()
        if status != CLOUD_SUCCESS_HTTP_STATUS:
            raise CloudUnavailableError()
        return bool(body.get("data", False))

    async def _request(
        self,
        method: str,
        path: str,
        payload: Optional[dict] = None,
    ) -> tuple[int, dict]:
        """Send one request with deterministic retries.

        Args:
            method: HTTP method.
            path: Absolute cloud path including the gateway prefix.
            payload: Optional JSON body.

        Returns:
            The final HTTP status and decoded JSON body.

        Raises:
            NotLoggedInError: When no token is stored locally.
            CloudUnauthorizedError: When the gateway rejects the token.
            CloudForbiddenError: When the gateway reports missing authorization.
            CloudUnavailableError: When retries are exhausted.
        """
        headers = await self._build_headers()
        url = f"{self._base_url}{path}"
        last_error: Optional[BaseException] = None
        for attempt in range(CLOUD_RETRY_LIMIT + 1):
            try:
                status, body = await self._send(method, url, headers, payload)
            except (aiohttp.ClientError, asyncio.TimeoutError) as error:
                last_error = error
            else:
                if status == CLOUD_HTTP_STATUS_UNAUTHORIZED:
                    raise CloudUnauthorizedError()
                if status == CLOUD_HTTP_STATUS_FORBIDDEN:
                    raise CloudForbiddenError()
                if status < CLOUD_HTTP_STATUS_SERVER_ERROR_MIN:
                    return status, body
                last_error = CloudUnavailableError()
            if attempt < CLOUD_RETRY_LIMIT:
                await asyncio.sleep(
                    random.uniform(CLOUD_RETRY_INTERVAL_MIN_SECONDS, CLOUD_RETRY_INTERVAL_MAX_SECONDS)
                )
        raise CloudUnavailableError() from last_error

    async def _send(
        self,
        method: str,
        url: str,
        headers: dict,
        payload: Optional[dict],
    ) -> tuple[int, dict]:
        """Perform one HTTP round trip.

        Args:
            method: HTTP method.
            url: Fully qualified cloud URL.
            headers: Authorization and content headers.
            payload: Optional JSON body.

        Returns:
            The HTTP status and decoded JSON body.
        """
        timeout = aiohttp.ClientTimeout(total=CLOUD_REQUEST_TIMEOUT_SECONDS)
        async with aiohttp.ClientSession(timeout=timeout) as session:
            async with session.request(method, url, headers=headers, json=payload) as response:
                status = response.status
                try:
                    body = await response.json(content_type=None)
                except (aiohttp.ContentTypeError, ValueError):
                    body = {}
                return status, body if isinstance(body, dict) else {}

    async def _build_headers(self) -> dict:
        """Build gateway headers from the stored token pair.

        Returns:
            Authorization and client id headers.

        Raises:
            NotLoggedInError: When no token is stored locally.
        """
        token, client_id = await self._token_provider()
        if not token:
            raise NotLoggedInError()
        return {
            CLOUD_AUTHORIZATION_HEADER: f"{CLOUD_AUTHORIZATION_PREFIX}{token}",
            CLOUD_CLIENT_ID_HEADER: client_id,
            CONTENT_TYPE_JSON_HEADER: "application/json",
        }
