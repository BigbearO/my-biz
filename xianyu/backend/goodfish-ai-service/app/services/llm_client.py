"""Sole outbound gateway to the LLM provider (ModelGateway).

Talks OpenAI-compatible ``/v1/chat/completions`` over httpx. Budget rules:
per-attempt timeout ``llm.timeout_seconds`` (default 30s) and a total
budget ``llm.total_budget_seconds`` (default 65s) — exceeding the budget
raises 50001 so the gateway (75s) never times out while we still run.
The api_key never appears in logs or exceptions.
"""

import asyncio
import logging
import time
from collections.abc import Callable
from dataclasses import dataclass

import httpx

from ..constants import (
    MESSAGE_LLM_CALL_FAILED,
    OPENAI_CHAT_COMPLETIONS_PATH,
)
from ..errors import LlmCallFailedError
from ..settings import BusinessSettings

logger = logging.getLogger(__name__)


@dataclass(frozen=True)
class LlmCallResult:
    reply: str
    prompt_tokens: int | None
    completion_tokens: int | None
    latency_ms: int


class LlmClient:
    def __init__(self, settings_provider: Callable[[], BusinessSettings]) -> None:
        self._settings_provider = settings_provider

    async def chat(
        self,
        *,
        base_url: str,
        api_key: str,
        model_name: str,
        temperature: float,
        max_tokens: int,
        messages: list[dict],
    ) -> LlmCallResult:
        """Call the model once within the retry/budget policy."""
        settings = self._settings_provider().llm
        url = base_url.rstrip("/") + OPENAI_CHAT_COMPLETIONS_PATH
        payload = {
            "model": model_name,
            "messages": messages,
            "temperature": temperature,
            "max_tokens": max_tokens,
        }
        headers = {
            "Authorization": f"Bearer {api_key}",
            "Content-Type": "application/json",
        }
        started = time.monotonic()
        budget_remaining = settings.total_budget_seconds
        last_error: Exception | None = None

        for attempt in range(settings.max_retries + 1):
            per_attempt = min(settings.timeout_seconds, budget_remaining)
            if per_attempt <= 0:
                break
            try:
                async with httpx.AsyncClient(timeout=per_attempt) as client:
                    response = await client.post(url, json=payload, headers=headers)
            except (httpx.TimeoutException, httpx.TransportError) as error:
                last_error = error
            else:
                if response.status_code == 200:
                    return self._parse_success(response, started)
                if 400 <= response.status_code < 500:
                    # Deterministic rejection (bad key, bad model): retrying
                    # cannot help; log the status only, never the body.
                    logger.warning(
                        "LLM provider rejected request with status %s",
                        response.status_code,
                    )
                    raise LlmCallFailedError(MESSAGE_LLM_CALL_FAILED)
                last_error = RuntimeError(f"llm status {response.status_code}")

            budget_remaining = settings.total_budget_seconds - (time.monotonic() - started)
            if budget_remaining <= 0:
                break
            if attempt < settings.max_retries:
                await asyncio.sleep(settings.retry_delay_seconds)

        raise LlmCallFailedError(MESSAGE_LLM_CALL_FAILED) from last_error

    @staticmethod
    def _parse_success(response: httpx.Response, started: float) -> LlmCallResult:
        try:
            body = response.json()
            reply = body["choices"][0]["message"]["content"] or ""
            usage = body.get("usage") or {}
            prompt_tokens = usage.get("prompt_tokens")
            completion_tokens = usage.get("completion_tokens")
        except (ValueError, KeyError, IndexError, TypeError) as exc:
            logger.warning("LLM response shape unexpected")
            raise LlmCallFailedError(MESSAGE_LLM_CALL_FAILED) from exc
        return LlmCallResult(
            reply=reply.strip(),
            prompt_tokens=int(prompt_tokens) if prompt_tokens is not None else None,
            completion_tokens=int(completion_tokens) if completion_tokens is not None else None,
            latency_ms=int((time.monotonic() - started) * 1000),
        )
