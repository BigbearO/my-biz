"""Redis connection used exclusively for reply idempotency keys."""

import logging

import redis.asyncio as redis

from .settings import RedisSettings

logger = logging.getLogger(__name__)


class RedisPool:
    """Lazy, degradable Redis pool.

    Redis is only used for idempotency; when it is unavailable the reply
    pipeline skips deduplication and logs a warning (availability first).
    The connection is therefore created lazily and failures never abort
    startup.
    """

    def __init__(self, settings: RedisSettings) -> None:
        self._settings = settings
        self._client: redis.Redis | None = None

    @property
    def client(self) -> redis.Redis:
        if self._client is None:
            self._client = redis.from_url(
                self._settings.url,
                decode_responses=True,
            )
        return self._client

    async def ping(self) -> bool:
        try:
            await self.client.ping()
        except redis.RedisError as exc:
            logger.warning("Redis unavailable, idempotency degraded: %s", exc)
            return False
        return True

    async def close(self) -> None:
        if self._client is not None:
            await self._client.aclose()
            self._client = None
