"""Request idempotency on Redis, with availability-first degradation.

Key layout (one key, two states):
- result JSON, TTL = ``idempotency.ttl_seconds`` (default 600s)
- processing placeholder via SET NX, TTL = ``idempotency.processing_ttl_seconds``
  (default 100s = 90s worst case + 10s buffer; shorter placeholders let
  concurrent duplicates slip through and double-generate).

When Redis is unavailable every operation degrades: cache lookups miss,
placeholders are skipped, results are not stored. A duplicate reply is a
low-frequency problem; refusing traffic is not acceptable.
"""

import json
import logging
from collections.abc import Callable
from typing import Any

import redis.asyncio as redis

from ..constants import IDEMPOTENCY_KEY_PREFIX
from ..redis_client import RedisPool
from ..settings import BusinessSettings

logger = logging.getLogger(__name__)

_PROCESSING_MARKER = "processing"


class IdempotencyManager:
    def __init__(
        self,
        pool: RedisPool,
        settings_provider: Callable[[], BusinessSettings],
    ) -> None:
        self._pool = pool
        self._settings_provider = settings_provider

    def _key(self, request_id: str) -> str:
        return f"{IDEMPOTENCY_KEY_PREFIX}{request_id}"

    async def get_cached_response(self, request_id: str) -> dict[str, Any] | None:
        try:
            raw = await self._pool.client.get(self._key(request_id))
        except redis.RedisError as exc:
            logger.warning("Idempotency cache lookup degraded: %s", exc)
            return None
        if not raw or raw == _PROCESSING_MARKER:
            return None
        try:
            return json.loads(raw)
        except (TypeError, ValueError):
            return None

    async def try_acquire_placeholder(self, request_id: str) -> bool:
        """Reserve the request. False means another worker is processing."""
        try:
            acquired = await self._pool.client.set(
                self._key(request_id),
                _PROCESSING_MARKER,
                nx=True,
                ex=self._settings_provider().idempotency.processing_ttl_seconds,
            )
        except redis.RedisError as exc:
            logger.warning("Idempotency placeholder degraded, proceeding: %s", exc)
            return True
        return bool(acquired)

    async def release_placeholder(self, request_id: str) -> None:
        """Del the placeholder after any failure past step 2 of the pipeline."""
        try:
            await self._pool.client.delete(self._key(request_id))
        except redis.RedisError as exc:
            logger.warning("Idempotency placeholder release degraded: %s", exc)

    async def store_result(self, request_id: str, response: dict[str, Any]) -> None:
        try:
            await self._pool.client.set(
                self._key(request_id),
                json.dumps(response, ensure_ascii=False),
                ex=self._settings_provider().idempotency.ttl_seconds,
            )
        except redis.RedisError as exc:
            logger.warning("Idempotency result storage degraded: %s", exc)
